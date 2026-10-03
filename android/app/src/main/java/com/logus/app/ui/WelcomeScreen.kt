package com.logus.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.theme.GoogleButton
import com.logus.app.ui.theme.GoogleLabel
import com.logus.app.ui.theme.GoogleOutline
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/**
 * 첫 화면 (앱을 처음 연 사람 / 로그아웃한 사람)
 * LOG EARTH 로고 → 앱 소개 이미지 자리 → "Google 계정으로 계속하기".
 * 처음 온 사람은 로그인 뒤 약관 동의 → 프로필 설정으로, 이미 가입한 사람은 바로 홈으로 간다.
 */
@Composable
fun WelcomeScreen(
    busy: Boolean,
    error: String?,
    onGoogleClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 로고
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = LogUsColors.primaryStrong,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "LOG EARTH",
                color = LogUsColors.primaryStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(16.dp))

        // 앱 소개 이미지 자리: 이미지가 정해지면 이 상자 안을 Image(painterResource(...)) 로 바꾼다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, LogUsColors.border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text("앱 소개 이미지가 들어갈 자리예요", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))

        // 구글 로그인 버튼: 구글 브랜드 가이드(흰 배경, 회색 테두리, 4색 G 로고) — 팔레트 예외
        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoogleOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = GoogleButton,
                contentColor = GoogleLabel,
                disabledContainerColor = GoogleButton,
                disabledContentColor = GoogleLabel,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = GoogleLabel, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("로그인하는 중…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Image(painterResource(R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Google 계정으로 계속하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorMessage(error)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "계속하면 이용약관과 개인정보 처리방침에\n동의하는 절차로 넘어가요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    LogUsTheme { WelcomeScreen(busy = false, error = null, onGoogleClick = {}) }
}
