package com.logus.app.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import com.logus.app.journey.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 홈 화면 아래쪽에 보여 줄 내용 */
sealed interface HomeUiState {
    /** 진행 중인 여정을 찾는 중 */
    data object Loading : HomeUiState

    /** S01: 진행 중인 여정이 없음 → "현재 진행중인 여정이 없어요!" + 새 여정·초대 코드 버튼 */
    data object NoJourney : HomeUiState

    /** S01-A: 오늘이 여행 기간 안인 여정이 하나 이상 → 여정 카드를 모두(아래로 스크롤) + 새 여정·초대 코드 버튼 */
    data class Ongoing(val journeys: List<OngoingCard>) : HomeUiState

    /** 여정을 읽지 못함(인터넷·보안 규칙 등) */
    data class Failed(val message: String) : HomeUiState
}

/**
 * S01-A 여정 카드 하나: 여정 + 구성원(이름 동그라미) + 내 대표 사진.
 * coverPath 는 Storage 경로(journeys/{여정}/{내 uid}/cover_….jpg). 내 멤버 문서에 저장해서 내 홈에서만 보인다. 없으면 기본 그림.
 */
data class OngoingCard(val journey: Journey, val members: List<Member>, val coverPath: String? = null)

/**
 * 여정에 초대하기 팝업에 보여 줄 "내" 초대 코드(구성원마다 다르다).
 * journeyId 가 지금 여정과 같을 때만 쓴다. loading = 서버에서 받는 중, code 가 null 이고 loading 도 아니면 받지 못한 것.
 */
data class MyInviteCodeState(val journeyId: String? = null, val code: String? = null, val loading: Boolean = false)

/**
 * 홈(S01·S01-A) 상태 관리.
 * 로그인한 사람(uid)이 바뀔 때만 Firestore 를 다시 읽는다(탭을 오가도 다시 읽지 않아 비용을 아낀다).
 * 실시간 리스너 대신 한 번 읽기(get)를 쓴다.
 */
class HomeViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
    private val coverRepository: CoverPhotoRepository = CoverPhotoRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadedUid: String? = null

    /** 대표 사진 Storage 경로 → 내려받기 주소(한 번 받으면 기억한다) */
    private val coverUrls = mutableMapOf<String, String?>()

    private val _myInviteCode = MutableStateFlow(MyInviteCodeState())
    val myInviteCode: StateFlow<MyInviteCodeState> = _myInviteCode.asStateFlow()

    /**
     * 홈을 읽는다. firstJourneyId 를 넘기면 그 여정 카드를 맨 위에 둔다(방금 참여한 여정).
     * 진행 중인 여정이 여러 개면 모두 보여 준다(최근 시작한 순서). 여정마다 구성원 이름은 동시에 읽는다.
     */
    fun load(uid: String, force: Boolean = false, firstJourneyId: String? = null) {
        if (!force && uid == loadedUid) return
        loadedUid = uid
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                // 여정 날짜는 현지 달력 날짜라서 폰의 오늘 날짜와 비교한다
                val found = repository.findOngoingJourneys(uid, LocalDate.now().toString())
                if (found.isEmpty()) {
                    HomeUiState.NoJourney
                } else {
                    val cards = coroutineScope {
                        found.map { (journey, coverPath) ->
                            async {
                                val members = runCatching { repository.loadMembers(journey.memberIds) }.getOrDefault(emptyList())
                                OngoingCard(journey, members, coverPath)
                            }
                        }.awaitAll()
                    }
                    HomeUiState.Ongoing(cards.sortedByDescending { it.journey.id == firstJourneyId })
                }
            } catch (e: Exception) {
                Log.w(TAG, "진행 중인 여정 읽기 실패 (보안 규칙·색인 배포 확인)", e)
                loadedUid = null // 다음에 홈으로 오면 다시 시도한다
                HomeUiState.Failed("여정 정보를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
    }

    /**
     * S01-A 여정 카드의 친구 추가 버튼(여정에 초대하기 팝업)을 열 때: 그 여정 문서를 다시 읽어
     * 현재 인원수를 새로 고친다. 그사이 누가 참여했어도 팝업에 맞는 인원이 보인다.
     * 화면은 그대로 두고(Loading 으로 바꾸지 않음) 그 카드만 바꾼다. 구성원이 바뀌었을 때만 이름을 다시 읽는다(비용 절약).
     */
    fun refreshOngoing(journeyId: String) {
        val card = (_state.value as? HomeUiState.Ongoing)?.journeys?.firstOrNull { it.journey.id == journeyId } ?: return
        viewModelScope.launch {
            val fresh = runCatching { repository.loadJourney(journeyId) }
                .onFailure { Log.w(TAG, "여정 다시 읽기 실패", it) }
                .getOrNull() ?: return@launch
            val members = if (fresh.memberIds == card.journey.memberIds) {
                card.members
            } else {
                runCatching { repository.loadMembers(fresh.memberIds) }.getOrDefault(card.members)
            }
            replaceCard(journeyId) { it.copy(journey = fresh, members = members) }
        }
    }

    /** 대표 사진을 바꿨을 때: 그 카드의 사진만 바꾼다(홈을 다시 읽지 않는다) */
    fun setCover(journeyId: String, coverPath: String) {
        replaceCard(journeyId) { it.copy(coverPath = coverPath) }
    }

    /** 대표 사진 Storage 경로 → 내려받기 주소(못 받으면 null → 기본 그림). 한 번 받은 주소는 기억한다 */
    suspend fun coverUrl(path: String): String? {
        if (coverUrls.containsKey(path)) return coverUrls[path]
        val url = runCatching { coverRepository.downloadUrl(path) }
            .onFailure { Log.w(TAG, "대표 사진 주소 받기 실패", it) }
            .getOrNull()
        coverUrls[path] = url
        return url
    }

    private fun replaceCard(journeyId: String, change: (OngoingCard) -> OngoingCard) {
        _state.update { state ->
            if (state !is HomeUiState.Ongoing) return@update state
            HomeUiState.Ongoing(state.journeys.map { if (it.journey.id == journeyId) change(it) else it })
        }
    }

    /**
     * 여정에 초대하기 팝업을 열 때: 이 여정에서 쓰는 내 초대 코드를 받는다.
     * 한 번 받은 코드는 바뀌지 않으므로 같은 여정이면 다시 받지 않는다(받다가 실패했을 때만 다시 시도).
     */
    fun loadMyInviteCode(uid: String, journey: Journey) {
        val current = _myInviteCode.value
        if (current.journeyId == journey.id && (current.code != null || current.loading)) return
        _myInviteCode.value = MyInviteCodeState(journeyId = journey.id, loading = true)
        viewModelScope.launch {
            val code = runCatching { repository.myInviteCode(journey, uid) }
                .onFailure { Log.w(TAG, "내 초대 코드 받기 실패 (서버 함수 getInviteCode 배포 확인)", it) }
                .getOrNull()
            _myInviteCode.value = MyInviteCodeState(journeyId = journey.id, code = code, loading = false)
        }
    }

    /**
     * 초대를 수락하고 "홈으로 가기"를 눌렀을 때: 홈을 다시 읽고 방금 참여한 여정 카드를 맨 위에 둔다.
     * 오늘이 그 여정 기간 밖이면 홈 규칙대로 다른 진행 중 여정(S01-A) 또는 S01 을 보여 준다.
     */
    fun showJoined(uid: String, journeyId: String) {
        load(uid, force = true, firstJourneyId = journeyId)
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
