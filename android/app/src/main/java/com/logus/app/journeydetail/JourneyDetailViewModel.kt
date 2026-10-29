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
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
     * S16 추억 지도·S17 DAY RECAP이 함께 쓰는 "그날 기록 한 번에 읽기"(최대 DAY_LIMIT 개):
     * 읽은 날짜(null = 아직 안 읽음), 읽는 중, 오류, 기록이 너무 많아 앞의 것만 썼는지
     */
    val dayAllIndex: Int? = null,
    val dayAllLoading: Boolean = false,
    val dayAllError: String? = null,
    val dayAllTruncated: Boolean = false,
    /** S16: 구성원별 경로, 위치 없이 올린 기록 수 */
    val routes: List<MemberRoute> = emptyList(),
    val unlocatedCount: Int = 0,
    /** S17: DAY RECAP 장면들(지도 장면 + 시간대마다 구성원 영상 최대 3개를 차례대로) */
    val recapScenes: List<RecapStep> = emptyList(),
)

/** S17 DAY RECAP 의 장면 하나: 영상 장면(RecapScene) 또는 지도 장면(RecapMapScene). startMinute 은 화면 위 "11:00" */
sealed interface RecapStep {
    val startMinute: Int
}

/**
 * S17 DAY RECAP 의 영상 장면(한 차례): 한 시간대 안에서 구성원 칸(최대 3칸)마다 n번째 영상을 위·가운데·아래로 동시에 튼다.
 * 한 시간대는 가장 많이 올린 사람의 영상 개수만큼 장면(차례)이 생긴다.
 */
data class RecapScene(
    /** 시간대 시작·끝(그날 0시부터 분). 화면 위 "11:00" */
    override val startMinute: Int,
    val endMinute: Int,
    val clips: List<RecapClip>,
) : RecapStep

/**
 * S17 DAY RECAP 2단계 지도 장면(2026-10-21 팀 결정): 시간대 영상 앞에 구성원 위치를 지도로 보여 준다.
 * - 첫 지도 장면(fromMinute == null): 그 시간대 위치를 확대해 보여 준다.
 * - 그다음부터: 앞 지도 장면 위치(from)에서 이번 위치(to)로 구성원마다 자기 길을 따라 움직인 뒤 확대한다(화면 위 "11:00 → 13:00").
 * pins 의 from == null 은 이번에 새로 나타나는 사람, to == null 은 이번 시간대에 위치가 없어 사라지는 사람이다.
 * groups 는 도착한 위치(to)가 같은 건물(GROUP_METERS 안)인 사람들 uid 묶음(혼자면 한 명짜리 묶음).
 */
data class RecapMapScene(
    override val startMinute: Int,
    val fromMinute: Int?,
    val pins: List<RecapPin>,
    val groups: List<List<String>>,
) : RecapStep

/** 지도 장면 속 구성원 한 명: 구성원 구분색 번호(추억 지도와 같은 색), 출발·도착 위치 */
data class RecapPin(val uid: String, val colorIndex: Int, val from: GeoPoint?, val to: GeoPoint?)

/**
 * 장면 속 칸 하나: 그 칸의 구성원, 구성원 구분색 번호(추억 지도와 같은 색), 이번 차례에 틀 영상.
 * log 가 null 이면 그 사람은 이 차례에 올린 영상이 없어서 검은 화면을 보여 준다.
 */
data class RecapClip(val uid: String, val colorIndex: Int, val log: FeedLog?)

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
 * 여정 상세(스토리보드 v5 17·23쪽, S15 기록 · S16 추억 지도 · S17 DAY RECAP) 상태 관리.
 * - S15 기록: 고른 날짜(폰 시간대 기준 하루)의 기록을 찍은 순서로 30개씩 읽고, N01 과 같은 방식으로 시간대를 나눈다.
 * - S16 추억 지도·S17 DAY RECAP: 그날 기록을 한 번에 최대 DAY_LIMIT 개 읽어(loadDayAll) 경로와 DAY RECAP 장면을 만든다.
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
        val days = journeyDays(journey.startDate, journey.endDate)
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
     * S16 추억 지도·S17 DAY RECAP 탭을 보거나 그 탭에서 날짜를 바꿨을 때: 그날 기록을 한 번에 최대 DAY_LIMIT 개 읽어
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

    /** 지도·DAY RECAP 오류 뒤 "다시 불러오기" */
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

        /** 지도·DAY RECAP에 쓸 하루 기록 수 상한(비용 관리: limit) */
        const val DAY_LIMIT = 100L

        /** 프로필을 읽어 올 최대 구성원 수(N01 과 같다) */
        const val MAX_MEMBERS = 30
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

/** 지도 장면에서 같은 건물로 보고 프로필을 한 말풍선에 묶는 거리(미터, 2026-10-21 팀 결정) */
const val GROUP_METERS = 50.0

/**
 * 기록들 → DAY RECAP 장면들(S17). 영상 기록(mediaType video, 파일 경로 있음)만 쓰고,
 * N01 과 같은 시간대로 나눈다(영상이 없는 시간대는 건너뜀). 시간대마다:
 * - 지도 장면(2단계, 2026-10-21 팀 결정): 그 시간대에 위치를 남긴 구성원마다 그 시간대 첫 위치. 위치가 있는 사람이 없으면 지도 장면을 건너뛴다.
 *   앞 지도 장면에 있던 사람은 그 위치에서 이번 위치로 움직이고, 이번에 위치가 없는 사람은 사라진다(다음에 위치가 생기면 다시 나타남).
 * - 영상 장면(2026-10-22 팀 결정):
 *   - 칸 = 여정 구성원 전원(memberIds 순서, 혼자면 한 칸). 4명 이상이면 그 시간대에 영상을 많이 올린 3명(같으면 구성원 순서)
 *   - 장면(차례) 수 = 그 시간대에 가장 많이 올린 사람의 영상 개수. n번째 장면에는 칸마다 그 사람의 n번째 영상(찍은 순서)
 *   - 영상이 모자라거나 없는 사람의 칸은 log = null(검은 화면)
 * 색 번호는 구성원 순서(추억 지도와 같은 색). memberIds 에 없는 사람(나간 구성원)의 영상도 뒤에 붙여 보여 준다.
 * logs 는 찍은 시각 순서여야 한다.
 */
fun buildRecapScenes(logs: List<FeedLog>, memberIds: List<String>, intervalHours: Int, zone: ZoneId): List<RecapStep> {
    val videos = logs.filter { it.mediaType == "video" && it.mediaPath != null }
    val people = memberIds + videos.map { it.authorId }.distinct().filterNot { it in memberIds }
    var lastMap: RecapMapScene? = null
    return groupByWindows(videos, intervalHours, zone).flatMap { window ->
        val byAuthor = window.logs.groupBy { it.authorId }

        // 지도 장면: 구성원마다 이 시간대 첫 위치
        val here = people.withIndex().mapNotNull { (index, uid) ->
            byAuthor[uid]?.firstNotNullOfOrNull { it.location }?.let { Triple(uid, index, it) }
        }
        val mapScene = if (here.isEmpty()) null else {
            val before = lastMap?.pins.orEmpty().filter { it.to != null }.associateBy { it.uid }
            val pins = here.map { (uid, index, point) -> RecapPin(uid, index, before[uid]?.to, point) } +
                before.values.filter { old -> here.none { it.first == old.uid } }
                    .map { RecapPin(it.uid, it.colorIndex, it.to, null) }
            RecapMapScene(
                startMinute = window.startMinute,
                fromMinute = lastMap?.startMinute,
                pins = pins,
                groups = groupNearby(here.map { it.first to it.third }),
            ).also { lastMap = it }
        }

        // 영상 장면들
        val count = { uid: String -> byAuthor[uid]?.size ?: 0 }
        val lanes = people.withIndex()
            .sortedWith(compareByDescending<IndexedValue<String>> { count(it.value) }.thenBy { it.index })
            .take(RECAP_MAX_CLIPS)
            .sortedBy { it.index }
        val rounds = lanes.maxOfOrNull { count(it.value) } ?: 0
        listOfNotNull(mapScene) + (0 until rounds).map { round ->
            RecapScene(
                startMinute = window.startMinute,
                endMinute = window.endMinute,
                clips = lanes.map { (index, uid) -> RecapClip(uid, index, byAuthor[uid]?.getOrNull(round)) },
            )
        }
    }
}

/**
 * 같은 건물(GROUP_METERS 안)에 있는 사람끼리 묶는다. 순서대로 보면서, 앞에서 만든 묶음의 첫 사람과 가까우면 그 묶음에 넣고
 * 아니면 새 묶음을 만든다(첫 사람 기준이라 묶음이 끝없이 길게 이어지지 않는다).
 */
internal fun groupNearby(points: List<Pair<String, GeoPoint>>): List<List<String>> {
    val groups = mutableListOf<MutableList<Pair<String, GeoPoint>>>()
    points.forEach { p ->
        val group = groups.firstOrNull { distanceMeters(it.first().second, p.second) <= GROUP_METERS }
        if (group != null) group += p else groups += mutableListOf(p)
    }
    return groups.map { g -> g.map { it.first } }
}

/** 두 좌표 사이 거리(미터, 지구를 공으로 보고 계산) */
internal fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** 날짜 상자로 넘겨 볼 수 있는 최대 일수(날짜가 잘못 저장돼도 목록이 끝없이 길어지지 않게) */
private const val MAX_DAYS = 366

/** 여정의 날짜들: 시작일부터 종료일까지 하루씩("YYYY-MM-DD"). 날짜를 읽을 수 없으면 빈 목록, 종료일이 이상하면 시작일 하루만 */
internal fun journeyDays(startDate: String, endDate: String): List<LocalDate> {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull()?.takeIf { !it.isBefore(start) } ?: start
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .take(MAX_DAYS)
        .toList()
}

/** 그날 0시 ~ 다음 날 0시(폰 시간대, N01 기록 보기와 같은 기준) */
internal fun dayRange(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Date, Date> =
    Date.from(day.atStartOfDay(zone).toInstant()) to Date.from(day.plusDays(1).atStartOfDay(zone).toInstant())
