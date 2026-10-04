package com.logus.app.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import com.logus.app.legal.LegalDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 회원가입 안에서 지금 보여 줄 화면 */
enum class SignupStep {
    TERMS, // 1 / 2 단계: 약관 동의
    LEGAL_DOC, // 약관 전문 보기(약관 동의 화면에서 › 를 눌렀을 때)
    PROFILE, // 2 / 2 단계: P01 프로필 설정(사진·닉네임)
    PHOTO, // P02 프로필 사진 고르기(P01 의 사진 원을 눌렀을 때)
}

/** 앱이 지금 보여 줄 화면 상태 */
sealed interface AuthUiState {
    /** 앱을 켜고 로그인·가입 여부를 확인하는 중 */
    data object Checking : AuthUiState

    /** 첫 화면(구글 계정으로 계속하기). busy = 로그인 진행 중, error = 보여 줄 오류 문구 */
    data class SignedOut(val busy: Boolean = false, val error: String? = null) : AuthUiState

    /** 구글 로그인은 했지만 가입 전 → 약관 동의 → 프로필 설정(P01·P02) */
    data class Signup(
        val email: String?,
        val googlePhotoUrl: String?,
        val step: SignupStep = SignupStep.TERMS,
        val openedDoc: LegalDoc? = null,
        val agreements: Agreements = Agreements(),
        val nickname: String,
        /** P01 에 보이는 확정된 사진 */
        val photo: PhotoChoice,
        /** P02 에서 고르는 중인 사진("이 사진 사용"을 누르면 photo 가 된다) */
        val photoDraft: PhotoChoice = photo,
        val saving: Boolean = false,
        val error: String? = null,
    ) : AuthUiState

    /** 가입까지 끝남 → 홈. uid 는 홈에서 내 여정을 찾을 때 쓴다 */
    data class Ready(val uid: String, val profile: Profile) : AuthUiState

    /** 프로필을 읽지 못함(네트워크·보안 규칙 문제 등) */
    data class Failed(val message: String) : AuthUiState
}

/**
 * 로그인·회원가입 흐름을 관리한다. 화면을 돌려도 상태가 유지된다.
 * 흐름: 앱 시작 → (로그인 기록 있음?) → 프로필 있음? → 홈 / 약관 동의 → P01(↔P02) → 홈 / 첫 화면
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

    // ---------- 첫 화면 ----------

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

    // ---------- 1 / 2 단계: 약관 동의 ----------

    fun toggleAllAgreements() = updateSignup { it.copy(agreements = it.agreements.toggleAll()) }

    fun toggleAgreement(item: AgreementItem) = updateSignup { it.copy(agreements = it.agreements.toggle(item)) }

    fun openLegalDoc(doc: LegalDoc) = updateSignup { it.copy(step = SignupStep.LEGAL_DOC, openedDoc = doc) }

    /** 필수 항목을 모두 동의했을 때만 다음(P01)으로 간다 */
    fun agreeAndContinue() = updateSignup {
        if (it.agreements.requiredDone) it.copy(step = SignupStep.PROFILE) else it
    }

    // ---------- 2 / 2 단계: P01 프로필 설정 · P02 사진 ----------

    fun updateNickname(value: String) = updateSignup {
        if (value.length <= 40) it.copy(nickname = value, error = null) else it
    }

    fun openPhotoPicker() = updateSignup { it.copy(step = SignupStep.PHOTO, photoDraft = it.photo) }

    fun pickAlbumPhoto(uri: Uri) = updateSignup { it.copy(photoDraft = PhotoChoice.Album(uri)) }

    fun pickGooglePhoto() = updateSignup { s ->
        s.googlePhotoUrl?.let { s.copy(photoDraft = PhotoChoice.Google(it)) } ?: s
    }

    fun pickDefaultPhoto() = updateSignup { it.copy(photoDraft = PhotoChoice.Default) }

    /** P02 "이 사진 사용" */
    fun confirmPhoto() = updateSignup { it.copy(photo = it.photoDraft, step = SignupStep.PROFILE) }

    /** P01 "가입 완료" */
    fun completeSignup(context: Context) {
        val current = _state.value as? AuthUiState.Signup ?: return
        if (current.saving) return
        if (!current.agreements.requiredDone) { // 혹시라도 약관 동의 없이 오면 약관 화면으로 돌려보낸다
            _state.value = current.copy(step = SignupStep.TERMS)
            return
        }
        val user = repository.currentUser ?: run {
            _state.value = AuthUiState.SignedOut()
            return
        }
        when (val check = checkNickname(current.nickname)) {
            is NicknameCheck.Invalid -> _state.value = current.copy(error = check.message)
            is NicknameCheck.Ok -> {
                _state.value = current.copy(saving = true, error = null)
                val appContext = context.applicationContext
                viewModelScope.launch {
                    _state.value = try {
                        AuthUiState.Ready(
                            user.uid,
                            repository.createProfile(appContext, user.uid, check.value, current.photo, current.agreements),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "회원가입 실패 (보안 규칙 배포·Firestore·Storage 설정 확인)", e)
                        current.copy(saving = false, error = "가입하지 못했어요. 잠시 후 다시 시도해 주세요.")
                    }
                }
            }
        }
    }

    // ---------- 뒤로 가기 · 로그아웃 ----------

    /**
     * 회원가입 화면의 뒤로 가기(화면 위 ‹ 와 폰의 뒤로 가기 버튼).
     * 약관 전문 → 약관, P02 → P01, P01 → 약관, 약관 → 가입 취소(로그아웃하고 첫 화면).
     */
    fun back(activity: Activity) {
        val current = _state.value as? AuthUiState.Signup ?: return
        if (current.saving) return
        when (current.step) {
            SignupStep.LEGAL_DOC -> _state.value = current.copy(step = SignupStep.TERMS, openedDoc = null)
            SignupStep.PHOTO -> _state.value = current.copy(step = SignupStep.PROFILE)
            SignupStep.PROFILE -> _state.value = current.copy(step = SignupStep.TERMS, error = null)
            SignupStep.TERMS -> signOut(activity)
        }
    }

    fun signOut(activity: Activity) {
        viewModelScope.launch {
            repository.signOut(activity)
            _state.value = AuthUiState.SignedOut()
        }
    }

    private inline fun updateSignup(change: (AuthUiState.Signup) -> AuthUiState.Signup) {
        val current = _state.value as? AuthUiState.Signup ?: return
        if (current.saving) return
        _state.value = change(current)
    }

    private fun checkProfile(user: FirebaseUser) {
        _state.value = AuthUiState.Checking
        viewModelScope.launch {
            _state.value = try {
                val profile = repository.loadProfile(user.uid)
                if (profile != null) {
                    AuthUiState.Ready(user.uid, profile)
                } else {
                    // 처음 온 사람: 약관 동의부터. 구글 사진이 있으면 그 사진을 기본으로 보여 준다.
                    val googlePhoto = safePhotoUrl(user.photoUrl?.toString())
                    AuthUiState.Signup(
                        email = user.email,
                        googlePhotoUrl = googlePhoto,
                        nickname = suggestNickname(user.displayName),
                        photo = googlePhoto?.let { PhotoChoice.Google(it) } ?: PhotoChoice.Default,
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
