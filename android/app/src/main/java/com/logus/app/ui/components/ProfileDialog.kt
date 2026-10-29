package com.logus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.journey.Member
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

// 프로필 팝업 (스토리보드 v5 16·17쪽 "프로필 사진 클릭하면 팝업으로 프로필 띄우기", N01·S15 기록 카드 공통)
/**
 * 기록 카드 왼쪽 위 작은 프로필을 누르면 뜨는 팝업: X → 큰 동그란 프로필 사진(사진이 없으면 지구본) → 닉네임.
 * 뒤 화면은 그대로 두고 어둡게 덮는다. 바깥을 누르거나 X·뒤로 가기로 닫는다.
 */
@Composable
fun ProfileDialog(member: Member, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(photo = member.photoUrl, size = 112.dp, ringWidth = 4.dp, ringColor = LogUsColors.border)
                Spacer(Modifier.height(16.dp))
                Text(
                    member.nickname,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileDialogPreview() {
    LogUsTheme { ProfileDialog(member = Member(uid = "a", nickname = "김성연"), onDismiss = {}) }
}
