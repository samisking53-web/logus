package com.logus.app.ui.journeydetail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.R
import com.logus.app.journeydetail.RecapExportState
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.Success

// S17 DAY RECAP 영상 추출 (2026-10-28 팀 요청: "영상 추출" → [영상 다운로드] [탐색에 자동 올리기])
/**
 * "영상 추출"을 누르면 아래에서 올라오는 창.
 * - 영상 다운로드: 폰에서 릴스 크기 영상을 만들어 갤러리 "LOG US" 앨범에 저장한다(onDownload).
 * - 탐색에 자동 올리기: 탐색 화면이 아직 없어서 흐리게 둔다(눌리지 않음). 탐색을 만들 때 연결한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapExportSheet(summary: String, onDismiss: () -> Unit, onDownload: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LogUsColors.card,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                "DAY RECAP 영상 추출",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            ExportOption(
                icon = R.drawable.ic_download,
                filled = true,
                title = "영상 다운로드",
                body = "릴스 크기(세로) 영상으로 만들어 갤러리 \"LOG US\" 앨범에 저장해요",
                onClick = onDownload,
            )
            Spacer(Modifier.height(10.dp))
            ExportOption(
                icon = R.drawable.ic_download,
                filled = false,
                title = "탐색에 자동 올리기",
                badge = "준비 중",
                body = "탐색 화면이 생기면 연결돼요",
                onClick = null,
                upload = true,
            )
        }
    }
}

/** 창 안의 선택지 한 줄: 동그라미 아이콘 + 제목·설명. onClick 이 null 이면 흐리게(눌리지 않음) */
@Composable
private fun ExportOption(
    icon: Int,
    filled: Boolean,
    title: String,
    body: String,
    onClick: (() -> Unit)?,
    badge: String? = null,
    upload: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, LogUsColors.line, shape)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (filled) Primary else LogUsColors.iconCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (filled) OnPrimary else LogUsColors.primaryStrong,
                    // 올리기는 다운로드 아이콘을 뒤집어 쓴다(새 그림 파일 없이)
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { scaleY = if (upload) -1f else 1f },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            badge,
                            color = LogUsColors.primaryStrong,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(LogUsColors.profileBox, RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = LogUsColors.card) { content() }
    } else {
        Box(
            Modifier
                .alpha(0.55f)
                .semantics { stateDescription = "준비 중, 아직 쓸 수 없어요" },
        ) { content() }
    }
}

/**
 * 영상 만들기 팝업들: 만드는 중(진행률·취소) → 다 만들면 "갤러리에 저장했어요"([닫기] [공유하기]) / 실패하면 안내.
 * 공유하기는 안드로이드 공유 창(인스타그램·카카오톡 등)을 띄운다.
 */
@Composable
fun RecapExportDialogs(state: RecapExportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        RecapExportState.Idle -> Unit
        is RecapExportState.Working -> ExportDialog(onDismiss = {}) {
            CircleIcon { Icon(painterResource(R.drawable.ic_download), null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(30.dp)) }
            DialogTitle("영상을 만들고 있어요")
            DialogBody("폰에서 직접 만들어서 시간이 조금 걸려요.\n화면을 켜 둔 채로 기다려 주세요.")
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                color = Primary,
                trackColor = LogUsColors.border,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${(state.progress * 100).toInt()}%",
                color = LogUsColors.primaryStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("취소", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold)
            }
        }
        is RecapExportState.Done -> ExportDialog(onDismiss = onDismiss) {
            CircleIcon { Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(30.dp)) }
            DialogTitle("갤러리에 저장했어요")
            DialogBody("앨범 \"LOG US\"에서 볼 수 있어요.")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("닫기", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, state.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "DAY RECAP 공유"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("공유하기", fontWeight = FontWeight.Bold) }
            }
        }
        is RecapExportState.Failed -> ExportDialog(onDismiss = onDismiss) {
            DialogTitle("영상을 만들지 못했어요")
            DialogBody(state.message)
            // 원인 찾기용 오류 이름(이 화면을 캡처해 보내면 고칠 수 있다)
            state.detail?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("확인", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun CircleIcon(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .background(LogUsColors.iconCircle, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DialogBody(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
}
