package com.logus.app.record

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

/** S05 기록 올리기 상태 */
data class LogUploadUiState(
    /** "위치 없이 저장"에 체크했는지 */
    val withoutLocation: Boolean = false,
    /** L01 지도에서 고른 장소(좌표·이름·주소). 있으면 위치와 함께 올린다 */
    val place: PickedPlace? = null,
    /** 넣은 테마 태그(# 없이, 최대 3개) */
    val themes: List<String> = emptyList(),
    /** 지금 치고 있는 태그 글자(# 없이) */
    val themeInput: String = "",
    /** 태그 입력 안내(최대 3개·이미 넣은 태그) */
    val themeHint: String? = null,
    /** 올리는 중 */
    val uploading: Boolean = false,
    /** 사용자에게 보여 줄 오류 문구 */
    val error: String? = null,
    /** 여정에 다 올렸다(MainScreen 이 보고 홈으로 돌아간다) */
    val done: Boolean = false,
    /** 위치와 함께 올려서 받은 코인 수 */
    val coinsGranted: Int = 0,
    /** 기록은 올렸지만 위치 저장(saveLogLocation)에 실패했다 */
    val locationSaveFailed: Boolean = false,
)

/** 테마 태그는 최대 3개 */
const val MAX_THEMES = 3

/** 태그 하나의 최대 글자 수(보안 규칙과 같다) */
const val MAX_THEME_LENGTH = 20

/**
 * S05 기록 올리기: 위치 없이 저장 체크, 테마 태그(인스타그램 해시태그처럼 # 이 앞에 붙는다), 여정에 올리기.
 * - 태그: 띄어쓰기·쉼표·# 를 치거나 키보드의 완료를 누르면 지금까지 친 글자가 태그 하나가 된다.
 *   글자·숫자·_ 만 남기고, 최대 3개, 같은 태그는 한 번만.
 * - 위치: "위치 확인 · 지도 열기"(L01)에서 고른 장소와 함께 올리거나(+10코인), "위치 없이 저장"을 켜고 올린다.
 *   둘 중 하나는 해야 올릴 수 있다. 둘은 함께 쓸 수 없다(하나를 고르면 다른 하나가 풀린다).
 */
class LogUploadViewModel(
    private val repository: LogRepository = LogRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LogUploadUiState())
    val state: StateFlow<LogUploadUiState> = _state.asStateFlow()

    /** 지금 S05 에 보여 주는 영상 파일 경로(같은 영상이면 입력한 내용을 그대로 둔다) */
    private var videoPath: String? = null

    /** S04 를 새로 열 때: 지난번 올리기 상태(완료 등)를 지운다 */
    fun clear() {
        if (_state.value.uploading) return
        videoPath = null
        _state.value = LogUploadUiState()
    }

    /** S05 를 열 때: 새 영상이면 처음 상태로 되돌린다 */
    fun prepare(videoPath: String) {
        if (this.videoPath == videoPath || _state.value.uploading) return
        this.videoPath = videoPath
        _state.value = LogUploadUiState()
    }

    fun setWithoutLocation(checked: Boolean) {
        _state.update { it.copy(withoutLocation = checked, place = if (checked) null else it.place, error = null) }
    }

    /** L01 "이 위치 사용": 고른 장소를 넣고 "위치 없이 저장"은 끈다 */
    fun setPlace(place: PickedPlace) {
        _state.update { it.copy(place = place, withoutLocation = false, error = null) }
    }

    /** 태그 입력칸 글자가 바뀔 때. 구분 글자(띄어쓰기·쉼표·#)가 나오면 그 앞까지를 태그로 넣는다 */
    fun onThemeInput(raw: String) {
        val parts = raw.split(SEPARATORS)
        if (parts.size == 1) {
            _state.update { it.copy(themeInput = cleanTag(raw), themeHint = null) }
            return
        }
        parts.dropLast(1).forEach { addTheme(it) }
        // 마지막 조각은 아직 치는 중인 글자
        _state.update { it.copy(themeInput = cleanTag(parts.last())) }
    }

    /** 키보드의 완료를 누르면 지금 치던 글자를 태그로 넣는다 */
    fun commitThemeInput() {
        addTheme(_state.value.themeInput)
        _state.update { it.copy(themeInput = "") }
    }

    fun removeTheme(tag: String) {
        _state.update { it.copy(themes = it.themes - tag, themeHint = null) }
    }

    private fun addTheme(raw: String) {
        val tag = cleanTag(raw)
        if (tag.isEmpty()) return
        val current = _state.value
        when {
            current.themes.size >= MAX_THEMES ->
                _state.update { it.copy(themeHint = "태그는 최대 ${MAX_THEMES}개까지 넣을 수 있어요") }
            current.themes.any { it.equals(tag, ignoreCase = true) } ->
                _state.update { it.copy(themeHint = "이미 넣은 태그예요") }
            else -> _state.update { it.copy(themes = it.themes + tag, themeHint = null) }
        }
    }

    /**
     * "여정에 올리기". 지도에서 위치를 고르거나 "위치 없이 저장"을 켜야 올라간다.
     * ① 영상·기록 올리기(위치 비움) → ② 위치를 골랐으면 saveLogLocation 으로 위치 저장 + 10코인.
     * 다 올리면 done = true 가 되고, MainScreen 이 영상 파일을 지우고 홈으로 돌아간다.
     * ②만 실패하면 기록은 올라간 것이므로 done 으로 두고 locationSaveFailed 로 알린다.
     */
    fun upload(journeyId: String, uid: String, video: File, capturedAtMillis: Long) {
        val current = _state.value
        if (current.uploading || current.done) return
        if (!current.withoutLocation && current.place == null) {
            _state.update {
                it.copy(error = "먼저 '위치 확인 · 지도 열기'로 위치를 확인하거나, '위치 없이 저장'을 켜 주세요.")
            }
            return
        }
        // 치다 만 태그도 함께 넣는다
        if (current.themeInput.isNotBlank()) commitThemeInput()
        val themes = _state.value.themes
        val place = if (current.withoutLocation) null else current.place

        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            try {
                val logId = repository.uploadVideoLog(
                    journeyId = journeyId,
                    uid = uid,
                    video = video,
                    capturedAtMillis = capturedAtMillis,
                    capturedTz = ZoneId.systemDefault().id,
                    themes = themes,
                )
                if (place == null) {
                    _state.update { it.copy(uploading = false, done = true) }
                    return@launch
                }
                val coins = try {
                    repository.saveLocation(journeyId, logId, place)
                } catch (e: Exception) {
                    Log.w(TAG, "위치 저장 실패 (saveLogLocation 함수 배포 확인)", e)
                    _state.update { it.copy(uploading = false, done = true, locationSaveFailed = true) }
                    return@launch
                }
                _state.update { it.copy(uploading = false, done = true, coinsGranted = coins) }
            } catch (e: Exception) {
                Log.w(TAG, "기록 올리기 실패 (보안 규칙·Storage 배포 확인)", e)
                _state.update { it.copy(uploading = false, error = e.toKoreanMessage()) }
            }
        }
    }

    private companion object {
        const val TAG = "LogUploadViewModel"

        /** 태그를 나누는 글자: 띄어쓰기·줄바꿈, 쉼표, # */
        val SEPARATORS = Regex("[\\s,#]+")

        /** 글자(한글·영문)·숫자·_ 만 남기고 20자까지 */
        fun cleanTag(raw: String): String =
            raw.filter { it.isLetterOrDigit() || it == '_' }.take(MAX_THEME_LENGTH)
    }
}

private fun Exception.toKoreanMessage(): String = when {
    this is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
    this is StorageException && errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
        "인터넷 연결을 확인해 주세요."
    this is StorageException && errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
        "영상을 올릴 권한이 없어요. (여정 구성원인지, 영상이 50MB 미만인지 확인)"
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "기록을 저장할 권한이 없어요. (보안 규칙 배포 확인 필요)"
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        "인터넷 연결을 확인해 주세요."
    else -> "여정에 올리지 못했어요. 잠시 후 다시 시도해 주세요."
}
