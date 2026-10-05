package com.logus.app.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import com.logus.app.journey.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 홈 화면 아래쪽에 보여 줄 내용 */
sealed interface HomeUiState {
    /** 진행 중인 여정을 찾는 중 */
    data object Loading : HomeUiState

    /** S01: 진행 중인 여정이 없음 → "현재 진행중인 여정이 없어요!" + 새 여정·초대 코드 버튼 */
    data object NoJourney : HomeUiState

    /** S01-A: 오늘이 여행 기간 안인 여정이 있음 → 여정 카드 + 지금 기록하기 */
    data class Ongoing(val journey: Journey, val members: List<Member>) : HomeUiState

    /** 여정을 읽지 못함(인터넷·보안 규칙 등) */
    data class Failed(val message: String) : HomeUiState
}

/**
 * 홈(S01·S01-A) 상태 관리.
 * 로그인한 사람(uid)이 바뀔 때만 Firestore 를 다시 읽는다(탭을 오가도 다시 읽지 않아 비용을 아낀다).
 * 실시간 리스너 대신 한 번 읽기(get)를 쓴다.
 */
class HomeViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadedUid: String? = null

    fun load(uid: String, force: Boolean = false) {
        if (!force && uid == loadedUid) return
        loadedUid = uid
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                // 여정 날짜는 현지 달력 날짜라서 폰의 오늘 날짜와 비교한다
                val journey = repository.findOngoingJourney(uid, LocalDate.now().toString())
                if (journey == null) {
                    HomeUiState.NoJourney
                } else {
                    HomeUiState.Ongoing(journey, repository.loadMembers(journey.memberIds))
                }
            } catch (e: Exception) {
                Log.w(TAG, "진행 중인 여정 읽기 실패 (보안 규칙·색인 배포 확인)", e)
                loadedUid = null // 다음에 홈으로 오면 다시 시도한다
                HomeUiState.Failed("여정 정보를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
    }

    /**
     * 초대를 수락하고 "홈으로 가기"를 눌렀을 때: 방금 참여한 여정을 바로 S01-A 로 보여 준다.
     * (진행 중인 내 여정이 여러 개여도 방금 참여한 여정이 보이게 그 문서를 직접 읽는다)
     * 오늘이 그 여정 기간 밖이면 홈 규칙대로 다른 진행 중 여정(S01-A) 또는 S01 을 보여 준다.
     */
    fun showJoined(uid: String, journeyId: String) {
        loadedUid = uid
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val joined = runCatching { repository.loadJourney(journeyId) }
                .onFailure { Log.w(TAG, "참여한 여정 읽기 실패", it) }
                .getOrNull()
            if (joined != null && joined.startDate <= today && today <= joined.endDate) {
                val members = runCatching { repository.loadMembers(joined.memberIds) }.getOrDefault(emptyList())
                _state.value = HomeUiState.Ongoing(joined, members)
            } else {
                load(uid, force = true)
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
