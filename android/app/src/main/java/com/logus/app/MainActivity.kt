package com.logus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.auth.AuthUiState
import com.logus.app.auth.AuthViewModel
import com.logus.app.auth.SignupStep
import com.logus.app.ui.HomeScreen
import com.logus.app.ui.WelcomeScreen
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.signup.LegalDocScreen
import com.logus.app.ui.signup.ProfilePhotoScreen
import com.logus.app.ui.signup.ProfileSetupScreen
import com.logus.app.ui.signup.TermsScreen
import com.logus.app.ui.theme.LogUsTheme

/**
 * 앱 시작 지점. 로그인·회원가입 상태에 따라 화면을 고른다.
 * 처음 온 사람: 첫 화면 → (구글 로그인) → 약관 동의 → P01 프로필 설정(↔ P02 사진) → 홈
 * 가입한 사람: (로그인 유지) → 홈
 */
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogUsTheme {
                val state by authViewModel.state.collectAsStateWithLifecycle()

                // 회원가입 중에는 폰의 뒤로 가기 버튼이 앱을 끄지 않고 한 단계 앞 화면으로 간다.
                BackHandler(enabled = state is AuthUiState.Signup) {
                    authViewModel.back(this@MainActivity)
                }

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (val s = state) {
                        AuthUiState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }

                        is AuthUiState.SignedOut -> WelcomeScreen(
                            busy = s.busy,
                            error = s.error,
                            onGoogleClick = { authViewModel.signInWithGoogle(this@MainActivity) },
                        )

                        is AuthUiState.Signup -> SignupFlow(s)

                        is AuthUiState.Ready -> HomeScreen(
                            profile = s.profile,
                            onSignOut = { authViewModel.signOut(this@MainActivity) },
                        )

                        is AuthUiState.Failed -> Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ErrorMessage(s.message)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { authViewModel.signOut(this@MainActivity) }) {
                                Text("다시 로그인하기", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 회원가입 화면들: 약관 동의 → (약관 전문) → P01 프로필 설정 ↔ P02 사진 */
    @Composable
    private fun SignupFlow(s: AuthUiState.Signup) {
        val back = { authViewModel.back(this@MainActivity) }
        when (s.step) {
            SignupStep.TERMS -> TermsScreen(
                agreements = s.agreements,
                onBack = back,
                onToggleAll = authViewModel::toggleAllAgreements,
                onToggle = authViewModel::toggleAgreement,
                onOpenDoc = authViewModel::openLegalDoc,
                onContinue = authViewModel::agreeAndContinue,
            )

            SignupStep.LEGAL_DOC -> s.openedDoc?.let { LegalDocScreen(doc = it, onBack = back) }

            SignupStep.PROFILE -> ProfileSetupScreen(
                state = s,
                onBack = back,
                onPhotoClick = authViewModel::openPhotoPicker,
                onNicknameChange = authViewModel::updateNickname,
                onSubmit = { authViewModel.completeSignup(this@MainActivity) },
            )

            SignupStep.PHOTO -> ProfilePhotoScreen(
                draft = s.photoDraft,
                hasGooglePhoto = s.googlePhotoUrl != null,
                onBack = back,
                onPickAlbum = authViewModel::pickAlbumPhoto,
                onUseGooglePhoto = authViewModel::pickGooglePhoto,
                onUseDefault = authViewModel::pickDefaultPhoto,
                onConfirm = authViewModel::confirmPhoto,
            )
        }
    }
}
