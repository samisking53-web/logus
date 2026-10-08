package com.logus.app.record

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** S04 촬영 단계 */
enum class CapturePhase {
    /** 촬영 전(또는 실패 뒤) */
    Ready,

    /** 녹화 중 */
    Recording,

    /** 촬영 완료(영상 파일이 있다) */
    Done,
}

/** S04 앱 내 카메라 상태 */
data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.Ready,
    /** 지금까지 찍은 시간(밀리초). 10초(MAX_VIDEO_MILLIS)까지 */
    val elapsedMs: Long = 0,
    /** 촬영을 마친 영상 파일(폰 안 앱 전용 임시 폴더). 기록 올리기(S05)에서 올린다 */
    val videoFile: File? = null,
    /** 사용자에게 보여 줄 오류 문구 */
    val error: String? = null,
    /** 공통 촬영 알림 간격(1·2·3시간, 받지 않으면 null) */
    val notifyIntervalHours: Int? = null,
    /** 알림 간격을 다 읽었는지(읽기 전에는 아래 알림 문구를 숨긴다) */
    val notifyLoaded: Boolean = false,
)

/** 한 번에 찍을 수 있는 영상 길이(10초) */
const val MAX_VIDEO_MILLIS = 10_000L

/**
 * S04 앱 내 카메라: 영상 녹화(최대 10초)와 공통 알림 간격을 맡는다.
 * - 카메라 미리보기·16:9 잘라 찍기는 화면(CaptureScreen)의 LifecycleCameraController 가 하고,
 *   여기서는 그 컨트롤러로 녹화를 시작·멈추고 진행 시간을 받는다.
 * - 영상은 폰 안 앱 전용 임시 폴더(cacheDir/captures)에 저장한다. 갤러리에는 저장하지 않는다.
 * - 10초가 되면 CameraX 가 저절로 멈춘다(FileOutputOptions.setDurationLimitMillis).
 */
class CaptureViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** 지금 녹화 중인 것(없으면 null) */
    private var recording: Recording? = null

    /** true 면 녹화가 끝났을 때 파일을 지운다(뒤로 가기로 나갈 때) */
    private var discardOnFinish = false

    /** S04 를 열 때: 상태를 처음으로 되돌리고 공통 알림 간격을 읽는다 */
    fun open(journey: Journey) {
        discardAndStop()
        _state.value = CaptureUiState()
        viewModelScope.launch {
            val hours = runCatching { repository.loadCommonNotifyInterval(journey) }
                .onFailure { Log.w(TAG, "공통 알림 간격 읽기 실패", it) }
                .getOrNull()
            _state.update { it.copy(notifyIntervalHours = hours, notifyLoaded = true) }
        }
    }

    /**
     * 촬영 시작. withAudio 는 마이크 권한이 있을 때만 true(없으면 소리 없이 찍는다).
     * 다시 찍으면 앞에서 찍은 영상 파일은 지운다.
     */
    @SuppressLint("MissingPermission") // 마이크 권한은 화면에서 확인한 뒤 withAudio 로 넘긴다
    fun startRecording(context: Context, controller: LifecycleCameraController, withAudio: Boolean) {
        if (recording != null) return
        _state.value.videoFile?.delete()
        val file = newVideoFile(context)
        discardOnFinish = false
        _state.update { it.copy(phase = CapturePhase.Recording, elapsedMs = 0, videoFile = null, error = null) }
        recording = try {
            controller.startRecording(
                FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_VIDEO_MILLIS).build(),
                AudioConfig.create(withAudio),
                ContextCompat.getMainExecutor(context),
            ) { event -> onEvent(event, file) }
        } catch (e: Exception) {
            // 카메라가 아직 준비되지 않았을 때 등
            Log.w(TAG, "녹화 시작 실패", e)
            _state.update { it.copy(phase = CapturePhase.Ready, error = "카메라를 준비하는 중이에요. 잠시 후 다시 눌러 주세요.") }
            null
        }
    }

    /** 촬영 멈추기(10초 전에 멈출 때). 멈춘 뒤 Finalize 이벤트에서 촬영 완료가 된다 */
    fun stopRecording() {
        recording?.stop()
    }

    /** 뒤로 가기로 나갈 때: 녹화 중이면 멈추고 그 영상은 버린다. 다 찍은 영상도 지운다 */
    fun discardAndStop() {
        if (recording != null) {
            discardOnFinish = true
            recording?.stop()
        }
        _state.value.videoFile?.delete()
        _state.update { it.copy(phase = CapturePhase.Ready, elapsedMs = 0, videoFile = null) }
    }

    private fun onEvent(event: VideoRecordEvent, file: File) {
        when (event) {
            is VideoRecordEvent.Status -> {
                val ms = event.recordingStats.recordedDurationNanos / 1_000_000
                if (!discardOnFinish) _state.update { it.copy(elapsedMs = ms.coerceAtMost(MAX_VIDEO_MILLIS)) }
            }

            is VideoRecordEvent.Finalize -> {
                recording = null
                val ms = (event.recordingStats.recordedDurationNanos / 1_000_000).coerceAtMost(MAX_VIDEO_MILLIS)
                // 10초가 되어 저절로 멈춘 것도 정상 촬영이다
                val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                when {
                    discardOnFinish -> {
                        file.delete()
                        discardOnFinish = false
                    }

                    ok -> _state.update { it.copy(phase = CapturePhase.Done, elapsedMs = ms, videoFile = file) }

                    else -> {
                        Log.w(TAG, "녹화 실패 (오류 ${event.error})", event.cause)
                        file.delete()
                        val message = if (event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                            "영상이 너무 짧아요. 조금 더 길게 찍어 주세요."
                        } else {
                            "촬영하지 못했어요. 다시 시도해 주세요."
                        }
                        _state.update { it.copy(phase = CapturePhase.Ready, elapsedMs = 0, error = message) }
                    }
                }
            }

            else -> Unit // Start·Pause·Resume 은 따로 할 일이 없다(Start 전에 이미 Recording 상태)
        }
    }

    override fun onCleared() {
        recording?.close()
    }

    private companion object {
        const val TAG = "CaptureViewModel"

        /** cacheDir/captures/LOGUS_20260924_143012.mp4 (앱 전용 임시 폴더라 다른 앱이 볼 수 없다) */
        fun newVideoFile(context: Context): File {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            return File(dir, "LOGUS_$name.mp4")
        }
    }
}
