package com.logus.app.mylog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import com.logus.app.record.LogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** S14 마이로그 여정 목록 상태 */
data class MyLogUiState(
    /** 처음 읽는 중 */
    val loading: Boolean = true,
    /** 지난 여정(최근 시작한 순서) */
    val journeys: List<Journey> = emptyList(),
    /** 다음 쪽을 읽는 중 */
    val loadingMore: Boolean = false,
    /** 내 여정을 끝까지 다 읽었다 */
    val endReached: Boolean = false,
    val error: String? = null,
    /** 휴지통으로 지우는 중인 여정 ID */
    val deletingId: String? = null,
    /** 잠깐 보여 줄 안내(지움 완료·실패) */
    val message: String? = null,
)

/**
 * S14 마이로그 여정 목록 상태 관리.
 * - 나에게 끝난 여정(종료일이 지났거나 N03 에서 "저장"한 여정)을 최근 시작한 순서로 10개씩 읽는다.
 * - 탭을 오갈 때는 다시 읽지 않는다(비용 절약). 여정을 저장하고 마이로그로 왔을 때만 force 로 다시 읽는다.
 * - 실시간 리스너 대신 한 번 읽기(get)를 쓴다.
 */
class MyLogViewModel(
    private val journeys: JourneyRepository = JourneyRepository(),
    private val logs: LogRepository = LogRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(MyLogUiState())
    val state: StateFlow<MyLogUiState> = _state.asStateFlow()

    private var loadedUid: String? = null
    private var lastDoc: DocumentSnapshot? = null
    private var loadJob: Job? = null

    /** 여정 ID → 대표 사진 주소(없으면 null). 스크롤로 다시 보일 때 또 읽지 않게 기억해 둔다 */
    private val coverUrls = mutableMapOf<String, String?>()

    fun load(uid: String, force: Boolean = false) {
        if (!force && uid == loadedUid) return
        loadedUid = uid
        loadJob?.cancel()
        lastDoc = null
        coverUrls.clear()
        _state.value = MyLogUiState(loading = true)
        loadJob = viewModelScope.launch {
            try {
                val (found, last) = readPage(uid, after = null)
                lastDoc = last
                _state.value = MyLogUiState(loading = false, journeys = found, endReached = last == null)
            } catch (e: Exception) {
                Log.w(TAG, "지난 여정 읽기 실패 (보안 규칙·색인 배포 확인)", e)
                loadedUid = null // 다음에 탭을 열면 다시 시도한다
                _state.value = MyLogUiState(loading = false, error = "여정을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
    }

    /** 아래로 내려 끝에 가까워지면: 다음 지난 여정을 읽어 이어 붙인다 */
    fun loadMore() {
        val uid = loadedUid ?: return
        val after = lastDoc ?: return
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            try {
                val (found, last) = readPage(uid, after)
                lastDoc = last
                _state.update {
                    it.copy(
                        loadingMore = false,
                        journeys = (it.journeys + found).distinctBy { j -> j.id },
                        endReached = last == null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "다음 여정 읽기 실패", e)
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * 한 쪽 읽기. 읽은 10개가 모두 진행 중 여정이라 지난 여정이 하나도 없으면 다음 쪽을 이어서 읽는다
     * (화면이 "지난 여정이 없어요"로 잘못 보이지 않게, 최대 MAX_EMPTY_PAGES 번). 끝까지 읽었으면 last 는 null.
     */
    private suspend fun readPage(uid: String, after: DocumentSnapshot?): Pair<List<Journey>, DocumentSnapshot?> {
        val today = LocalDate.now().toString() // 여정 날짜는 현지 달력 날짜라서 폰의 오늘 날짜와 비교한다
        var cursor = after
        repeat(MAX_EMPTY_PAGES) {
            val page = journeys.loadPastJourneys(uid, today, cursor)
            if (page.journeys.isNotEmpty() || page.last == null) return page.journeys to page.last
            cursor = page.last
        }
        return emptyList<Journey>() to cursor
    }

    /**
     * S14 휴지통 "마이로그에서 지우기": 내 마이로그에서만 뺀다(여정·기록·다른 구성원의 마이로그는 그대로).
     * 서버에 표시(hiddenAt)를 남긴 뒤 목록에서 뺀다. 실패하면 목록은 그대로 두고 알려 준다.
     */
    fun hide(journey: Journey) {
        val uid = loadedUid ?: return
        if (_state.value.deletingId != null) return
        _state.update { it.copy(deletingId = journey.id) }
        viewModelScope.launch {
            try {
                journeys.hideFromMyLog(journey.id, uid)
                coverUrls.remove(journey.id)
                _state.update {
                    it.copy(
                        deletingId = null,
                        journeys = it.journeys.filterNot { j -> j.id == journey.id },
                        message = "마이로그에서 지웠어요",
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "마이로그에서 지우기 실패 (보안 규칙 배포 확인)", e)
                _state.update { it.copy(deletingId = null, message = "지우지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 안내를 보여 준 뒤 */
    fun messageShown() = _state.update { it.copy(message = null) }

    /** 카드의 대표 사진 주소: 그 여정에서 가장 먼저 찍은 기록의 대표 화면. 없거나 못 받으면 null(보라 그림) */
    suspend fun coverUrl(journeyId: String): String? {
        if (coverUrls.containsKey(journeyId)) return coverUrls[journeyId]
        val url = runCatching { logs.loadFirstLog(journeyId)?.let { logs.previewUrl(it) } }
            .onFailure { Log.w(TAG, "대표 사진 읽기 실패", it) }
            .getOrNull()
        coverUrls[journeyId] = url
        return url
    }

    private companion object {
        const val TAG = "MyLogViewModel"

        /** 지난 여정이 없는 쪽을 연달아 몇 번까지 이어 읽을지(읽기 비용 상한) */
        const val MAX_EMPTY_PAGES = 3
    }
}
