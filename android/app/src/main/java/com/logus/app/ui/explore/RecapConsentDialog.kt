package com.logus.app.ui.explore

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.explore.ConsentUiState
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary

// DAY RECAP 업로드 동의 팝업(스토리보드에 없음, 2026-11-08 요청)
/**
 * 구성원이 DAY RECAP 을 탐색에 올리자고 하면, 동의 요청 알림 배너를 누르거나 홈에 들어올 때 뜬다.
 * "탐색 공개 동의" · X → 여정 이름 · "9.24 · 포르투" → "○○ 님이 이날의 DAY RECAP을 탐색에 전체 공개로 올리려고 해요"
 * → 주의 상자(내 영상이 모두에게 공개됨, 장소는 20코인 쓴 사람만, 모두 동의해야 올라감) → "동의 2 / 3명"
 * → [동의 안 함] [동의하기]. 한 명이라도 동의하지 않으면 이번 공개는 취소된다. X 는 나중에 답하기(다음에 홈에서 다시 물음).
 */
@Composable
fun RecapConsentDialog(state: ConsentUiState, onAnswer: (Boolean) -> Unit, onLater: () -> Unit) {
    val request = state.request ?: return
    Dialog(onDismissRequest = { if (!state.busy) onLater() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "탐색 공개 동의",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 40.dp).semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(request.journeyName, color = LogUsColors.primaryStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOf(shortDate(request.date), request.city).filter { it.isNotEmpty() }.joinToString(" · ") + " · DAY RECAP",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${request.requesterName} 님이 이날의 DAY RECAP을 탐색에 전체 공개로 올리려고 해요.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "동의하면 내 영상이 LOG US 사용자 모두에게 공개돼요. 장소는 20코인을 쓴 사람만 볼 수 있어요. " +
                        "구성원 모두 동의해야 올라가고, 한 명이라도 동의하지 않으면 취소돼요.",
                    color = LogUsColors.amberText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.warningBox, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "동의 ${request.approvedCount} / ${request.memberCount}명",
                    color = LogUsColors.primaryStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                state.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onAnswer(false) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(0.45f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, LogUsColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = LogUsColors.card, contentColor = LogUsColors.primaryStrong),
                    ) {
                        Text("동의 안 함", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onAnswer(true) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(0.55f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("동의하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Surface(
                onClick = onLater,
                enabled = !state.busy,
                shape = CircleShape,
                color = LogUsColors.iconCircle,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "나중에 답하기", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
