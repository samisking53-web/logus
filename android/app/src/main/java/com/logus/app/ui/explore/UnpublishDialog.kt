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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.logus.app.explore.UnpublishState
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary

// 탐색에서 내리기 팝업(2026-11-08 요청: [내 영상]에서 꾹 누르면 묻고 지운다, 올린 사람만)
/**
 * "탐색에서 내릴까요?" → 여정 이름 · "11.4 · 포르투 · 좋아요 N · 저장 N"
 * → 주의 상자(탐색과 다른 사람의 모아보기에서 사라짐, 좋아요·저장 숫자도 사라짐, 여정 기록은 그대로, 다시 올리려면 동의를 다시 받음)
 * → [취소] [🗑 내리기](빨강 채움·흰 글자, N01 삭제 버튼과 같음)
 */
@Composable
fun UnpublishDialog(state: UnpublishState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val recap = state.recap
    Dialog(onDismissRequest = { if (!state.busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(20.dp),
        ) {
            Text(
                "탐색에서 내릴까요?",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(recap.journeyName, color = LogUsColors.primaryStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(shortDate(recap.date), recap.city, "좋아요 ${recap.likeCount} · 저장 ${recap.saveCount}")
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "내리면 탐색과 다른 사람의 모아보기에서 사라지고 좋아요·저장 숫자도 없어져요. " +
                    "여정의 기록과 영상은 그대로 남아요. 다시 올리려면 구성원 동의를 다시 받아야 해요.",
                color = LogUsColors.amberText,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LogUsColors.warningBox, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
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
                    enabled = !state.busy,
                    modifier = Modifier.weight(0.6f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    // 지우기 버튼은 빨강 채움 + 흰 글자(대비 5.4:1, 라이트·다크 같음)
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = OnPrimary),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("내리기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
