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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 앱이 지금 보여 줄 화면 상태 */
sealed interface AuthUiState {
    /** 앱을 켜고 로그인·가입 여부를 확인하는 중 */
    data object Checking : AuthUiState

    /** 로그인 화면 (busy = 로그인 진행 중, error = 보여 줄 오류 문구) */
    data class SignedOut(val busy: Boolean = false, val error: String? = null) : AuthUiState

    /** 구글 로그인은 했지만 회원가입(프로필) 전 → 회원가입 화면 */
    data class NeedsProfile(
        val email: String?,
        val suggestedNickname: String,
        val googlePhotoUrl: String?,
        val saving: Boolean = false,
        val error: String? = null,
    ) : AuthUiState

    /** 가입까지 끝남 → 홈 */
    data class Ready(val profile: Profile) : AuthUiState

    /** 프로필을 읽지 못함(네트워크·보안 규칙 문제 등) */
    data class Failed(val message: String) : AuthUiState
}

/**
 * 로그인·회원가입 흐름을 관리한다. 화면을 돌려도 상태가 유지된다.
 * 흐름: 앱 시작 → (로그인 기록 있음?) → 프로필 있음? → 홈 / 회원가입 / 로그인
 */
class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // 이전에 로그인한 적이 있으면 Firebase 가 기억하고 있다 → 바로 프로필을 확인한다.
        val user = repository.currentUser
        if (user == null) _state.value = AuthUiState.SignedOut() else checkProfile(user)
    }

    fun signInWithGoogle(activity: Activity) {
        val current = _state.value
        if (current is AuthUiState.SignedOut && current.busy) return // 연달아 눌러도 한 번만
        _state.value = AuthUiState.SignedOut(busy = true)
        viewModelScope.launch {
            try {
                val user = repository.signInWithGoogle(activity)
                checkProfile(user)
            } catch (e: GetCredentialCancellationException) {
                _state.value = AuthUiState.SignedOut() // 계정 선택 창을 닫음: 오류로 보지 않는다
            } catch (e: Exception) {
                Log.w(TAG, "구글 로그인 실패", e)
                _state.value = AuthUiState.SignedOut(error = e.toKoreanMessage())
            }
        }
    }

    /** 회원가입 화면의 "가입 완료" */
    fun completeSignup(nickname: String, useGooglePhoto: Boolean) {
        val current = _state.value as? AuthUiState.NeedsProfile ?: return
        if (current.saving) return
        val user = repository.currentUser ?: run {
            _state.value = AuthUiState.SignedOut()
            return
        }
        when (val check = checkNickname(nickname)) {
            is NicknameCheck.Invalid -> _state.value = current.copy(error = check.message)
            is NicknameCheck.Ok -> {
                _state.value = current.copy(saving = true, error = null)
                viewModelScope.launch {
                    _state.value = try {
                        val photo = if (useGooglePhoto) current.googlePhotoUrl else null
                        AuthUiState.Ready(repository.createProfile(user.uid, check.value, photo))
                    } catch (e: Exception) {
                        Log.w(TAG, "회원가입 실패 (보안 규칙 배포·Firestore 생성 여부 확인)", e)
                        current.copy(saving = false, error = "가입하지 못했어요. 잠시 후 다시 시도해 주세요.")
                    }
                }
            }
        }
    }

    fun signOut(activity: Activity) {
        viewModelScope.launch {
            repository.signOut(activity)
            _state.value = AuthUiState.SignedOut()
        }
    }

    /** 회원가입 화면의 "다른 구글 계정으로 하기", 오류 화면의 "다시 로그인" */
    fun restart(activity: Activity) = signOut(activity)

    private fun checkProfile(user: FirebaseUser) {
        _state.value = AuthUiState.Checking
        viewModelScope.launch {
            _state.value = try {
                val profile = repository.loadProfile(user.uid)
                if (profile != null) {
                    AuthUiState.Ready(profile)
                } else {
                    AuthUiState.NeedsProfile(
                        email = user.email,
                        suggestedNickname = suggestNickname(user.displayName),
                        googlePhotoUrl = safePhotoUrl(user.photoUrl?.toString()),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "프로필 확인 실패", e)
                AuthUiState.Failed(e.toKoreanMessage())
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
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE -> "인터넷 연결을 확인해 주세요."
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "회원 정보를 읽을 권한이 없어요. (보안 규칙 배포 확인 필요)"
        else -> "회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
    }
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> "사용이 중지된 계정이에요."
        else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요. ($errorCode)"
    }
    else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
}
