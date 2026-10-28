package com.logus.app.journeydetail

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** S17 DAY RECAP 영상 추출 상태: 아무것도 안 함 / 만드는 중(0~1) / 다 만듦(갤러리 주소) / 실패(안내 글) */
sealed interface RecapExportState {
    data object Idle : RecapExportState
    data class Working(val progress: Float) : RecapExportState
    data class Done(val uri: Uri) : RecapExportState
    data class Failed(val message: String) : RecapExportState
}

/**
 * S17 DAY RECAP "영상 추출 → 영상 다운로드" 상태 관리. 영상은 RecapExporter 가 폰에서 만든다.
 * 화면을 돌려도 만들기가 끊기지 않게 ViewModel 에서 돌린다. 여정 상세를 닫으면(ViewModel 정리) 멈춘다.
 */
class RecapExportViewModel(app: Application) : AndroidViewModel(app) {
    private val exporter = RecapExporter(app)
    private val _state = MutableStateFlow<RecapExportState>(RecapExportState.Idle)
    val state: StateFlow<RecapExportState> = _state.asStateFlow()
    private var job: Job? = null

    /** 여정 상세에서 지금 보는 날의 DAY RECAP 을 영상으로 만든다 */
    fun start(detail: JourneyDetailUiState) {
        val journey = detail.journey ?: return
        val day = detail.days.getOrNull(detail.dayIndex) ?: return
        if (detail.recapScenes.isEmpty() || _state.value is RecapExportState.Working) return
        val info = RecapExportInfo(
            title = journey.name,
            subtitle = listOf("${day.year}.${day.monthValue}.${day.dayOfMonth}", journey.city).filter { it.isNotBlank() }.joinToString(" · "),
            fileStem = fileStem(day),
        )
        _state.value = RecapExportState.Working(0f)
        job = viewModelScope.launch {
            _state.value = try {
                val uri = exporter.export(info, detail.recapScenes, detail.members) { p ->
                    if (_state.value is RecapExportState.Working) _state.value = RecapExportState.Working(p)
                }
                RecapExportState.Done(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "DAY RECAP 영상 만들기 실패", e)
                RecapExportState.Failed(
                    e.message?.takeIf { it.contains("인터넷") } ?: "영상을 만들지 못했어요. 잠시 뒤 다시 해 주세요.",
                )
            }
        }
    }

    /** 만드는 중 "취소" */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = RecapExportState.Idle
    }

    /** 완료·실패 팝업 닫기 */
    fun dismiss() {
        if (_state.value !is RecapExportState.Working) _state.value = RecapExportState.Idle
    }

    private fun fileStem(day: LocalDate) = "LOGUS_DAYRECAP_${day}_${System.currentTimeMillis() % 100000}"

    private companion object {
        const val TAG = "RecapExportViewModel"
    }
}
