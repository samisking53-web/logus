package com.logus.app.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.ads.AdReward
import com.logus.app.ads.AdRewardUiState
import com.logus.app.ads.AdRewardViewModel
import com.logus.app.ui.theme.Amber
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.Success
import java.text.NumberFormat
import java.util.Locale

// 광고 보고 코인 받기 팝업(스토리보드에 없음, 2026-11-11 요청·팀 그림: S01·S01-A 보유 코인 상자를 누르면 뜬다)
/**
 * ① 묻기: 큰 동전(▶ 표시) → "광고 보고 코인 받을까요?" → "광고 1편을 끝까지 보면 2코인을 드려요"
 *    → 오늘 쓴 횟수 점 5개 + "오늘 N / 5회 사용 · 하루 최대 10코인" → [취소] [▶ 광고 보기]
 * ② 결과(팀 그림): 큰 동전(초록 체크) → "+2 코인을 받았어요" → "오늘 N / 5회 사용" → 연한 주황 상자 "102 코인"
 *    → [한 번 더 보기] [확인]
 * 오늘 5번을 다 쓰면 [광고 보기]·[한 번 더 보기]가 꺼지고 "내일 오전 9시에 다시 받을 수 있어요".
 */
@Composable
fun AdRewardDialog(state: AdRewardUiState, onWatch: () -> Unit, onClose: () -> Unit) {
    if (state is AdRewardUiState.Closed) return
    val busy = state is AdRewardUiState.Ask && state.busy
    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            when (state) {
                is AdRewardUiState.Ask -> AskContent(state, onWatch, onClose)
                is AdRewardUiState.Done -> DoneContent(state, onWatch, onClose)
                AdRewardUiState.Closed -> Unit
            }
            IconButton(
                onClick = onClose,
                enabled = !busy,
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

@Composable
private fun AskContent(state: AdRewardUiState.Ask, onWatch: () -> Unit, onClose: () -> Unit) {
    val used = state.used
    val limitReached = used != null && used >= AdReward.DAILY_LIMIT && state.retryViewId == null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigCoin(badge = Icons.Filled.PlayArrow, badgeColor = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            "광고 보고 코인 받을까요?",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "광고 1편을 끝까지 보면 ${AdReward.COINS_PER_VIEW}코인을 드려요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        // 오늘 쓴 횟수: 점 5개(쓴 만큼 동전색) + 글자(색만으로 알리지 않게)
        Column(
            Modifier
                .fillMaxWidth()
                .background(LogUsColors.iconCircle, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (used == null) {
                CircularProgressIndicator(Modifier.size(20.dp), color = LogUsColors.primaryStrong, strokeWidth = 2.dp)
            } else {
                UsageDots(used)
                Spacer(Modifier.height(8.dp))
                Text(
                    "오늘 $used / ${AdReward.DAILY_LIMIT}회 사용 · 하루 최대 ${AdReward.DAILY_MAX_COINS}코인",
                    color = LogUsColors.primaryStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        val notice = state.message ?: if (limitReached) AdRewardViewModel.LIMIT_MESSAGE else null
        if (notice != null) {
            Spacer(Modifier.height(10.dp))
            Text(notice, color = LogUsColors.warning, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onClose,
                enabled = !state.busy,
                modifier = Modifier.weight(0.4f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LogUsColors.border),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = LogUsColors.card, contentColor = LogUsColors.primaryStrong),
            ) {
                Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onWatch,
                enabled = used != null && !state.busy && !limitReached,
                modifier = Modifier.weight(0.6f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
            ) {
                when {
                    state.busy -> CircularProgressIndicator(Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
                    state.retryViewId != null -> Text("코인 다시 받기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    limitReached -> Text("오늘은 다 받았어요", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    else -> {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("광고 보기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneContent(state: AdRewardUiState.Done, onWatch: () -> Unit, onClose: () -> Unit) {
    val canWatchMore = state.used < AdReward.DAILY_LIMIT
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigCoin(badge = Icons.Filled.Check, badgeColor = Success)
        Spacer(Modifier.height(16.dp))
        Text(
            "+${state.granted} 코인을 받았어요",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "오늘 ${state.used} / ${AdReward.DAILY_LIMIT}회 사용",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        // 받은 뒤 보유 코인(연한 주황 상자 + 주황 글자, 대비 5.5:1)
        val total = NumberFormat.getNumberInstance(Locale.KOREA).format(state.coins)
        Row(
            Modifier
                .background(LogUsColors.warningBox, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .semantics { contentDescription = "보유 코인 ${total}개" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinIcon(Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(total, color = LogUsColors.amberText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("코인", color = LogUsColors.amberText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!canWatchMore) {
            Spacer(Modifier.height(10.dp))
            Text(AdRewardViewModel.LIMIT_MESSAGE, color = LogUsColors.warning, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onWatch,
                enabled = canWatchMore,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LogUsColors.border),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = LogUsColors.card, contentColor = LogUsColors.primaryStrong),
            ) {
                Text(if (canWatchMore) "한 번 더 보기" else "오늘은 다 받았어요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
            ) {
                Text("확인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 큰 동전(64dp) + 오른쪽 아래 작은 표시(▶ 보라 / ✓ 초록, 흰 테두리) */
@Composable
private fun BigCoin(badge: ImageVector, badgeColor: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(72.dp)) {
        CoinIcon(Modifier.size(68.dp).align(Alignment.Center))
        Box(
            Modifier
                .size(28.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 2.dp)
                .background(badgeColor, CircleShape)
                .border(2.dp, LogUsColors.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(badge, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** 오늘 쓴 횟수 점 5개: 쓴 만큼 동전색(amber) 채움, 남은 것은 테두리만 */
@Composable
private fun UsageDots(used: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(AdReward.DAILY_LIMIT) { i ->
            val filled = i < used
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (filled) Amber else LogUsColors.card, CircleShape)
                    .border(1.5.dp, if (filled) Amber else LogUsColors.border, CircleShape),
            )
        }
    }
}

/** 화면(Compose)의 Context 에서 Activity 를 찾는다(광고는 Activity 위에 전체 화면으로 띄운다) */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
