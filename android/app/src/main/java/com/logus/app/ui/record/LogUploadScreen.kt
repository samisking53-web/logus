package com.logus.app.ui.record

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.record.LogUploadUiState
import com.logus.app.record.MAX_THEMES
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PhotoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// S05 기록 올리기(위치 확인 우선)
/**
 * S04 에서 촬영을 마치면 바로 뜨는 화면. 하단 탭은 숨긴다. 위에서부터:
 * - 뒤로 가기 + "기록 올리기"
 * - 찍은 영상의 첫 장면(16:9)과 "▶ 0:08"(영상 길이)
 * - 흰 카드: "위치 확인 · 지도 열기"(지도 팝업은 다음 작업, 지금은 눌러도 아무 일도 없다) →
 *   "영상·위치 함께 저장하면 +10P" → □ 위치 없이 저장(켜면 위치 정보 없이 올린다)
 * - 테마(직접 입력): 인스타그램 해시태그처럼 # 이 앞에 붙는다. 띄어쓰기·쉼표·완료로 태그 하나가 되고 최대 3개.
 * - 맨 아래 "여정에 올리기"와 작은 안내 문구
 * 뒤로 가기(버튼·폰)는 S04 로 돌아간다(찍은 영상은 지우고 다시 찍는다). 올리는 중에는 뒤로 가지 않는다.
 */
@Composable
fun LogUploadScreen(
    video: File,
    durationMs: Long,
    state: LogUploadUiState,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onWithoutLocationChange: (Boolean) -> Unit,
    onThemeInput: (String) -> Unit,
    onThemeDone: () -> Unit,
    onRemoveTheme: (String) -> Unit,
    onUpload: () -> Unit,
) {
    BackHandler { if (!state.uploading) onBack() }

    // 영상 첫 장면(앱 안에서 작은 그림으로 보여 준다)
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, video) {
        value = withContext(Dispatchers.IO) { firstFrame(video)?.asImageBitmap() }
    }

    LogUploadContent(
        thumbnail = thumbnail,
        durationMs = durationMs,
        state = state,
        onBack = { if (!state.uploading) onBack() },
        onOpenMap = onOpenMap,
        onWithoutLocationChange = onWithoutLocationChange,
        onThemeInput = onThemeInput,
        onThemeDone = onThemeDone,
        onRemoveTheme = onRemoveTheme,
        onUpload = onUpload,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogUploadContent(
    thumbnail: ImageBitmap?,
    durationMs: Long,
    state: LogUploadUiState,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onWithoutLocationChange: (Boolean) -> Unit,
    onThemeInput: (String) -> Unit,
    onThemeDone: () -> Unit,
    onRemoveTheme: (String) -> Unit,
    onUpload: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // 위쪽(화면이 작거나 키보드가 올라오면 이 부분만 스크롤된다). 올리기 버튼은 늘 맨 아래
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            LogoHeader()
            RecordTopBar(title = "기록 올리기", onBack = onBack)
            Spacer(Modifier.height(12.dp))

            // 찍은 영상(첫 장면) + 길이
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BackgroundDark)
                    .semantics { contentDescription = "찍은 영상 ${durationMs / 1000}초" },
            ) {
                thumbnail?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Row(
                    Modifier
                        .padding(12.dp)
                        .background(PhotoFrame, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = BackgroundDark, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(durationLabel(durationMs), color = BackgroundDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))

            // 위치 카드
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(LogUsColors.card, RoundedCornerShape(20.dp))
                    .border(1.dp, LogUsColors.line, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                // 위치 확인 · 지도 열기(지도 팝업은 다음 작업). "위치 없이 저장"을 켜면 누를 수 없다
                Surface(
                    onClick = onOpenMap,
                    enabled = !state.withoutLocation && !state.uploading,
                    shape = RoundedCornerShape(16.dp),
                    color = LogUsColors.profileBox,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .alpha(if (state.withoutLocation) 0.45f else 1f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = LogUsColors.primaryStrong)
                        Text(
                            "위치 확인 · 지도 열기",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = LogUsColors.primaryStrong,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "영상·위치 함께 저장하면 +10P",
                    color = LogUsColors.primaryStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                // □ 위치 없이 저장(줄 전체를 눌러도 켜지고 꺼진다)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.withoutLocation,
                            enabled = !state.uploading,
                            role = Role.Checkbox,
                            onValueChange = onWithoutLocationChange,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.withoutLocation,
                        onCheckedChange = null, // 줄 전체가 눌림을 처리한다
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column {
                        Text("위치 없이 저장", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("장소를 남기고 싶지 않을 때 켜세요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // 테마(직접 입력) + 넣은 개수
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "테마 (직접 입력)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.themes.size} / $MAX_THEMES",
                    color = LogUsColors.primaryStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(LogUsColors.card, RoundedCornerShape(16.dp))
                    .border(1.dp, LogUsColors.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 넣은 태그: "#카페 ✕"
                state.themes.forEach { tag ->
                    Row(
                        Modifier
                            .height(32.dp)
                            .background(LogUsColors.iconCircle, RoundedCornerShape(50))
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#$tag", color = LogUsColors.primaryStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .size(28.dp)
                                .clickable(enabled = !state.uploading, role = Role.Button) { onRemoveTheme(tag) }
                                .semantics { contentDescription = "$tag 태그 지우기" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                // 다음 태그 입력: 앞에 # 이 붙어 있다. 3개를 다 넣으면 숨긴다
                if (state.themes.size < MAX_THEMES) {
                    Row(
                        Modifier.height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#", color = LogUsColors.primaryStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(2.dp))
                        BasicTextField(
                            value = state.themeInput,
                            onValueChange = onThemeInput,
                            enabled = !state.uploading,
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onThemeDone() }),
                            modifier = Modifier
                                .widthIn(min = 120.dp)
                                .semantics { contentDescription = "테마 태그 입력, 최대 ${MAX_THEMES}개" },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.themeInput.isEmpty()) {
                                        Text(
                                            if (state.themes.isEmpty()) "카페  #에그타르트  (최대 ${MAX_THEMES}개)" else "태그 더 넣기",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 15.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                state.themeHint ?: "띄어쓰기나 완료를 누르면 태그가 돼요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            state.error?.let {
                Spacer(Modifier.height(14.dp))
                ErrorMessage(it)
            }
            Spacer(Modifier.height(24.dp))
        }

        // 여정에 올리기
        Button(
            onClick = onUpload,
            enabled = !state.uploading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (state.uploading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("올리는 중…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("여정에 올리기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.withoutLocation) "위치 없이 여정에 올려요 (코인은 쌓이지 않아요)" else "지도에 핀이 찍히고 코인 10개가 쌓여요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 8000 → "0:08" */
private fun durationLabel(ms: Long): String {
    val s = (ms + 500) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** 영상의 첫 장면을 그림으로 꺼낸다(실패하면 null). 안드로이드 기본 기능이라 라이브러리가 필요 없다 */
private fun firstFrame(video: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(video.absolutePath)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun LogUploadContentPreview() {
    LogUsTheme {
        LogUploadContent(
            thumbnail = null,
            durationMs = 8_000,
            state = LogUploadUiState(themes = listOf("카페"), themeInput = "에그타"),
            onBack = {},
            onOpenMap = {},
            onWithoutLocationChange = {},
            onThemeInput = {},
            onThemeDone = {},
            onRemoveTheme = {},
            onUpload = {},
        )
    }
}
