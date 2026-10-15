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
)

/**
 * 여정 상세(스토리보드 v5 17·23쪽, S15 기록 · S16 추억 지도 · S17 추억 영상) 상태 관리.
 * 지금은 S15 기록만 데이터를 읽는다: 고른 날짜(폰 시간대 기준 하루)의 기록을 찍은 순서로 30개씩 읽고,
 * N01 기록 보기와 같은 방식으로 공통 알림 간격마다 시간대를 나눈다. 실시간 리스너 대신 한 번 읽기(get)를 쓴다.
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

    /** 대표 화면 주소를 한 번 받으면 기억해 둔다(스크롤·날짜를 오갈 때 Storage 에 또 묻지 않게) */
    private val previewUrls = mutableMapOf<String, String?>()

    /** 마이로그에서 여정 카드를 눌렀을 때: 첫날 기록부터 보여 준다 */
    fun open(journey: Journey) {
        val days = daysOf(journey)
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

    /** 기록의 대표 화면 주소(없으면 null → 화면은 보라 그림). 한 번 받은 주소는 기억한다 */
    suspend fun previewUrl(log: FeedLog): String? {
        if (previewUrls.containsKey(log.id)) return previewUrls[log.id]
        val url = runCatching { logs.previewUrl(log) }.getOrNull()
        previewUrls[log.id] = url
        return url
    }

    private companion object {
        const val TAG = "JourneyDetailViewModel"

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
