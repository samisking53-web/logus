package com.logus.app.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import com.logus.app.journey.InvitePreview
import com.logus.app.journey.JourneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 초대 코드로 참여하기 상태: 입력 팝업 → (서버 확인) → I01 초대 확인 화면 → (참여) → 수락 완료 팝업 */
data class JoinUiState(
    /** 초대 코드 입력 팝업을 보여 주는 중 */
    val dialogOpen: Boolean = false,
    /** "여정 확인하기"를 눌러 서버가 코드를 확인하는 중 */
    val checking: Boolean = false,
    /** 팝업에 보여 줄 오류(없는 코드·만료·횟수 초과·이미 참여) */
    val error: String? = null,
    /** 확인된 여정 요약. 있으면 I01 초대 확인 화면을 보여 준다 */
    val preview: InvitePreview? = null,
    /** I01 "초대 수락하기"를 눌러 서버가 참여 처리하는 중 */
    val accepting: Boolean = false,
    /** I01 화면에 보여 줄 참여 실패 문구 */
    val acceptError: String? = null,
    /** 참여한 여정 ID. 있으면 I01 위에 "초대가 수락되었습니다!" 팝업을 보여 준다 */
    val joinedJourneyId: String? = null,
)

/**
 * 홈 "초대 코드로 참여" → 초대 코드 입력 팝업 → "여정 확인하기" → I01 초대 확인 화면.
 * → "초대 수락하기"(서버 joinJourney 로 참여) → "초대가 수락되었습니다!" 팝업 → "홈으로 가기"(S01-A).
 */
class JoinViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    fun openDialog() {
        _state.value = JoinUiState(dialogOpen = true)
    }

    fun closeDialog() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(dialogOpen = false, error = null)
    }

    /** 코드를 고치면 이전 오류를 지운다 */
    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    /** "여정 확인하기": 서버(previewInvite)에 코드를 확인한다. 맞으면 팝업을 닫고 I01 화면으로 */
    fun check(code: String) {
        val current = _state.value
        if (current.checking) return
        _state.value = current.copy(checking = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                val preview = repository.previewInvite(code)
                if (preview.alreadyMember) {
                    _state.value.copy(checking = false, error = "이미 함께하고 있는 여정이에요.")
                } else {
                    JoinUiState(preview = preview)
                }
            } catch (e: Exception) {
                Log.w(TAG, "초대 코드 확인 실패 (previewInvite 함수 배포 확인)", e)
                _state.value.copy(checking = false, error = e.toKoreanMessage(action = "확인"))
            }
        }
    }

    /** I01 초대 확인 화면을 닫고 홈으로 */
    fun closePreview() {
        if (_state.value.accepting) return // 참여 처리 중에는 닫지 않는다
        _state.value = JoinUiState()
    }

    /** I01 "초대 수락하기": 서버(joinJourney)로 참여한다. 성공하면 수락 완료 팝업을 띄운다 */
    fun accept() {
        val current = _state.value
        val preview = current.preview ?: return
        if (current.accepting || current.joinedJourneyId != null) return
        _state.value = current.copy(accepting = true, acceptError = null)
        viewModelScope.launch {
            _state.value = try {
                val journeyId = repository.joinJourney(preview.code)
                _state.value.copy(accepting = false, joinedJourneyId = journeyId)
            } catch (e: Exception) {
                Log.w(TAG, "여정 참여 실패 (joinJourney 함수 배포 확인)", e)
                _state.value.copy(accepting = false, acceptError = e.toKoreanMessage(action = "참여"))
            }
        }
    }

    /** 수락 완료 팝업의 "홈으로 가기": 초대 흐름을 모두 닫는다(홈 화면은 MainScreen 이 다시 읽는다) */
    fun finish() {
        _state.value = JoinUiState()
    }

    private companion object {
        const val TAG = "JoinViewModel"
    }
}

/** action: "확인"(여정 확인하기) 또는 "참여"(초대 수락하기) */
private fun Exception.toKoreanMessage(action: String): String = when {
    this is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
    this is FirebaseFunctionsException -> when (code) {
        // 서버(functions/src)가 보낸 한국어 문구를 그대로 보여 준다
        // 함수를 아직 배포하지 않았을 때도 NOT_FOUND 가 오는데, 그때는 한국어 문구가 없다
        FirebaseFunctionsException.Code.NOT_FOUND ->
            message?.takeIf { it.contains("코드") || it.contains("여정") } ?: "초대 $action 기능이 아직 준비되지 않았어요. (서버 함수 배포 필요)"
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        -> message ?: if (action == "참여") "여정에 참여할 수 없어요." else "초대 코드를 확인할 수 없어요."
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "인터넷 연결을 확인해 주세요."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "다시 로그인해 주세요."
        else -> failed(action)
    }
    else -> failed(action)
}

private fun failed(action: String) =
    if (action == "참여") "여정에 참여하지 못했어요. 잠시 후 다시 시도해 주세요." else "초대 코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요."
