package com.logus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

// 앱 시작 화면 (스토리보드 v4 1쪽)
/**
 * 앱을 켤 때마다 잠깐 보이는 화면. 연보라 바탕 가운데에 LOG EARTH.
 * 이 화면이 보이는 동안 로그인·가입 여부를 확인하고, 끝나면 홈(또는 첫 화면)으로 넘어간다.
 * 보이는 시간은 MainActivity 의 SPLASH_MILLIS 로 정한다.
 */
@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "LOG EARTH",
            color = LogUsColors.primaryStrong,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    LogUsTheme { SplashScreen() }
}
