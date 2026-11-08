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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.explore.PLACE_VIEW_COINS
import com.logus.app.explore.UnlockDialogState
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary

// 위치 정보 열람 팝업(스토리보드 v5 22쪽 왼쪽: S08·S09 "장소 보기"를 누르면 뜬다)
/**
 * "위치 정보 열람" → "현재 보유 N 코인"(연보라 상자) → "20코인을 차감하겠습니까?" → [취소] [20코인 사용]
 * "20코인 사용" → 서버(unlockRecapPlaces)가 코인을 빼고 → 그 여정의 공개 여정 상세(장소·추억 지도가 보임).
 * 내가 올린 영상(내 영상)만 팝업 없이 무료로 열리고, 같은 여정 구성원의 영상도 20코인(2026-11-08 팀 요청).
 * 같은 영상은 한 번만 코인을 쓴다(다시 누르면 팝업 없이 바로 열림). 코인이 모자라면 버튼이 꺼지고 안내를 보여 준다.
 */
@Composable
fun PlaceUnlockDialog(state: UnlockDialogState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val enough = state.coins != null && state.coins >= PLACE_VIEW_COINS
    Dialog(onDismissRequest = { if (!state.busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "위치 정보 열람",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(LogUsColors.iconCircle, RoundedCornerShape(50))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.coins == null && state.error == null) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = LogUsColors.primaryStrong, strokeWidth = 2.dp)
                } else {
                    Text(
                        "현재 보유  ${state.coins ?: "-"} 코인",
                        color = LogUsColors.primaryStrong,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${PLACE_VIEW_COINS}코인을 차감하겠습니까?",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "구성원들이 기록한 장소와 추억 지도를 볼 수 있어요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            val notice = state.error ?: if (state.coins != null && !enough) "코인이 부족해요. 기록에 위치를 남기면 코인이 쌓여요." else null
            if (notice != null) {
                Spacer(Modifier.height(10.dp))
                Text(notice, color = LogUsColors.warning, fontSize = 13.sp, textAlign = TextAlign.Center)
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
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("${PLACE_VIEW_COINS}코인 사용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
