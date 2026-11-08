package com.logus.app.explore

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.logus.app.journeydetail.JourneyDetailUiState
import com.logus.app.journeydetail.RecapScene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "탐색에 자동 올리기"를 누른 뒤 보여 줄 확인 팝업 내용 */
data class PublishAsk(
    val journeyId: String,
    val journeyName: String,
    val date: String,
    val city: String,
    val memberCount: Int,
    val scenes: List<ConsentScene>,
    val busy: Boolean = false,
    val error: String? = null,
)

/** 업로드 동의 팝업 상태: 지금 보여 줄 요청(없으면 팝업 없음) */
data class ConsentUiState(
    /** 내가 요청하려는 공개(확인 팝업). 없으면 null */
    val publishAsk: PublishAsk? = null,
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
                _state.update { it.copy(request = queue.firstOrNull(), busy = false, error = null, message = message) }
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
        _state.update { it.copy(request = queue.firstOrNull(), busy = false, error = null) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * S17 "영상 추출" → "탐색에 자동 올리기": 지금 보고 있는 날의 DAY RECAP 장면으로 확인 팝업을 연다.
     * 장면은 앱 DAY RECAP 과 똑같이(buildRecapScenes 결과) 시간대마다 칸 순서대로 기록 ID 를 보낸다(지도 장면은 빼고).
     */
    fun askPublish(detail: JourneyDetailUiState) {
        val journey = detail.journey ?: return
        val day = detail.days.getOrNull(detail.dayIndex) ?: return
        val scenes = detail.recapScenes.filterIsInstance<RecapScene>().mapNotNull { scene ->
            val ids = scene.clips.mapNotNull { it.log?.id }.distinct().take(4)
            if (ids.isEmpty()) null
            else ConsentScene(label = "%02d:%02d".format(scene.startMinute / 60, scene.startMinute % 60), logIds = ids)
        }.take(24)
        if (scenes.isEmpty()) {
            _state.update { it.copy(message = "올릴 DAY RECAP 장면이 없어요") }
            return
        }
        _state.update {
            it.copy(
                publishAsk = PublishAsk(
                    journeyId = journey.id,
                    journeyName = journey.name,
                    date = day.toString(),
                    city = journey.city,
                    memberCount = journey.memberIds.size,
                    scenes = scenes,
                ),
            )
        }
    }

    fun dismissPublish() = _state.update { s -> if (s.publishAsk?.busy == true) s else s.copy(publishAsk = null) }

    /** 확인 팝업 "동의 요청 보내기" → 서버가 다른 구성원에게 알림을 보낸다(혼자면 바로 공개) */
    fun confirmPublish() {
        val ask = _state.value.publishAsk ?: return
        if (ask.busy) return
        _state.update { it.copy(publishAsk = ask.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                val result = repository.requestPublish(ask.journeyId, ask.date, ask.scenes)
                val message = when {
                    result.status == "published" -> "탐색에 올렸어요. 탐색 탭에서 볼 수 있어요."
                    result.alreadyRequested -> "이미 구성원들의 동의를 기다리고 있어요."
                    else -> "구성원들에게 동의 요청을 보냈어요. 모두 동의하면 탐색에 올라가요."
                }
                _state.update { it.copy(publishAsk = null, message = message) }
            } catch (e: Exception) {
                Log.w(TAG, "탐색 공개 요청 실패", e)
                val error = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.ALREADY_EXISTS) {
                    "이날 DAY RECAP은 이미 탐색에 올렸어요."
                } else {
                    "요청을 보내지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요."
                }
                _state.update { s -> s.copy(publishAsk = s.publishAsk?.copy(busy = false, error = error)) }
            }
        }
    }

    private companion object {
        const val TAG = "RecapConsent"
    }
}
