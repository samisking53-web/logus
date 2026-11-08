package com.logus.app.explore

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 탐색 탭 안에서 연 화면(맨 아래는 늘 S08 피드) */
sealed interface ExploreRoute {
    /** S09 검색 결과 */
    data class Search(val query: String) : ExploreRoute

    /** 모아보기(좋아요·저장) */
    data object Collection : ExploreRoute

    /** 모아보기에서 고른 리캡 하나를 S08 과 같은 모양으로 */
    data class Viewer(val recapId: String) : ExploreRoute

    /** 20코인으로 장소를 연 뒤의 여정 상세([기록] [추억 지도] [DAY RECAP]) */
    data class Detail(val recapId: String) : ExploreRoute
}

/** 위아래로 넘겨 보는 리캡 목록(S08 피드·S09 검색 결과 공통) */
data class RecapListState(
    val recaps: List<PublicRecap> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val ended: Boolean = false,
    internal val next: DocumentSnapshot? = null,
)

/** 위치 정보 열람 팝업("현재 보유 N 코인" · "20코인을 사용해 열람하시겠습니까?") */
data class UnlockDialogState(
    val recap: PublicRecap,
    /** 아직 못 읽었으면 null */
    val coins: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** 공개 여정 상세: 리캡과 장소(좌표·장소 이름) */
data class PublicDetailState(
    val recap: PublicRecap? = null,
    val places: Map<String, PublicPlace> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class CollectionState(
    val kind: MarkKind = MarkKind.LIKE,
    val items: List<RecapMark> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class ExploreUiState(
    val routes: List<ExploreRoute> = emptyList(),
    val feed: RecapListState = RecapListState(),
    val search: RecapListState = RecapListState(),
    val liked: Set<String> = emptySet(),
    val saved: Set<String> = emptySet(),
    val collection: CollectionState = CollectionState(),
    val viewer: PublicRecap? = null,
    val viewerError: String? = null,
    val unlock: UnlockDialogState? = null,
    val detail: PublicDetailState = PublicDetailState(),
    /** 잠깐 보여 줄 안내(토스트) */
    val message: String? = null,
) {
    val route: ExploreRoute? get() = routes.lastOrNull()
}

/**
 * 탐색 탭 상태 관리(S08 공개 영상 피드 · S09 검색 결과 · 모아보기 · 위치 정보 열람 팝업 · 공개 여정 상세).
 * - 피드·검색은 EXPLORE_PAGE_SIZE 개씩 읽고, 끝에 가까워지면 다음 묶음을 읽는다(비용 관리).
 * - 좋아요·저장은 화면에서 바로 바꾸고(채운 하트·책갈피) 서버에 저장한다. 실패하면 되돌린다.
 * - "장소 보기": 이미 열람했거나 그 여정의 구성원이면 바로 여정 상세, 아니면 20코인 팝업.
 */
class ExploreViewModel : ViewModel() {
    private val repository = ExploreRepository()
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private var uid: String? = null
    private var searchJob: Job? = null

    /** 탭을 처음 열 때(로그인한 사람이 바뀌면 다시) */
    fun start(uid: String) {
        if (this.uid == uid) return
        this.uid = uid
        _state.value = ExploreUiState()
        loadMore(search = false)
        viewModelScope.launch {
            val liked = runCatching { repository.loadMarkedIds(uid, MarkKind.LIKE) }.getOrDefault(emptySet())
            val saved = runCatching { repository.loadMarkedIds(uid, MarkKind.SAVE) }.getOrDefault(emptySet())
            _state.update { it.copy(liked = it.liked + liked, saved = it.saved + saved) }
        }
    }

    /** 피드를 처음부터 다시 읽는다(당겨서 새로 고침·오류 뒤 다시 시도) */
    fun refreshFeed() {
        _state.update { it.copy(feed = RecapListState()) }
        loadMore(search = false)
    }

    /** 목록 끝에 가까워지면 다음 묶음 */
    fun loadMore(search: Boolean) {
        val current = if (search) _state.value.search else _state.value.feed
        if (current.loading || current.ended) return
        val query = (_state.value.route as? ExploreRoute.Search)?.query
        if (search && query == null) return
        setList(search) { it.copy(loading = true, error = null) }
        val job = viewModelScope.launch {
            try {
                val page = if (search) repository.search(query!!, current.next) else repository.loadFeed(current.next)
                setList(search) {
                    it.copy(
                        recaps = (it.recaps + page.recaps).distinctBy { r -> r.id },
                        loading = false,
                        next = page.next,
                        ended = page.next == null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "공개 리캡 읽기 실패", e)
                setList(search) { it.copy(loading = false, error = "영상을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
        if (search) searchJob = job
    }

    private fun setList(search: Boolean, change: (RecapListState) -> RecapListState) {
        _state.update { if (search) it.copy(search = change(it.search)) else it.copy(feed = change(it.feed)) }
    }

    /** S09: 지역 또는 테마로 찾기 */
    fun search(text: String) {
        val query = text.trim()
        if (ExploreRepository.searchWords(query).isEmpty()) return
        searchJob?.cancel()
        _state.update { s ->
            val routes = s.routes.filterNot { it is ExploreRoute.Search } + ExploreRoute.Search(query)
            s.copy(routes = routes, search = RecapListState())
        }
        loadMore(search = true)
    }

    fun retrySearch() {
        _state.update { it.copy(search = it.search.copy(error = null, ended = false)) }
        loadMore(search = true)
    }

    /** 뒤로 가기: 연 화면을 하나 닫는다. 닫을 것이 없으면 false(→ 홈 탭으로) */
    fun back(): Boolean {
        if (_state.value.unlock != null) {
            _state.update { it.copy(unlock = null) }
            return true
        }
        if (_state.value.routes.isEmpty()) return false
        _state.update { it.copy(routes = it.routes.dropLast(1)) }
        return true
    }

    // ---------- 좋아요·저장 ----------

    fun toggleMark(kind: MarkKind, recap: PublicRecap) {
        val uid = uid ?: return
        val on = recap.id !in marks(kind)
        updateMarks(kind) { if (on) it + recap.id else it - recap.id }
        viewModelScope.launch {
            try {
                repository.setMark(uid, kind, recap, on)
                // 모아보기를 보고 있으면 목록도 맞춘다
                if (_state.value.collection.kind == kind && !on) {
                    _state.update { s -> s.copy(collection = s.collection.copy(items = s.collection.items.filterNot { it.recapId == recap.id })) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "${kind.label} 저장 실패", e)
                updateMarks(kind) { if (on) it - recap.id else it + recap.id }
                _state.update { it.copy(message = "${kind.label}을(를) 저장하지 못했어요. 다시 눌러 주세요.") }
            }
        }
    }

    private fun marks(kind: MarkKind) = if (kind == MarkKind.LIKE) _state.value.liked else _state.value.saved

    private fun updateMarks(kind: MarkKind, change: (Set<String>) -> Set<String>) {
        _state.update { if (kind == MarkKind.LIKE) it.copy(liked = change(it.liked)) else it.copy(saved = change(it.saved)) }
    }

    // ---------- 모아보기 ----------

    fun openCollection() {
        _state.update { s -> s.copy(routes = s.routes.filterNot { it is ExploreRoute.Collection } + ExploreRoute.Collection) }
        loadCollection(_state.value.collection.kind)
    }

    fun loadCollection(kind: MarkKind) {
        val uid = uid ?: return
        _state.update { it.copy(collection = CollectionState(kind = kind, loading = true)) }
        viewModelScope.launch {
            try {
                val items = repository.loadMarks(uid, kind)
                _state.update { s -> if (s.collection.kind == kind) s.copy(collection = s.collection.copy(items = items, loading = false)) else s }
            } catch (e: Exception) {
                Log.w(TAG, "모아보기 읽기 실패", e)
                _state.update { s -> s.copy(collection = s.collection.copy(loading = false, error = "목록을 불러오지 못했어요.")) }
            }
        }
    }

    /** 모아보기에서 고른 리캡을 연다 */
    fun openViewer(recapId: String) {
        _state.update { it.copy(routes = it.routes + ExploreRoute.Viewer(recapId), viewer = null, viewerError = null) }
        viewModelScope.launch {
            val recap = runCatching { repository.loadRecap(recapId) }.getOrNull()
            _state.update {
                if (recap == null) it.copy(viewerError = "이 영상은 더 이상 볼 수 없어요.") else it.copy(viewer = recap)
            }
        }
    }

    // ---------- 장소 보기(20코인) → 여정 상세 ----------

    fun openPlaces(recap: PublicRecap) {
        val uid = uid ?: return
        if (uid in recap.memberIds) {
            openDetail(recap)
            return
        }
        _state.update { it.copy(unlock = UnlockDialogState(recap)) }
        viewModelScope.launch {
            try {
                if (repository.isUnlocked(uid, recap.id)) {
                    _state.update { it.copy(unlock = null) }
                    openDetail(recap)
                    return@launch
                }
                val coins = repository.myCoins(uid)
                _state.update { s -> s.unlock?.takeIf { it.recap.id == recap.id }?.let { s.copy(unlock = it.copy(coins = coins)) } ?: s }
            } catch (e: Exception) {
                Log.w(TAG, "코인 읽기 실패", e)
                _state.update { s -> s.copy(unlock = s.unlock?.copy(error = "보유 코인을 불러오지 못했어요.")) }
            }
        }
    }

    fun dismissUnlock() = _state.update { it.copy(unlock = null) }

    /** "20코인 사용" */
    fun confirmUnlock() {
        val dialog = _state.value.unlock ?: return
        if (dialog.busy) return
        _state.update { it.copy(unlock = dialog.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                repository.unlockPlaces(dialog.recap.id)
                _state.update { it.copy(unlock = null) }
                openDetail(dialog.recap)
            } catch (e: Exception) {
                Log.w(TAG, "장소 열람 실패", e)
                val message = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                    "코인이 부족해요. 기록에 위치를 남기면 코인이 쌓여요."
                } else {
                    "열람하지 못했어요. 잠시 뒤 다시 시도해 주세요."
                }
                _state.update { s -> s.copy(unlock = s.unlock?.copy(busy = false, error = message)) }
            }
        }
    }

    private fun openDetail(recap: PublicRecap) {
        _state.update { it.copy(routes = it.routes + ExploreRoute.Detail(recap.id), detail = PublicDetailState(recap = recap)) }
        loadPlaces(recap)
    }

    fun retryPlaces() {
        _state.value.detail.recap?.let { loadPlaces(it) }
    }

    private fun loadPlaces(recap: PublicRecap) {
        _state.update { it.copy(detail = it.detail.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val places = repository.loadPlaces(recap.id)
                _state.update { s -> if (s.detail.recap?.id == recap.id) s.copy(detail = s.detail.copy(places = places, loading = false)) else s }
            } catch (e: Exception) {
                Log.w(TAG, "장소 읽기 실패", e)
                _state.update { s -> s.copy(detail = s.detail.copy(loading = false, error = "장소를 불러오지 못했어요.")) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** 공개 파일 주소(영상·대표 화면) */
    fun publicUrl(path: String): String = repository.publicUrl(path)

    private companion object {
        const val TAG = "Explore"
    }
}
