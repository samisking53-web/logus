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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.logus.app.journeydetail.PremiumExport
import com.logus.app.journeydetail.RecapExportState
import com.logus.app.ui.home.CoinIcon
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.Success

// S17 DAY RECAP 영상 추출 (2026-10-28 팀 요청: "영상 추출" → [영상 다운로드] [탐색에 자동 올리기],
// 2026-11-11 팀 요청: 다운로드를 일반(무료·워터마크)과 프리미엄(50코인·워터마크 없음)으로 나눔)
/**
 * "영상 추출"을 누르면 아래에서 올라오는 창.
 * - 영상 다운로드(일반): 폰에서 릴스 크기 영상을 만들어 갤러리 "LOG US" 앨범에 저장한다(onDownload).
 *   오른쪽 아래에 "LOG EARTH" 워터마크가 들어간다. 무료이고, 만드는 동안 진행 팝업 위쪽에 광고(300×250 배너)가 나온다(2026-11-11).
 * - 프리미엄 다운로드: 50코인을 내면 광고·워터마크 없이 만든다(onPremium → 확인 팝업 PremiumExportDialog).
 *   한 번 낸 날(premiumUnlocked = true)은 "이날 무료"로 보이고 팝업 없이 바로 만든다.
 * - 탐색에 자동 올리기(2026-11-08 탐색과 연결): 확인 팝업 → 구성원 모두에게 동의 요청 → 모두 동의하면 탐색(S08)에 전체 공개.
 *   onPublish 가 null 이면 예전처럼 흐리게 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapExportSheet(
    summary: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onPremium: () -> Unit = {},
    /** 이날 프리미엄을 이미 50코인으로 열었는지(null = 아직 모름) */
    premiumUnlocked: Boolean? = null,
    onPublish: (() -> Unit)? = null,
) {
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
                badge = "무료",
                body = "릴스 크기 영상으로 갤러리 \"LOG US\" 앨범에 저장해요. 만드는 동안 광고가 나오고, LOG EARTH 워터마크가 들어가요",
                onClick = onDownload,
            )
            Spacer(Modifier.height(10.dp))
            ExportOption(
                icon = R.drawable.ic_download,
                filled = false,
                coin = true,
                title = "프리미엄 다운로드",
                badge = if (premiumUnlocked == true) "이날 무료" else "${PremiumExport.COINS}코인",
                coinBadge = premiumUnlocked != true,
                body = if (premiumUnlocked == true) {
                    "이날은 ${PremiumExport.COINS}코인을 냈어요. 광고·워터마크 없이 다시 받아도 무료예요"
                } else {
                    "광고·워터마크 없이 저장해요. 한 번 내면 이날 DAY RECAP은 다시 받아도 무료예요"
                },
                onClick = onPremium,
            )
            Spacer(Modifier.height(10.dp))
            ExportOption(
                icon = R.drawable.ic_download,
                filled = false,
                title = "탐색에 자동 올리기",
                badge = if (onPublish == null) "준비 중" else null,
                body = if (onPublish == null) "탐색 화면이 생기면 연결돼요" else "구성원 모두 동의하면 탐색에 전체 공개로 올라가요",
                onClick = onPublish,
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
    /** 아이콘 자리에 동전 그림(프리미엄 다운로드) */
    coin: Boolean = false,
    /** 배지를 코인 색(연한 주황 바탕·amber 글자)으로 */
    coinBadge: Boolean = false,
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
                    .background(
                        when {
                            coin -> LogUsColors.warningBox
                            filled -> Primary
                            else -> LogUsColors.iconCircle
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (coin) {
                    CoinIcon(Modifier.size(26.dp))
                } else Icon(
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
                            color = if (coinBadge) LogUsColors.amberText else LogUsColors.primaryStrong,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(if (coinBadge) LogUsColors.warningBox else LogUsColors.profileBox, RoundedCornerShape(8.dp))
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
 * 일반 다운로드는 만드는 동안 위쪽에 광고(ExportAdBanner, 300×250)를 보여 주고 그 아래에 "지금 DAY RECAP 영상을 추출하고 있어요"
 * ·진행 막대·[취소]를 둔다(2026-11-11 팀 요청). 광고와 영상 만들기는 동시에 돌아서 광고 때문에 늦어지지 않는다.
 * 프리미엄은 광고 없이 예전 모양 그대로.
 */
@Composable
fun RecapExportDialogs(state: RecapExportState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        RecapExportState.Idle -> Unit
        is RecapExportState.Working -> ExportDialog(onDismiss = {}, wide = !state.premium) {
            if (state.premium) {
                CircleIcon { Icon(painterResource(R.drawable.ic_download), null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(30.dp)) }
                DialogTitle("영상을 만들고 있어요")
                DialogBody("프리미엄이라 광고·워터마크가 없어요.\n화면을 켜 둔 채로 기다려 주세요.")
            } else {
                // 일반 다운로드: 위에 광고, 그 아래에 추출 중 안내(광고를 못 불러오면 광고 자리는 접힌다)
                ExportAdBanner()
                Spacer(Modifier.height(18.dp))
                DialogTitle("지금 DAY RECAP 영상을\n추출하고 있어요")
                DialogBody("광고가 나오는 동안에도 영상은 계속 만들어져요.\n화면을 켜 둔 채로 기다려 주세요.")
            }
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
            if (!state.premium) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "광고·워터마크 없이 받으려면 프리미엄 다운로드(${PremiumExport.COINS}코인)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("취소", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold)
            }
        }
        is RecapExportState.Done -> ExportDialog(onDismiss = onDismiss) {
            CircleIcon { Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(30.dp)) }
            DialogTitle("갤러리에 저장했어요")
            DialogBody(
                if (state.premium) "워터마크 없는 프리미엄 영상이에요.\n앨범 \"LOG US\"에서 볼 수 있어요."
                else "앨범 \"LOG US\"에서 볼 수 있어요.",
            )
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

/** wide: 광고(가로 300dp)를 넣을 때는 팝업 양옆 여백을 줄여 광고가 잘리지 않게 한다. 키가 작은 폰은 위아래로 밀어 본다 */
@Composable
private fun ExportDialog(onDismiss: () -> Unit, wide: Boolean = false, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = if (wide) 14.dp else 28.dp, vertical = 16.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(start = if (wide) 14.dp else 22.dp, end = if (wide) 14.dp else 22.dp, top = if (wide) 18.dp else 26.dp, bottom = 20.dp),
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
