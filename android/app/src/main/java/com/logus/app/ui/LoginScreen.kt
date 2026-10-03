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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.auth.AuthUiState
import com.logus.app.ui.theme.GoogleButton
import com.logus.app.ui.theme.GoogleLabel
import com.logus.app.ui.theme.GoogleOutline
import com.logus.app.ui.theme.KakaoLabel
import com.logus.app.ui.theme.KakaoYellow
import com.logus.app.ui.theme.LogUsTheme

/**
 * 로그인 화면 (임시)
 * 회원가입 화면(스토리보드 3쪽)을 만들기 전까지, 카카오·구글 로그인 연동을 확인하는 용도다.
 * 로그인하면 이름과 로그아웃 버튼을 보여 준다.
 */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onKakaoClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "LOG EARTH",
            color = MaterialTheme.colorScheme.secondary, // 강조 글자(primary-strong)
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(48.dp))

        when (state) {
            is AuthUiState.SignedIn -> {
                Text(
                    text = "${state.displayName}님, 환영해요!",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSignOutClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("로그아웃", fontSize = 16.sp) }
            }

            AuthUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            AuthUiState.SignedOut, is AuthUiState.Error -> {
                // 카카오 로그인 버튼: 카카오 디자인 가이드(노란 배경 #FEE500, 검정 85% 글자)
                Button(
                    onClick = onKakaoClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KakaoYellow, contentColor = KakaoLabel),
                ) { Text("카카오 로그인", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

                Spacer(Modifier.height(12.dp))

                // 구글 로그인 버튼: 구글 브랜드 가이드(흰 배경, 회색 테두리)
                OutlinedButton(
                    onClick = onGoogleClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoogleOutline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = GoogleButton, contentColor = GoogleLabel),
                ) { Text("Google로 로그인", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

                if (state is AuthUiState.Error) {
                    Spacer(Modifier.height(16.dp))
                    // 오류 빨강(#C0392B)은 다크 바탕에서 글자 대비가 3.4라 부족하다.
                    // 그래서 빨강은 테두리에만 쓰고 글자는 본문색으로 쓴다.
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            // 화면 읽기 프로그램이 오류 문구를 바로 읽어 준다
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LogUsTheme {
        LoginScreen(AuthUiState.SignedOut, {}, {}, {})
    }
}
