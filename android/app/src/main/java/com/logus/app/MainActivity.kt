package com.logus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.auth.AuthUiState
import com.logus.app.auth.AuthViewModel
import com.logus.app.ui.ErrorMessage
import com.logus.app.ui.HomeScreen
import com.logus.app.ui.LoginScreen
import com.logus.app.ui.SignupScreen
import com.logus.app.ui.theme.LogUsTheme

/**
 * 앱 시작 지점. 로그인·회원가입 상태에 따라 화면을 고른다.
 * 로그인 전 → 로그인 / 구글 로그인만 하고 가입 전 → 회원가입 / 가입 완료 → 홈
 */
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogUsTheme {
                val state by authViewModel.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (val s = state) {
                        AuthUiState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }

                        is AuthUiState.SignedOut -> LoginScreen(
                            busy = s.busy,
                            error = s.error,
                            onGoogleClick = { authViewModel.signInWithGoogle(this@MainActivity) },
                        )

                        is AuthUiState.NeedsProfile -> SignupScreen(
                            state = s,
                            onSubmit = { nickname, useGooglePhoto ->
                                authViewModel.completeSignup(nickname, useGooglePhoto)
                            },
                            onUseOtherAccount = { authViewModel.restart(this@MainActivity) },
                        )

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
                            TextButton(onClick = { authViewModel.restart(this@MainActivity) }) {
                                Text("다시 로그인하기", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
