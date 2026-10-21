package com.logus.app.journeydetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.logus.app.feed.DEFAULT_INTERVAL_HOURS
import com.logus.app.feed.FeedWindow
import com.logus.app.feed.groupByWindows
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import com.logus.app.journey.Member
import com.logus.app.record.FeedLog
import com.logus.app.record.GeoPoint
import com.logus.app.record.LogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** 여정 상세(S15~S17) 상태 */
data class JourneyDetailUiState(
    /** 보고 있는 여정(없으면 화면을 열지 않은 것) */
    val journey: Journey? = null,
    /** 여행 기간의 날짜들(시작일 ~ 종료일) */
    val days: List<LocalDate> = emptyList(),
    /** 지금 보는 날짜의 순서(0 = 첫날) */
    val dayIndex: Int = 0,
    /** 그날 기록을 읽는 중 */
    val loading: Boolean = true,
    /** 그날 기록을 시간대별로 나눈 것 */
    val windows: List<FeedWindow> = emptyList(),
    /** 시간대 길이(시간). 여정을 만든 사람의 공통 알림 간격, 없으면 2시간 */
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    /** 기록한 사람 uid → 닉네임·프로필 사진 */
    val members: Map<String, Member> = emptyMap(),
    val loadingMore: Boolean = false,
    /** 그날 기록을 끝까지 다 읽었다 */
    val endReached: Boolean = false,
    val error: String? = null,
    /**
     * S16 추억 지도·S17 추억 영상이 함께 쓰는 "그날 기록 한 번에 읽기"(최대 DAY_LIMIT 개):
     * 읽은 날짜(null = 아직 안 읽음), 읽는 중, 오류, 기록이 너무 많아 앞의 것만 썼는지
     */
    val dayAllIndex: Int? = null,
    val dayAllLoading: Boolean = false,
    val dayAllError: String? = null,
    val dayAllTruncated: Boolean = false,
    /** S16: 구성원별 경로, 위치 없이 올린 기록 수 */
    val routes: List<MemberRoute> = emptyList(),
    val unlocatedCount: Int = 0,
    /** S17: DAY RECAP 장면들(시간대마다 구성원 영상 최대 3개) */
    val recapScenes: List<RecapScene> = emptyList(),
)

/** S17 DAY RECAP 의 한 장면: 한 시간대에 구성원별 첫 영상(먼저 찍은 3명까지)을 위·가운데·아래로 동시에 튼다 */
data class RecapScene(
    /** 시간대 시작·끝(그날 0시부터 분). 화면 위 "11:00" */
    val startMinute: Int,
    val endMinute: Int,
    val clips: List<RecapClip>,
)

/** 장면 속 영상 하나: 기록과 그 사람의 구성원 구분색 번호(추억 지도와 같은 색) */
data class RecapClip(val log: FeedLog, val colorIndex: Int)

/**
 * S16 추억 지도의 한 구성원 경로: 그날 위치를 남긴 기록을 찍은 순서대로(핀 번호 1, 2, 3 …).
 * colorIndex 는 여정 구성원 순서(memberIds)로 정한 구성원 구분색 번호(Color.kt 의 MemberColors, 4색을 돌려 쓴다).
 * 이름은 화면을 그릴 때 구성원 정보(members)에서 찾는다.
 */
data class MemberRoute(
    val uid: String,
    val colorIndex: Int,
    val points: List<GeoPoint>,
)

/**
 * 여정 상세(스토리보드 v5 17·23쪽, S15 기록 · S16 추억 지도 · S17 추억 영상) 상태 관리.
 * - S15 기록: 고른 날짜(폰 시간대 기준 하루)의 기록을 찍은 순서로 30개씩 읽고, N01 과 같은 방식으로 시간대를 나눈다.
 * - S16 추억 지도·S17 추억 영상: 그날 기록을 한 번에 최대 DAY_LIMIT 개 읽어(loadDayAll) 경로와 DAY RECAP 장면을 만든다.
 * 실시간 리스너 대신 한 번 읽기(get)를 쓴다.
 */
class JourneyDetailViewModel(
    private val logs: LogRepository = LogRepository(),
    private val journeys: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(JourneyDetailUiState())
    val state: StateFlow<JourneyDetailUiState> = _state.asStateFlow()

    /** 지금 날짜에서 지금까지 읽은 기록(시간대로 나누기 전) */
    private var loaded: List<FeedLog> = emptyList()
    private var lastDoc: DocumentSnapshot? = null
    private var loadJob: Job? = null

    private var mapJob: Job? = null

    /** 대표 화면 주소를 한 번 받으면 기억해 둔다(스크롤·날짜를 오갈 때 Storage 에 또 묻지 않게) */
    private val previewUrls = mutableMapOf<String, String?>()
    private val videoUrls = mutableMapOf<String, String?>()

    /** 마이로그에서 여정 카드를 눌렀을 때: 첫날 기록부터 보여 준다 */
    fun open(journey: Journey) {
        val days = daysOf(journey)
        loadJob?.cancel() // 전에 보던 여정의 읽기가 늦게 끝나 덮어쓰지 않게
        mapJob?.cancel()
        _state.value = JourneyDetailUiState(journey = journey, days = days)
        viewModelScope.launch {
            val interval = runCatching { journeys.loadCommonNotifyInterval(journey) }.getOrNull() ?: DEFAULT_INTERVAL_HOURS
            val members = runCatching { journeys.loadMembers(journey.memberIds, limit = MAX_MEMBERS) }
                .getOrDefault(emptyList())
                .associateBy { it.uid }
            if (_state.value.journey?.id != journey.id) return@launch
            _state.update { it.copy(intervalHours = interval, members = members) }
            // 시간대 길이를 알아야 나눌 수 있어서, 간격을 읽은 뒤 기록을 읽는다
            loadDay(0)
        }
    }

    /** 날짜 상자의 ‹ › : 하루 앞·뒤로 */
    fun moveDay(delta: Int) {
        val current = _state.value
        val next = current.dayIndex + delta
        if (current.journey == null || next !in current.days.indices) return
        loadDay(next)
    }

    /** 오류가 났을 때 "다시 불러오기" */
    fun retry() = loadDay(_state.value.dayIndex)

    private fun loadDay(index: Int) {
        val journey = _state.value.journey ?: return
        val day = _state.value.days.getOrNull(index) ?: run {
            _state.update { it.copy(loading = false) } // 날짜를 읽을 수 없는 여정: 빈 화면으로
            return
        }
        loadJob?.cancel()
        loaded = emptyList()
        lastDoc = null
        _state.update {
            it.copy(dayIndex = index, loading = true, windows = emptyList(), loadingMore = false, endReached = false, error = null)
        }
        val (from, to) = dayRange(day)
        loadJob = viewModelScope.launch {
            try {
                val page = logs.loadLogs(journey.id, from, to, after = null)
                loaded = page.logs
                lastDoc = page.last
                _state.update {
                    it.copy(
                        loading = false,
                        windows = groupByWindows(loaded, it.intervalHours, ZoneId.systemDefault()),
                        endReached = page.last == null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "그날 기록 읽기 실패", e)
                _state.update { it.copy(loading = false, error = "기록을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 아래로 내려 끝에 가까워지면: 그날의 다음 30개를 읽어 이어 붙인다 */
    fun loadMore() {
        val current = _state.value
        val journey = current.journey ?: return
        val day = current.days.getOrNull(current.dayIndex) ?: return
        val after = lastDoc ?: return
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        val (from, to) = dayRange(day)
        loadJob = viewModelScope.launch {
            try {
                val page = logs.loadLogs(journey.id, from, to, after = after)
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

    /**
     * S16 추억 지도·S17 추억 영상 탭을 보거나 그 탭에서 날짜를 바꿨을 때: 그날 기록을 한 번에 최대 DAY_LIMIT 개 읽어
     * 구성원별 경로(위치가 있는 것만)와 DAY RECAP 장면(영상만)을 만든다. 같은 날을 이미 읽었으면 다시 읽지 않는다(비용 절약).
     */
    fun loadDayAll() {
        val current = _state.value
        val journey = current.journey ?: return
        val index = current.dayIndex
        if (current.dayAllIndex == index && current.dayAllError == null) return // 이미 읽었거나 읽는 중
        val day = current.days.getOrNull(index) ?: return
        mapJob?.cancel()
        _state.update {
            it.copy(
                dayAllIndex = index, dayAllLoading = true, dayAllError = null, dayAllTruncated = false,
                routes = emptyList(), unlocatedCount = 0, recapScenes = emptyList(),
            )
        }
        val (from, to) = dayRange(day)
        mapJob = viewModelScope.launch {
            try {
                val page = logs.loadLogs(journey.id, from, to, after = null, limit = DAY_LIMIT)
                _state.update {
                    it.copy(
                        dayAllLoading = false,
                        dayAllTruncated = page.last != null,
                        routes = buildRoutes(page.logs, journey.memberIds),
                        unlocatedCount = page.logs.count { log -> log.location == null },
                        recapScenes = buildRecapScenes(page.logs, journey.memberIds, it.intervalHours, ZoneId.systemDefault()),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "그날 기록 한 번에 읽기 실패", e)
                _state.update { it.copy(dayAllLoading = false, dayAllError = "불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 지도·추억 영상 오류 뒤 "다시 불러오기" */
    fun retryDayAll() {
        _state.update { it.copy(dayAllIndex = null) }
        loadDayAll()
    }

    /** 영상 재생 주소(Storage 다운로드 주소, 구성원만 받을 수 있다). 못 받으면 null. 한 번 받은 주소는 기억한다 */
    suspend fun videoUrl(log: FeedLog): String? {
        if (videoUrls.containsKey(log.id)) return videoUrls[log.id]
        val url = runCatching { logs.videoUrl(log) }.onFailure { Log.w(TAG, "영상 주소 받기 실패", it) }.getOrNull()
        videoUrls[log.id] = url
        return url
    }

    /** 기록의 대표 화면 주소(없으면 null → 화면은 보라 그림). 한 번 받은 주소는 기억한다 */
    suspend fun previewUrl(log: FeedLog): String? {
        if (previewUrls.containsKey(log.id)) return previewUrls[log.id]
        val url = runCatching { logs.previewUrl(log) }.getOrNull()
        previewUrls[log.id] = url
        return url
    }

    private companion object {
        const val TAG = "JourneyDetailViewModel"

        /** 지도·추억 영상에 쓸 하루 기록 수 상한(비용 관리: limit) */
        const val DAY_LIMIT = 100L

        /** 프로필을 읽어 올 최대 구성원 수(N01 과 같다) */
        const val MAX_MEMBERS = 30

        /** 날짜 상자로 넘겨 볼 수 있는 최대 일수(날짜가 잘못 저장돼도 목록이 끝없이 길어지지 않게) */
        const val MAX_DAYS = 366
    }

    /** 여정의 날짜들: 시작일부터 종료일까지 하루씩. 날짜를 읽을 수 없으면 시작일 하루만 */
    private fun daysOf(journey: Journey): List<LocalDate> {
        val start = runCatching { LocalDate.parse(journey.startDate) }.getOrNull() ?: return emptyList()
        val end = runCatching { LocalDate.parse(journey.endDate) }.getOrNull()?.takeIf { !it.isBefore(start) } ?: start
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .take(MAX_DAYS)
            .toList()
    }

    /** 그날 0시 ~ 다음 날 0시(폰 시간대, N01 기록 보기와 같은 기준) */
    private fun dayRange(day: LocalDate): Pair<Date, Date> {
        val zone = ZoneId.systemDefault()
        return Date.from(day.atStartOfDay(zone).toInstant()) to Date.from(day.plusDays(1).atStartOfDay(zone).toInstant())
    }
}

/**
 * 기록들 → 구성원별 경로(S16 추억 지도). 위치가 있는 기록만, 찍은 순서대로 쓴다. logs 는 찍은 시각 순서여야 한다.
 * 구성원 순서는 memberIds 순서(색이 늘 같게), memberIds 에 없는 사람(나간 구성원 등)은 뒤에 붙인다.
 */
fun buildRoutes(logs: List<FeedLog>, memberIds: List<String>): List<MemberRoute> {
    val byAuthor = logs.filter { it.location != null }.groupBy { it.authorId }
    val order = memberIds.filter { it in byAuthor } + byAuthor.keys.filterNot { it in memberIds }
    return order.map { uid ->
        val index = memberIds.indexOf(uid).takeIf { it >= 0 } ?: (memberIds.size + order.indexOf(uid))
        MemberRoute(
            uid = uid,
            colorIndex = index,
            points = byAuthor.getValue(uid).mapNotNull { it.location },
        )
    }
}

/** 장면 하나에 동시에 트는 영상 수(위·가운데·아래) */
const val RECAP_MAX_CLIPS = 3

/**
 * 기록들 → DAY RECAP 장면들(S17). 영상 기록(mediaType video, 파일 경로 있음)만 쓰고, N01 과 같은 시간대로 나눈 뒤
 * 시간대마다 구성원별 첫 영상을 먼저 찍은 순서로 최대 3개 고른다. logs 는 찍은 시각 순서여야 한다.
 * 색 번호는 추억 지도와 같게 memberIds 순서로 정한다.
 */
fun buildRecapScenes(logs: List<FeedLog>, memberIds: List<String>, intervalHours: Int, zone: ZoneId): List<RecapScene> {
    val videos = logs.filter { it.mediaType == "video" && it.mediaPath != null }
    return groupByWindows(videos, intervalHours, zone).map { window ->
        RecapScene(
            startMinute = window.startMinute,
            endMinute = window.endMinute,
            clips = window.logs
                .distinctBy { it.authorId }
                .take(RECAP_MAX_CLIPS)
                .map { log -> RecapClip(log, memberIds.indexOf(log.authorId).takeIf { it >= 0 } ?: memberIds.size) },
        )
    }
}
