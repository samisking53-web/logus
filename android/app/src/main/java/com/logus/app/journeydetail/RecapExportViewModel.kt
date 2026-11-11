package com.logus.app.journeydetail

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.ExportException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** S17 DAY RECAP 영상 추출 상태: 아무것도 안 함 / 만드는 중(0~1) / 다 만듦(갤러리 주소) / 실패(안내 글) */
sealed interface RecapExportState {
    data object Idle : RecapExportState
    /** premium: 프리미엄(광고·워터마크 없음)으로 만드는 중인지. 일반 다운로드는 만드는 동안 광고를 보여 준다(2026-11-11) */
    data class Working(val progress: Float, val premium: Boolean = false) : RecapExportState
    /** premium: 프리미엄(워터마크 없음)으로 만들었는지 */
    data class Done(val uri: Uri, val premium: Boolean = false) : RecapExportState
    /** detail: 원인을 찾기 위한 오류 이름(팝업 아래 작은 글자. 팀원이 캡처해 개발에 쓴다) */
    data class Failed(val message: String, val detail: String? = null) : RecapExportState
}

/**
 * 추출 창의 "프리미엄 다운로드" 표시용: 이 여정의 이날(date = "YYYY-MM-DD")을 이미 50코인으로 열었는지.
 * unlocked 가 null 이면 아직 모름(읽는 중이거나 못 읽음. 눌렀을 때 서버에서 다시 확인한다)
 */
data class PremiumStatus(val journeyId: String, val date: String, val unlocked: Boolean?)

/**
 * 프리미엄 추출 확인 팝업("50코인을 사용할까요?"). coins 가 null 이면 보유 코인을 읽는 중.
 * dayLabel: "9.24 · 포르투 · DAY RECAP", busy: 서버에 코인을 쓰는 중, error: 안내 글
 */
data class PremiumConfirmState(
    val journeyId: String,
    val date: String,
    val journeyName: String,
    val dayLabel: String,
    val coins: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * S17 DAY RECAP "영상 추출 → 영상 다운로드" 상태 관리. 영상은 RecapExporter 가 폰에서 만든다.
 * 화면을 돌려도 만들기가 끊기지 않게 ViewModel 에서 돌린다. 여정 상세를 닫으면(ViewModel 정리) 멈춘다.
 * 2026-11-11 팀 요청: 일반 다운로드(무료·워터마크 있음, 만드는 동안 광고)와 프리미엄 다운로드(50코인·광고·워터마크 없음)로 나눈다.
 * 프리미엄은 한 번 내면 같은 여정의 같은 날 DAY RECAP 을 다시 받을 때도 무료다(서버 unlockPremiumExport 가 기억).
 */
class RecapExportViewModel(app: Application) : AndroidViewModel(app) {
    private val exporter = RecapExporter(app)
    private val premiumRepository = PremiumExportRepository()
    private val _state = MutableStateFlow<RecapExportState>(RecapExportState.Idle)
    val state: StateFlow<RecapExportState> = _state.asStateFlow()
    private val _premium = MutableStateFlow<PremiumStatus?>(null)
    /** 지금 보는 날의 프리미엄 추출을 이미 열었는지(추출 창의 "50코인" / "이날 무료" 표시) */
    val premium: StateFlow<PremiumStatus?> = _premium.asStateFlow()
    private val _premiumConfirm = MutableStateFlow<PremiumConfirmState?>(null)
    /** 프리미엄 추출 확인 팝업(null 이면 닫힘) */
    val premiumConfirm: StateFlow<PremiumConfirmState?> = _premiumConfirm.asStateFlow()
    private var job: Job? = null

    /**
     * 여정 상세에서 지금 보는 날의 DAY RECAP 을 영상으로 만든다.
     * premium = false: 일반 다운로드(워터마크 있음), true: 프리미엄 다운로드(워터마크 없음, 50코인을 낸 날만 부른다)
     */
    fun start(detail: JourneyDetailUiState, premium: Boolean = false) {
        val journey = detail.journey ?: return
        val day = detail.days.getOrNull(detail.dayIndex) ?: return
        if (detail.recapScenes.isEmpty() || _state.value is RecapExportState.Working) return
        val info = RecapExportInfo(
            title = journey.name,
            subtitle = listOf("${day.year}.${day.monthValue}.${day.dayOfMonth}", journey.city).filter { it.isNotBlank() }.joinToString(" · "),
            fileStem = fileStem(day),
            watermark = !premium,
        )
        _state.value = RecapExportState.Working(0f, premium)
        job = viewModelScope.launch {
            _state.value = try {
                val uri = exporter.export(info, detail.recapScenes, detail.members) { p ->
                    if (_state.value is RecapExportState.Working) _state.value = RecapExportState.Working(p, premium)
                }
                RecapExportState.Done(uri, premium)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "DAY RECAP 영상 만들기 실패", e)
                val message = e.message?.takeIf { it.contains("인터넷") } ?: "영상을 만들지 못했어요. 잠시 뒤 다시 해 주세요."
                RecapExportState.Failed(
                    // 코인은 이미 냈으니 다시 받을 때 무료라는 것을 알려 준다
                    if (premium) "$message\n${PremiumExport.COINS}코인은 이미 냈으니 이날은 다시 받아도 무료예요." else message,
                    detail = errorDetail(e),
                )
            }
        }
    }

    // ---------- 프리미엄 다운로드(50코인, 워터마크 없음) ----------

    /** 추출 창을 열 때: 이날 프리미엄을 이미 열었는지 읽는다(이미 연 것을 아는 날은 다시 읽지 않음) */
    fun checkPremium(detail: JourneyDetailUiState) {
        val (journeyId, date) = keyOf(detail) ?: return
        val known = _premium.value?.takeIf { it.journeyId == journeyId && it.date == date }
        if (known?.unlocked == true) return
        _premium.value = PremiumStatus(journeyId, date, known?.unlocked)
        viewModelScope.launch {
            val unlocked = try {
                premiumRepository.isUnlocked(journeyId, date)
            } catch (e: Exception) {
                Log.w(TAG, "프리미엄 추출 여부 읽기 실패", e)
                null
            }
            _premium.update { if (it?.journeyId == journeyId && it.date == date) it.copy(unlocked = unlocked) else it }
        }
    }

    /**
     * 추출 창 "프리미엄 다운로드": 이날 이미 열었으면 바로 워터마크 없이 만들고(무료),
     * 아니면 "50코인을 사용할까요?" 확인 팝업을 띄운다(보유 코인을 읽어 보여 줌).
     */
    fun askPremium(detail: JourneyDetailUiState) {
        if (_state.value is RecapExportState.Working || detail.recapScenes.isEmpty()) return
        val journey = detail.journey ?: return
        val day = detail.days.getOrNull(detail.dayIndex) ?: return
        val (journeyId, date) = keyOf(detail) ?: return
        if (_premium.value?.let { it.journeyId == journeyId && it.date == date && it.unlocked == true } == true) {
            start(detail, premium = true)
            return
        }
        _premiumConfirm.value = PremiumConfirmState(
            journeyId = journeyId,
            date = date,
            journeyName = journey.name,
            dayLabel = listOf("${day.monthValue}.${day.dayOfMonth}", journey.city, "DAY RECAP").filter { it.isNotBlank() }.joinToString(" · "),
        )
        viewModelScope.launch {
            try {
                // 다른 폰에서 이미 열었을 수도 있어서 한 번 더 확인한다
                if (premiumRepository.isUnlocked(journeyId, date)) {
                    _premium.value = PremiumStatus(journeyId, date, true)
                    if (_premiumConfirm.value?.date == date && _premiumConfirm.value?.journeyId == journeyId) {
                        _premiumConfirm.value = null
                        start(detail, premium = true)
                    }
                    return@launch
                }
                val coins = premiumRepository.myCoins()
                _premiumConfirm.update { if (it?.journeyId == journeyId && it.date == date) it.copy(coins = coins) else it }
            } catch (e: Exception) {
                Log.w(TAG, "보유 코인 읽기 실패", e)
                _premiumConfirm.update { it?.copy(error = "보유 코인을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 확인 팝업 "50코인 사용": 서버가 코인을 빼고 이날을 열어 두면 바로 워터마크 없이 만든다 */
    fun confirmPremium(detail: JourneyDetailUiState) {
        val dialog = _premiumConfirm.value ?: return
        if (dialog.busy) return
        if (keyOf(detail) != (dialog.journeyId to dialog.date)) {
            _premiumConfirm.value = null // 그사이 다른 날로 바뀌었으면 닫는다
            return
        }
        _premiumConfirm.value = dialog.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val left = premiumRepository.unlock(dialog.journeyId, dialog.date)
                _premium.value = PremiumStatus(dialog.journeyId, dialog.date, true)
                _premiumConfirm.value = null
                Log.d(TAG, "프리미엄 추출 열기 완료, 남은 코인 $left")
                start(detail, premium = true)
            } catch (e: Exception) {
                Log.w(TAG, "프리미엄 추출 열기 실패", e)
                val message = when ((e as? FirebaseFunctionsException)?.code) {
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message?.takeIf { it.isNotBlank() } ?: "코인이 부족해요."
                    FirebaseFunctionsException.Code.PERMISSION_DENIED -> "이 여정의 구성원만 할 수 있어요."
                    else -> "코인을 쓰지 못했어요. 인터넷 연결을 확인하고 다시 해 주세요."
                }
                _premiumConfirm.update { it?.copy(busy = false, error = message) }
            }
        }
    }

    /** 확인 팝업 닫기(코인을 쓰는 중에는 닫지 않음) */
    fun dismissPremium() {
        if (_premiumConfirm.value?.busy != true) _premiumConfirm.value = null
    }

    /** 이 여정·이날을 가리키는 열쇠(여정 ID, "YYYY-MM-DD") */
    private fun keyOf(detail: JourneyDetailUiState): Pair<String, String>? {
        val journey = detail.journey ?: return null
        val day = detail.days.getOrNull(detail.dayIndex) ?: return null
        return journey.id to day.toString()
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

    /** 오류 이름과 원인(가장 안쪽 원인까지)을 한 줄로: "ERROR_CODE_… · IllegalStateException: …" */
    private fun errorDetail(e: Exception): String {
        val code = (e as? ExportException)?.errorCodeName
        var root: Throwable = e
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return listOfNotNull(code, "${root::class.java.simpleName}: ${root.message.orEmpty().take(200)}").joinToString(" · ")
    }

    private fun fileStem(day: LocalDate) = "LOGUS_DAYRECAP_${day}_${System.currentTimeMillis() % 100000}"

    private companion object {
        const val TAG = "RecapExportViewModel"
    }
}
