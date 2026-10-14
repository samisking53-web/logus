package com.logus.app.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logus.app.auth.AuthRepository
import com.logus.app.auth.NicknameCheck
import com.logus.app.auth.Profile
import com.logus.app.auth.ProfilePhotoChange
import com.logus.app.auth.checkNickname
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 프로필 사진 수정 팝업의 사진 고르기 버튼 세 개 */
enum class PhotoSource { CAMERA, ALBUM, DEFAULT }

/** 홈 "사진 수정" → 프로필 사진 수정 팝업 상태 */
data class ProfileEditUiState(
    val open: Boolean = false,
    val nickname: String = "",
    /** 팝업을 열 때의 사진 주소(null 이면 기본 이미지) */
    val originalPhotoUrl: String? = null,
    /** 이번에 고른 방법. null 이면 지금 사진 그대로 */
    val choice: PhotoSource? = null,
    /** 찍거나 앨범에서 고른 사진 */
    val pickedUri: Uri? = null,
    val saving: Boolean = false,
    /** 사용자에게 보여 줄 오류 문구 */
    val error: String? = null,
    /** 저장을 마쳤다(MainScreen 이 보고 팝업을 닫고 안내한다) */
    val saved: Boolean = false,
) {
    /** 팝업 가운데 동그라미에 보여 줄 사진: 고른 사진 → 기본 이미지(null) → 지금 사진 */
    val previewPhoto: Any?
        get() = when (choice) {
            PhotoSource.CAMERA, PhotoSource.ALBUM -> pickedUri
            PhotoSource.DEFAULT -> null
            null -> originalPhotoUrl
        }

    /** 진하게 칠할 버튼: 고른 방법, 아무것도 안 골랐으면 지금이 기본 이미지일 때만 "기본 이미지" */
    val selectedSource: PhotoSource?
        get() = choice ?: if (originalPhotoUrl == null) PhotoSource.DEFAULT else null
}

/**
 * 홈(S01·S01-A) "사진 수정" → 프로필 사진 수정 팝업: 사진(사진 찍기·앨범에서·기본 이미지)과 닉네임을 바꾼다.
 * 저장하면 users/{uid} 가 바뀌고, 홈은 내 프로필을 실시간으로 지켜보고 있어서(AuthViewModel) 바로 새 사진·이름이 보인다.
 */
class ProfileEditViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    /** 팝업 열기: 지금 프로필에서 시작한다 */
    fun open(profile: Profile) {
        if (_state.value.saving) return
        _state.value = ProfileEditUiState(open = true, nickname = profile.nickname, originalPhotoUrl = profile.photoUrl)
    }

    fun close() {
        if (_state.value.saving) return
        _state.value = ProfileEditUiState()
    }

    fun updateNickname(value: String) {
        if (value.length > 40) return // 붙여넣기로 너무 길어지지 않게(실제 제한은 저장할 때 20자)
        _state.update { it.copy(nickname = value, error = null) }
    }

    fun tookPhoto(uri: Uri) = _state.update { it.copy(choice = PhotoSource.CAMERA, pickedUri = uri, error = null) }

    fun pickAlbum(uri: Uri) = _state.update { it.copy(choice = PhotoSource.ALBUM, pickedUri = uri, error = null) }

    fun useDefault() = _state.update { it.copy(choice = PhotoSource.DEFAULT, pickedUri = null, error = null) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    /** "저장" */
    fun save(context: Context, uid: String) {
        val current = _state.value
        if (current.saving) return
        val nickname = when (val check = checkNickname(current.nickname)) {
            is NicknameCheck.Invalid -> {
                _state.update { it.copy(error = check.message) }
                return
            }
            is NicknameCheck.Ok -> check.value
        }
        val change = when (current.choice) {
            PhotoSource.CAMERA, PhotoSource.ALBUM -> current.pickedUri?.let { ProfilePhotoChange.Upload(it) } ?: ProfilePhotoChange.Keep
            PhotoSource.DEFAULT -> if (current.originalPhotoUrl == null) ProfilePhotoChange.Keep else ProfilePhotoChange.Default
            null -> ProfilePhotoChange.Keep
        }
        _state.update { it.copy(saving = true, error = null) }
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                repository.updateProfile(appContext, uid, nickname, change, current.originalPhotoUrl)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                Log.w(TAG, "프로필 저장 실패 (보안 규칙·Storage 배포 확인)", e)
                _state.update { it.copy(saving = false, error = "저장하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.") }
            }
        }
    }

    private companion object {
        const val TAG = "ProfileEditViewModel"
    }
}
