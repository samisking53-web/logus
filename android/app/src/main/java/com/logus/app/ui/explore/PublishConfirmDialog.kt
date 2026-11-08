package com.logus.app.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.explore.PublishAsk
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary

// S17 "탐색에 자동 올리기" 확인 팝업(2026-11-08)
/**
 * "탐색에 올릴까요?" → 여정 이름 · "9.24 · 포르투 · 장면 N개"
 * → 주의 상자(구성원 모두에게 동의 요청 알림이 가고, 모두 동의하면 전체 공개, 장소는 20코인 쓴 사람만)
 * → [취소] [동의 요청 보내기]. 혼자인 여정이면 [탐색에 올리기](바로 공개).
 */
@Composable
fun PublishConfirmDialog(ask: PublishAsk, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val solo = ask.memberCount <= 1
    Dialog(onDismissRequest = { if (!ask.busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(20.dp),
        ) {
            Text(
                "탐색에 올릴까요?",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(ask.journeyName, color = LogUsColors.primaryStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(shortDate(ask.date), ask.city, "장면 ${ask.scenes.size}개").filter { it.isNotEmpty() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (solo) {
                    "이날 DAY RECAP이 탐색에 전체 공개로 올라가요. 장소는 20코인을 쓴 사람만 볼 수 있어요."
                } else {
                    "구성원 ${ask.memberCount - 1}명에게 동의 요청 알림이 가요. 모두 동의하면 이날 DAY RECAP이 탐색에 전체 공개로 올라가고, " +
                        "한 명이라도 동의하지 않으면 취소돼요. 장소는 20코인을 쓴 사람만 볼 수 있어요."
                },
                color = LogUsColors.amberText,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LogUsColors.warningBox, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            ask.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !ask.busy,
                    modifier = Modifier.weight(0.4f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LogUsColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = LogUsColors.card, contentColor = LogUsColors.primaryStrong),
                ) {
                    Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onConfirm,
                    enabled = !ask.busy,
                    modifier = Modifier.weight(0.6f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
                ) {
                    if (ask.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(if (solo) "탐색에 올리기" else "동의 요청 보내기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
