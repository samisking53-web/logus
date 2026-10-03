package com.logus.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.auth.Profile
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * S01 홈 (임시)
 * 지금은 로그인·회원가입 연동 확인용으로 프로필 상자와 로그아웃만 있다.
 * 스토리보드 S01·S01-A 화면(진행 중 여정, 기록 시작하기, 하단 탭)은 다음 작업에서 만든다.
 */
@Composable
fun HomeScreen(profile: Profile, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "LOG EARTH",
            color = LogUsColors.primaryStrong,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "어떤 순간을 남길까요?",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        // 프로필 상자: 동그란 사진, 닉네임, 보유 코인
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LogUsColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(photo = profile.photoUrl, size = 88.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        profile.nickname,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "보유 ${NumberFormat.getNumberInstance(Locale.KOREA).format(profile.coins)} 코인",
                        color = LogUsColors.amberText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, LogUsColors.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LogUsColors.primaryStrong),
        ) { Text("로그아웃", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LogUsTheme { HomeScreen(Profile(nickname = "성연", photoUrl = null, coins = 100), onSignOut = {}) }
}
