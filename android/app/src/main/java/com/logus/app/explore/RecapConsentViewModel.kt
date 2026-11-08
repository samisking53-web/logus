package com.logus.app.explore

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 업로드 동의 팝업 상태: 지금 보여 줄 요청(없으면 팝업 없음) */
data class ConsentUiState(
    val request: ConsentRequest? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** 답한 뒤 잠깐 보여 줄 안내(토스트) */
    val message: String? = null,
)

/**
 * DAY RECAP 업로드 동의 팝업 상태 관리.
 * 홈에 들어올 때와 동의 요청 알림 배너를 눌렀을 때 check 를 불러, 내가 아직 답하지 않은 요청이 있으면 팝업을 띄운다.
 * 여러 개면 하나씩 차례로 보여 준다.
 */
class RecapConsentViewModel : ViewModel() {
    private val repository = RecapConsentRepository()
    private val _state = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = _state.asStateFlow()

    private var queue: List<ConsentRequest> = emptyList()

    fun check(uid: String) {
        viewModelScope.launch {
            try {
                queue = repository.loadPending(uid)
                if (_state.value.request == null || _state.value.request?.id !in queue.map { it.id }) {
                    _state.update { it.copy(request = queue.firstOrNull(), error = null, busy = false) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "공개 동의 요청 읽기 실패", e)
            }
        }
    }

    fun answer(approve: Boolean) {
        val request = _state.value.request ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.answer(request.id, approve)
                val message = when {
                    !approve -> "동의하지 않았어요. 이번 탐색 공개는 취소돼요."
                    result.status == "published" -> "모두 동의해서 DAY RECAP을 탐색에 올렸어요."
                    else -> "동의했어요. 구성원 모두 동의하면 탐색에 올라가요."
                }
                queue = queue.filterNot { it.id == request.id }
                _state.update { ConsentUiState(request = queue.firstOrNull(), message = message) }
            } catch (e: Exception) {
                Log.w(TAG, "공개 동의 답하기 실패", e)
                _state.update { it.copy(busy = false, error = "보내지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.") }
            }
        }
    }

    /** X: 지금은 답하지 않고 닫는다(다음에 홈에 들어오면 다시 묻는다) */
    fun later() {
        val request = _state.value.request ?: return
        queue = queue.filterNot { it.id == request.id }
        _state.update { ConsentUiState(request = queue.firstOrNull()) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val TAG = "RecapConsent"
    }
}
