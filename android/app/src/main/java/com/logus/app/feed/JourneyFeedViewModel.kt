package com.logus.app.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import com.logus.app.journey.Member
import com.logus.app.record.FeedLog
import com.logus.app.record.LogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** 시간대 하나: "09:20 ~ 11:20 시간"과 그 안에 찍은 기록들(찍은 순서) */
data class FeedWindow(
    /** 시작 시각(그날 0시부터 분). 예) 9시 20분 → 560 */
    val startMinute: Int,
    val endMinute: Int,
    val logs: List<FeedLog>,
)

/** N01 기록 보기 상태 */
data class JourneyFeedUiState(
    val journeyId: String? = null,
    /** 보고 있는 날(폰 날짜 기준). 처음엔 오늘, 날짜 상자 양쪽 〈 〉로 하루씩 옮긴다 */
    val day: LocalDate = LocalDate.now(),
    /** 〈 를 누를 수 있는지(여정 시작일보다 뒤인 날) / 〉 를 누를 수 있는지(오늘·여정 종료일보다 앞인 날) */
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    /** 처음 읽는 중 */
    val loading: Boolean = true,
    /** 오늘 기록을 시간대별로 나눈 것(아래로 갈수록 늦은 시간) */
    val windows: List<FeedWindow> = emptyList(),
    /** 시간대 길이(시간). 여정을 만든 사람이 정한 공통 알림 간격, 없으면 2시간 */
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    /** 기록한 사람 uid → 닉네임·프로필 사진 */
    val members: Map<String, Member> = emptyMap(),
    /** 다음 쪽을 읽는 중 */
    val loadingMore: Boolean = false,
    /** 그날 기록을 끝까지 다 읽었다 */
    val endReached: Boolean = false,
    val error: String? = null,
    /** N03 저장 팝업의 "기록 N개"(이 여정 전체 기록 수). 세는 중이거나 못 세면 null */
    val recordCount: Int? = null,
    /** 여정 저장(나에게만 여정 끝내기)을 처리하는 중 / 끝남(MainScreen 이 보고 마이로그로 간다) / 실패 문구 */
    val finishing: Boolean = false,
    val finished: Boolean = false,
    val finishError: String? = null,
)

/** 촬영 알림을 받지 않는 여정의 시간대 길이(S02 기본값과 같은 2시간) */
const val DEFAULT_INTERVAL_HOURS = 2

/**
 * N01~N03 기록 보기(스토리보드 v4 13쪽, 화면 목록의 S06 우리 기록): S01-A 여정 카드를 누르면 연다.
 * - 그날(처음엔 오늘, 폰 날짜) 찍은 이 여정의 기록을 찍은 시각 순서로 30개씩 읽고, 아래로 내리면 다음 30개를 읽는다(비용 관리).
 * - 날짜 상자 양쪽 〈 〉: 전날·다음날 기록으로 옮긴다(여정 시작일 ~ 오늘(종료일이 먼저면 종료일)까지).
 * - 공통 알림 간격(1·2·3시간)으로 시간대를 나눈다. 시작점은 오늘 첫 기록 시각을 10분 단위로 내린 시각
 *   (예: 첫 기록 09:24, 2시간 → 09:20 ~ 11:20, 11:20 ~ 13:20 …). 기록이 없는 시간대는 건너뛴다.
 * - 여정 저장(다운로드 모양 버튼 → N03 "저장하시겠습니까?" → 저장): 나에게만 여정을 끝낸다(내 멤버 문서 finishedAt).
 *   다른 구성원은 계속 기록할 수 있다.
 */
class JourneyFeedViewModel(
    private val logs: LogRepository = LogRepository(),
    private val journeys: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(JourneyFeedUiState())
    val state: StateFlow<JourneyFeedUiState> = _state.asStateFlow()

    /** 지금까지 읽은 오늘 기록(시간대로 나누기 전) */
    private var loaded: List<FeedLog> = emptyList()
    private var lastDoc: DocumentSnapshot? = null
    private var dayStart: Date = Date()
    private var dayEnd: Date = Date()
    private var loadJob: Job? = null
    private var journey: Journey? = null

    /** 대표 화면 주소를 한 번 받으면 기억해 둔다(스크롤로 다시 보일 때 Storage 에 또 묻지 않게) */
    private val previewUrls = mutableMapOf<String, String?>()

    /** 원본 영상 팝업에서 받은 영상 주소(같은 영상을 다시 볼 때 Storage 에 또 묻지 않게) */
    private val videoUrls = mutableMapOf<String, String>()

    /** 화면을 열 때·기록을 올리고 돌아왔을 때: 오늘 기록을 처음부터 다시 읽는다 */
    fun open(journey: Journey) {
        this.journey = journey
        showDay(journey, lastDay(journey), reloadMembers = true)
    }

    /** 다시 시도(불러오기 실패 뒤): 보고 있던 날을 처음부터 다시 읽는다 */
    fun reload() {
        val journey = journey ?: return
        showDay(journey, _state.value.day, reloadMembers = true)
    }

    /** 날짜 상자 〈(-1) 〉(+1): 전날·다음날 기록으로 옮긴다(여정 시작일 ~ 오늘 사이에서만) */
    fun moveDay(delta: Long) {
        val journey = journey ?: return
        val target = _state.value.day.plusDays(delta)
        if (target < firstDay(journey) || target > lastDay(journey)) return
        showDay(journey, target, reloadMembers = false)
    }

    /** 그날(day) 기록을 처음부터 읽는다. reloadMembers 가 false 면(날짜만 옮길 때) 읽어 둔 구성원 프로필을 그대로 쓴다 */
    private fun showDay(journey: Journey, day: LocalDate, reloadMembers: Boolean) {
        loadJob?.cancel()
        val zone = ZoneId.systemDefault()
        dayStart = Date.from(day.atStartOfDay(zone).toInstant())
        dayEnd = Date.from(day.plusDays(1).atStartOfDay(zone).toInstant())
        val previous = _state.value.takeIf { it.journeyId == journey.id }
        loaded = emptyList()
        lastDoc = null
        _state.value = JourneyFeedUiState(
            journeyId = journey.id,
            day = day,
            canGoPrev = day > firstDay(journey),
            canGoNext = day < lastDay(journey),
            loading = true,
            intervalHours = previous?.intervalHours ?: DEFAULT_INTERVAL_HOURS,
            members = previous?.members.orEmpty(),
        )
        loadJob = viewModelScope.launch {
            try {
                val interval = runCatching { journeys.loadCommonNotifyInterval(journey) }.getOrNull()
                    ?: DEFAULT_INTERVAL_HOURS
                val members = previous?.members?.takeIf { !reloadMembers && it.isNotEmpty() }
                    ?: runCatching { journeys.loadMembers(journey.memberIds, limit = MAX_MEMBERS) }
                        .getOrDefault(emptyList())
                        .associateBy { it.uid }
                val page = logs.loadLogs(journey.id, dayStart, dayEnd, after = null)
                loaded = page.logs
                lastDoc = page.last
                _state.update {
                    it.copy(
                        loading = false,
                        intervalHours = interval,
                        members = members.ifEmpty { it.members },
                        windows = groupByWindows(loaded, interval, zone),
                        endReached = page.last == null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "기록 읽기 실패", e)
                _state.update { it.copy(loading = false, error = "기록을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 볼 수 있는 첫날: 여정 시작일(날짜를 읽지 못하면 오늘) */
    private fun firstDay(journey: Journey): LocalDate =
        runCatching { LocalDate.parse(journey.startDate) }.getOrNull()?.coerceAtMost(LocalDate.now()) ?: LocalDate.now()

    /** 볼 수 있는 마지막 날: 오늘(여정 종료일이 오늘보다 앞이면 종료일) */
    private fun lastDay(journey: Journey): LocalDate {
        val today = LocalDate.now()
        val end = runCatching { LocalDate.parse(journey.endDate) }.getOrNull() ?: return today
        return minOf(today, end).coerceAtLeast(firstDay(journey))
    }

    /** 아래로 내려 끝에 가까워지면: 다음 30개를 읽어 이어 붙인다 */
    fun loadMore() {
        val current = _state.value
        val journeyId = current.journeyId ?: return
        val after = lastDoc ?: return
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            try {
                val page = logs.loadLogs(journeyId, dayStart, dayEnd, after = after)
                loaded = loaded + page.logs
                lastDoc = page.last
                _state.update {
                    it.copy(
                        loadingMore = false,
                        windows = groupByWindows(loaded, it.intervalHours, ZoneId.systemDefault()),
                        endReached = page.last == null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "다음 기록 읽기 실패", e)
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** 기록의 대표 화면 주소(없으면 null → 화면은 보라 그림). 한 번 받은 주소는 기억한다 */
    suspend fun previewUrl(log: FeedLog): String? {
        if (previewUrls.containsKey(log.id)) return previewUrls[log.id]
        val url = runCatching { logs.previewUrl(log) }.getOrNull()
        previewUrls[log.id] = url
        return url
    }

    /** 원본 영상 팝업의 재생 주소(Storage 다운로드 주소). 못 받으면 null. 받은 주소만 기억한다(실패하면 다시 시도할 수 있게) */
    suspend fun videoUrl(log: FeedLog): String? {
        videoUrls[log.id]?.let { return it }
        val url = runCatching { logs.videoUrl(log) }.onFailure { Log.w(TAG, "영상 주소 받기 실패", it) }.getOrNull()
        if (url != null) videoUrls[log.id] = url
        return url
    }

    /** 저장 팝업을 열 때: 이 여정의 전체 기록 수를 센다(팝업의 "기록 N개") */
    fun loadRecordCount() {
        val journeyId = _state.value.journeyId ?: return
        _state.update { it.copy(recordCount = null, finishError = null) }
        viewModelScope.launch {
            val count = runCatching { logs.countLogs(journeyId) }
                .onFailure { Log.w(TAG, "기록 수 세기 실패", it) }
                .getOrNull()
            if (_state.value.journeyId == journeyId) _state.update { it.copy(recordCount = count) }
        }
    }

    /** 여정 저장(N03 팝업의 "저장"): 나에게만 이 여정을 끝낸다 */
    fun finish(uid: String) {
        val journeyId = _state.value.journeyId ?: return
        if (_state.value.finishing || _state.value.finished) return
        _state.update { it.copy(finishing = true, finishError = null) }
        viewModelScope.launch {
            try {
                journeys.finishJourney(journeyId, uid)
                _state.update { it.copy(finishing = false, finished = true) }
            } catch (e: Exception) {
                Log.w(TAG, "여정 마치기 실패 (보안 규칙 배포 확인)", e)
                _state.update {
                    it.copy(finishing = false, finishError = "저장하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.")
                }
            }
        }
    }

    /** 마이로그로 넘어간 뒤: 끝남 표시를 지운다(다음에 열 때 다시 넘어가지 않게) */
    fun consumeFinished() {
        _state.update { it.copy(finished = false) }
    }

    private companion object {
        const val TAG = "JourneyFeedViewModel"

        /** 기록 보기에서 프로필을 읽어 올 최대 구성원 수 */
        const val MAX_MEMBERS = 30
    }
}

/**
 * 기록을 시간대로 나눈다. 시작점은 첫 기록 시각(그날 몇 분째)을 10분 단위로 내린 값이고,
 * 거기서부터 intervalHours 시간씩 자른다. 기록이 없는 시간대는 만들지 않는다. logs 는 찍은 시각 순서여야 한다.
 */
fun groupByWindows(logs: List<FeedLog>, intervalHours: Int, zone: ZoneId): List<FeedWindow> {
    if (logs.isEmpty()) return emptyList()
    fun minuteOfDay(log: FeedLog): Int {
        val time = Instant.ofEpochMilli(log.capturedAtMillis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }
    val length = intervalHours.coerceIn(1, 24) * 60
    val anchor = minuteOfDay(logs.first()) / 10 * 10
    return logs
        .groupBy { ((minuteOfDay(it) - anchor).coerceAtLeast(0)) / length }
        .toSortedMap()
        .map { (index, inWindow) ->
            val start = anchor + index * length
            FeedWindow(startMinute = start, endMinute = start + length, logs = inWindow)
        }
}

/** 560 → "09:20". 24시가 넘으면 다음 날 시각으로(25:10 → "01:10") */
fun minuteLabel(minute: Int): String {
    val m = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}
