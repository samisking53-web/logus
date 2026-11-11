package com.logus.app.ui.journeydetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.journeydetail.PremiumConfirmState
import com.logus.app.journeydetail.PremiumExport
import com.logus.app.ui.home.CoinIcon
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary

// S17 DAY RECAP 프리미엄 추출 확인 팝업(스토리보드에 없음, 2026-11-11 팀 요청: 50코인으로 워터마크 없이 추출)
/**
 * "영상 추출" 창의 "프리미엄 다운로드"를 처음 누르면 뜬다(이미 낸 날은 뜨지 않고 바로 만든다).
 * X → 큰 동전 → "프리미엄 추출" → "50코인으로 워터마크 없이 저장할까요?"
 * → 연보라 상자(여정 이름 / "9.24 · 포르투 · DAY RECAP") → 연한 주황 상자("현재 보유 120 코인" / "사용하면 70 코인이 남아요")
 * → 안내 "한 번 내면 이날은 다시 받아도 무료예요" → [취소] [50코인 사용]
 * 코인이 모자라면 버튼이 "코인이 부족해요"로 꺼지고 광고로 코인을 받는 방법을 알려 준다.
 * "50코인 사용" → 서버(unlockPremiumExport)가 코인을 빼면 바로 워터마크 없는 영상을 만들기 시작한다.
 */
@Composable
fun PremiumExportDialog(state: PremiumConfirmState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val coins = state.coins
    val enough = coins != null && coins >= PremiumExport.COINS
    Dialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(LogUsColors.warningBox, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { CoinIcon(Modifier.size(44.dp)) }
                Spacer(Modifier.height(14.dp))
                Text(
                    "프리미엄 추출",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${PremiumExport.COINS}코인으로 워터마크 없이 저장할까요?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                // 어느 날 영상인지
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.iconCircle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.journeyName,
                        color = LogUsColors.primaryStrong,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(state.dayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                // 코인 상자: "현재 보유 120 코인" + 아래 "사용하면 70 코인이 남아요"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.warningBox, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (coins == null && state.error == null) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = LogUsColors.amberText, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "현재 보유 ${coins ?: "-"} 코인",
                                color = LogUsColors.amberText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (coins != null && enough) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "사용하면 ${coins - PremiumExport.COINS} 코인이 남아요",
                                color = LogUsColors.amberText,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "한 번 내면 이날은 다시 받아도 무료예요",
                    color = LogUsColors.primaryStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                val notice = state.error ?: if (coins != null && !enough) {
                    "코인이 ${PremiumExport.COINS - coins}개 부족해요. 홈의 보유 코인 상자를 눌러 광고를 보면 코인을 받을 수 있어요."
                } else {
                    null
                }
                if (notice != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(notice, color = LogUsColors.warning, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !state.busy,
                        modifier = Modifier.weight(0.4f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, LogUsColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = LogUsColors.card, contentColor = LogUsColors.primaryStrong),
                    ) {
                        Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = enough && !state.busy,
                        modifier = Modifier.weight(0.6f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
                    ) {
                        when {
                            state.busy -> CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                            coins != null && !enough -> Text("코인이 부족해요", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            else -> Text("${PremiumExport.COINS}코인 사용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                enabled = !state.busy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(40.dp)
                    .background(LogUsColors.iconCircle, CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
