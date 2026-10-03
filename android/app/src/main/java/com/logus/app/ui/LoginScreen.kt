package com.logus.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.ui.theme.GoogleButton
import com.logus.app.ui.theme.GoogleLabel
import com.logus.app.ui.theme.GoogleOutline
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/** 로그인 화면: 구글 계정으로 시작한다. 처음이면 로그인 뒤 회원가입 화면으로 이어진다. */
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onGoogleClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LOG EARTH", color = LogUsColors.primaryStrong, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "함께한 여정을 함께 기록해요",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(48.dp))

        // 구글 로그인 버튼: 구글 브랜드 가이드(흰 배경, 회색 테두리) — 팔레트 예외
        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
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
                Spacer(Modifier.size(12.dp))
                Text("로그인하는 중…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Google로 시작하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            ErrorMessage(error)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "처음이면 로그인 후 닉네임만 정하면 가입이 끝나요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

/**
 * 오류 문구. 오류 빨강은 다크 바탕에서 글자 대비가 부족해(3.4) 테두리에만 쓰고 글자는 본문색으로 쓴다.
 */
@Composable
fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
            .padding(12.dp)
            // 화면 읽기 프로그램이 오류 문구를 바로 읽어 준다
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LogUsTheme { LoginScreen(busy = false, error = null, onGoogleClick = {}) }
}
