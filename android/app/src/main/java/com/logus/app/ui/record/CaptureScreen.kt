package com.logus.app.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.logus.app.record.CapturePhase
import com.logus.app.record.CaptureUiState
import com.logus.app.record.MAX_VIDEO_MILLIS
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.Success
import kotlinx.coroutines.delay
import java.time.LocalTime

// S04 앱 내 카메라
/**
 * 여행 중 홈(S01-A)의 "지금 기록하기"(또는 여정 시작일 자동)로 여는 앱 안 카메라. 하단 탭은 숨긴다.
 * - 맨 위: 뒤로 가기 + 여정 이름 / 그 아래: "● 여정 진행 중 · 14:30"(지금 시각, 1분마다 바뀐다)
 * - 카메라 미리보기: 화면 위쪽 절반 안에 들어가도록 가로로 긴 16:9 상자. 앱은 세로로 고정되어 있고(AndroidManifest),
 *   상자에 보이는 그대로(16:9) 영상이 저장된다(CameraX 가 미리보기 영역대로 잘라 녹화한다).
 * - 영상은 10초까지. "00:04 / 00:10"과 진행 막대로 보여 주고, 10초가 되면 저절로 멈춘다.
 * - 아래: 빨간 동그라미가 있는 "촬영" 버튼(녹화 중에는 동그라미가 깜빡이고 "촬영 멈추기"),
 *   맨 아래 작게 "공통 알림 · 2시간마다"(여정을 만든 사람이 정한 간격).
 * - 촬영을 마치면 영상은 폰 안 임시 폴더에 남는다. 기록 올리기(S05)는 다음 작업에서 연결한다.
 * - 처음 열 때 카메라·마이크 권한을 묻는다. 마이크를 거절하면 소리 없이 찍는다.
 */
@Composable
fun CaptureScreen(
    journeyName: String,
    state: CaptureUiState,
    onBack: () -> Unit,
    onStart: (controller: LifecycleCameraController, withAudio: Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    var hasAudio by remember { mutableStateOf(context.granted(Manifest.permission.RECORD_AUDIO)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasAudio = result[Manifest.permission.RECORD_AUDIO] ?: hasAudio
    }
    LaunchedEffect(Unit) {
        if (!hasCamera || !hasAudio) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // 카메라 컨트롤러: 영상 녹화만 켜고, 화질은 FHD(안 되면 그보다 낮은 화질)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            videoCaptureQualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
            )
        }
    }
    LaunchedEffect(hasCamera) {
        if (hasCamera) controller.bindToLifecycle(lifecycleOwner)
    }
    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    BackHandler(onBack = onBack)

    // 지금 시각(HH:mm): 분이 바뀔 때마다 새로 그린다
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    CaptureContent(
        journeyName = journeyName,
        clock = "%02d:%02d".format(now.hour, now.minute),
        state = state,
        hasCamera = hasCamera,
        onBack = onBack,
        onRequestPermission = {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        },
        onRecordClick = {
            if (state.phase == CapturePhase.Recording) onStop() else onStart(controller, hasAudio)
        },
        cameraPreview = {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // 상자를 가득 채우고 넘치는 부분은 잘라 낸다. 녹화도 이 영역(16:9)대로 잘린다
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** 화면 그리기만 하는 부분(미리보기에서도 쓰려고 나눴다). cameraPreview 자리에 실제 카메라가 들어간다 */
@Composable
private fun CaptureContent(
    journeyName: String,
    clock: String,
    state: CaptureUiState,
    hasCamera: Boolean,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onRecordClick: () -> Unit,
    cameraPreview: @Composable () -> Unit,
) {
    val recording = state.phase == CapturePhase.Recording
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // 위쪽(화면이 작으면 이 부분만 스크롤된다). 촬영 버튼·알림 문구는 늘 맨 아래에 있다
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            LogoHeader()

            // 뒤로 가기 + 여정 이름
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = LogUsColors.card, modifier = Modifier.size(48.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(LogUsColors.card, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        journeyName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 여정 진행 중 · 지금 시각
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(LogUsColors.profileBox, RoundedCornerShape(16.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Success, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "여정 진행 중 · $clock",
                    color = LogUsColors.primaryStrong,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 카메라 미리보기(16:9, 위쪽 절반 안)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BackgroundDark),
            ) {
                if (hasCamera) {
                    cameraPreview()
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "영상을 찍으려면 카메라 권한이 필요해요",
                            color = PhotoFrame,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRequestPermission) {
                            Text("권한 허용하기", color = PhotoFrame, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CornerMarks(Modifier.fillMaxSize())
                Text(
                    "16:9 · 세로 고정",
                    color = PhotoFrame,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 40.dp, bottom = 10.dp) // 오른쪽 아래 꺾쇠와 겹치지 않게
                        .background(BackgroundDark.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // 10초 안내 + 시간
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "영상 · 10초까지 담겨요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${mmss(state.elapsedMs)} / ${mmss(MAX_VIDEO_MILLIS)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.elapsedMs.toFloat() / MAX_VIDEO_MILLIS).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LogUsColors.border,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            // 촬영 완료 / 오류 안내
            if (state.phase == CapturePhase.Done) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "촬영 완료 · ${state.elapsedMs / 1000}초 영상을 찍었어요",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            state.error?.let {
                Spacer(Modifier.height(14.dp))
                ErrorMessage(it)
            }
            Spacer(Modifier.height(24.dp))
        }

        // 촬영 버튼: 빨간 동그라미 + 글자. 녹화 중에는 동그라미가 깜빡인다
        Button(
            onClick = onRecordClick,
            enabled = hasCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            RecordDot(blinking = recording)
            Spacer(Modifier.width(10.dp))
            Text(
                when (state.phase) {
                    CapturePhase.Ready -> "촬영"
                    CapturePhase.Recording -> "촬영 멈추기"
                    CapturePhase.Done -> "다시 촬영"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))

        // 맨 아래: 공통 알림 간격
        Text(
            when {
                !state.notifyLoaded -> " "
                state.notifyIntervalHours != null -> "공통 알림 · ${state.notifyIntervalHours}시간마다"
                else -> "공통 알림 · 받지 않음"
            },
            color = LogUsColors.primaryStrong,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 녹화 표시 빨간 동그라미(흰 테두리). blinking 이면 깜빡인다 */
@Composable
private fun RecordDot(blinking: Boolean) {
    val alpha = if (blinking) {
        val transition = rememberInfiniteTransition(label = "rec")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "recAlpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(18.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .border(2.dp, PhotoFrame, CircleShape)
            .clearAndSetSemantics { contentDescription = if (blinking) "녹화 중" else "" },
    )
}

/** 미리보기 네 귀퉁이의 흰 꺾쇠 표시 */
@Composable
private fun CornerMarks(modifier: Modifier = Modifier) {
    Canvas(modifier.padding(12.dp)) {
        val len = 22.dp.toPx()
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(a: Offset, b: Offset) = drawLine(PhotoFrame, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
        line(Offset(0f, 0f), Offset(len, 0f)); line(Offset(0f, 0f), Offset(0f, len))
        line(Offset(w, 0f), Offset(w - len, 0f)); line(Offset(w, 0f), Offset(w, len))
        line(Offset(0f, h), Offset(len, h)); line(Offset(0f, h), Offset(0f, h - len))
        line(Offset(w, h), Offset(w - len, h)); line(Offset(w, h), Offset(w, h - len))
    }
}

/** 4500 → "00:04" */
private fun mmss(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun CaptureContentPreview() {
    LogUsTheme {
        CaptureContent(
            journeyName = "우리의 포르투",
            clock = "14:30",
            state = CaptureUiState(elapsedMs = 4_000, phase = CapturePhase.Recording, notifyIntervalHours = 2, notifyLoaded = true),
            hasCamera = true,
            onBack = {},
            onRequestPermission = {},
            onRecordClick = {},
            cameraPreview = {},
        )
    }
}
