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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.auth.AuthUiState
import com.logus.app.auth.AuthViewModel
import com.logus.app.auth.SignupStep
import com.logus.app.ui.MainScreen
import com.logus.app.ui.SplashScreen
import com.logus.app.ui.WelcomeScreen
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.signup.LegalDocScreen
import com.logus.app.ui.signup.ProfilePhotoScreen
import com.logus.app.ui.signup.ProfileSetupScreen
import com.logus.app.ui.signup.TermsScreen
import com.logus.app.ui.theme.LogUsTheme
import kotlinx.coroutines.delay

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

                // 앱 시작 화면: 켤 때마다 SPLASH_MILLIS 동안 보여 준다(화면을 돌려도 다시 나오지 않게 rememberSaveable).
                // 그동안 로그인·가입 여부를 확인하고, 확인이 더 오래 걸리면 끝날 때까지 계속 보여 준다.
                var splashTimeUp by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    splashTimeUp = true
                }
                val showSplash = !splashTimeUp || state is AuthUiState.Checking

                Crossfade(targetState = showSplash, label = "splash") { splash ->
                    if (splash) {
                        SplashScreen()
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (val s = state) {
                                AuthUiState.Checking -> SplashScreen() // 위에서 이미 시작 화면을 보여 주므로 실제로는 오지 않는다

                                is AuthUiState.SignedOut -> WelcomeScreen(
                                    busy = s.busy,
                                    error = s.error,
                                    onGoogleClick = { authViewModel.signInWithGoogle(this@MainActivity) },
                                )

                                is AuthUiState.Signup -> SignupFlow(s)

                                is AuthUiState.Ready -> MainScreen(
                                    uid = s.uid,
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

    private companion object {
        /** 앱 시작 화면을 보여 주는 시간(밀리초) */
        const val SPLASH_MILLIS = 1500L
    }
}
