package com.logus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.auth.AuthViewModel
import com.logus.app.ui.LoginScreen
import com.logus.app.ui.theme.LogUsTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogUsTheme {
                val state by authViewModel.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LoginScreen(
                        state = state,
                        onGoogleClick = { authViewModel.signInWithGoogle(this@MainActivity) },
                        onSignOutClick = { authViewModel.signOut(this@MainActivity) },
                    )
                }
            }
        }
    }
}
