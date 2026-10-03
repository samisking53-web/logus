package com.logus.app.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 로그인 화면의 상태 */
sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    data object Loading : AuthUiState
    data class SignedIn(val displayName: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/** 화면(LoginScreen)과 로그인 담당(AuthRepository) 사이에서 상태를 관리한다. 화면을 돌려도 상태가 유지된다. */
class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(
        repository.currentUser?.let { AuthUiState.SignedIn(it.displayName ?: "여행자") }
            ?: AuthUiState.SignedOut
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signInWithGoogle(activity: Activity) = signIn { repository.signInWithGoogle(activity) }

    fun signOut(activity: Activity) {
        viewModelScope.launch {
            repository.signOut(activity)
            _state.value = AuthUiState.SignedOut
        }
    }

    private fun signIn(block: suspend () -> FirebaseUser) {
        if (_state.value == AuthUiState.Loading) return // 버튼을 연달아 눌러도 한 번만
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val user = block()
                AuthUiState.SignedIn(user.displayName ?: "여행자")
            } catch (e: GetCredentialCancellationException) {
                AuthUiState.SignedOut // 구글 창을 닫음: 오류로 보지 않는다
            } catch (e: Exception) {
                Log.w(TAG, "로그인 실패", e)
                AuthUiState.Error(e.toKoreanMessage())
            }
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}

/** 사용자에게 보여 줄 한국어 오류 문구 */
private fun Exception.toKoreanMessage(): String = when (this) {
    is NoCredentialException -> "이 폰에 로그인된 구글 계정이 없어요. 설정에서 구글 계정을 추가해 주세요."
    is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> "사용이 중지된 계정이에요."
        else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요. ($errorCode)"
    }
    else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
}
