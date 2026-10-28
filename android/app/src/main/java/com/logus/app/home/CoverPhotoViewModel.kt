package com.logus.app.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 대표 사진 선택 팝업 상태 */
data class CoverPhotoUiState(
    /** 어느 여정의 대표 사진을 고르는지. null 이면 팝업이 닫혀 있다 */
    val journeyId: String? = null,
    /** 지금 대표 사진 경로(새 사진을 저장하면 이 파일을 지운다) */
    val currentPath: String? = null,
    val access: PhotoAccess = PhotoAccess.NONE,
    /** 권한을 한 번이라도 물어봤는지(거절한 뒤에는 설정 열기 안내도 보여 준다) */
    val asked: Boolean = false,
    val loading: Boolean = false,
    /** 최근 사진 전체(앨범을 고르면 화면에서 거른다) */
    val photos: List<GalleryPhoto> = emptyList(),
    val albums: List<GalleryAlbum> = emptyList(),
    /** 고른 앨범. null = 최근 항목 */
    val albumId: String? = null,
    val selected: GalleryPhoto? = null,
    val saving: Boolean = false,
    val error: String? = null,
    /** 저장이 끝난 새 경로(MainScreen 이 홈 카드에 반영하고 팝업을 닫는다) */
    val savedPath: String? = null,
) {
    val open: Boolean get() = journeyId != null

    /** 지금 앨범의 사진들 */
    val visiblePhotos: List<GalleryPhoto>
        get() = if (albumId == null) photos else photos.filter { it.albumId == albumId }

    val albumName: String
        get() = albums.firstOrNull { it.id == albumId }?.name ?: "최근 항목"
}

/**
 * 대표 사진 선택 팝업(S01-A 여정 카드 사진 오른쪽 위 전환 버튼): 폰 사진첩에서 한 장을 골라 내 홈의 여정 카드 사진으로 쓴다.
 * 사진은 "이 사진으로 변경"을 눌러야 올라간다. 내 멤버 문서에 저장해서 다른 구성원의 홈은 바뀌지 않는다.
 */
class CoverPhotoViewModel(
    private val repository: CoverPhotoRepository = CoverPhotoRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(CoverPhotoUiState())
    val state: StateFlow<CoverPhotoUiState> = _state.asStateFlow()

    /** 팝업 열기: 권한이 있으면 사진첩을 바로 읽는다(없으면 화면이 권한을 묻는다) */
    fun open(context: Context, journeyId: String, currentPath: String?) {
        _state.value = CoverPhotoUiState(journeyId = journeyId, currentPath = currentPath)
        refresh(context)
    }

    fun close() {
        if (_state.value.saving) return
        _state.value = CoverPhotoUiState()
    }

    /** 권한 창을 닫은 뒤 등: 권한을 다시 확인하고 사진첩을 다시 읽는다 */
    fun onPermissionResult(context: Context) {
        _state.update { it.copy(asked = true) }
        refresh(context)
    }

    private fun refresh(context: Context) {
        val access = GalleryRepository.access(context)
        _state.update { it.copy(access = access, loading = access != PhotoAccess.NONE, error = null) }
        if (access == PhotoAccess.NONE) return
        val app = context.applicationContext
        viewModelScope.launch {
            val result = runCatching { GalleryRepository.load(app) }
                .onFailure { Log.w(TAG, "사진첩 읽기 실패", it) }
                .getOrNull()
            _state.update { state ->
                if (!state.open) return@update state
                if (result == null) return@update state.copy(loading = false, error = "사진첩을 읽지 못했어요. 다시 열어 주세요.")
                val (photos, names) = result
                val albums = listOf(GalleryAlbum(null, "최근 항목", photos.size)) +
                    photos.groupBy { it.albumId }
                        .filterKeys { it != null }
                        .map { (id, list) -> GalleryAlbum(id, names[id] ?: "앨범", list.size) }
                        .sortedByDescending { it.count }
                state.copy(
                    loading = false,
                    photos = photos,
                    albums = albums,
                    // 고른 앨범·사진이 그대로 있으면 둔다
                    albumId = state.albumId.takeIf { id -> albums.any { it.id == id } },
                    selected = state.selected?.takeIf { sel -> photos.any { it.uri == sel.uri } },
                )
            }
        }
    }

    fun selectAlbum(albumId: String?) {
        _state.update { it.copy(albumId = albumId) }
    }

    fun select(photo: GalleryPhoto) {
        _state.update { it.copy(selected = photo, error = null) }
    }

    /** "이 사진으로 변경": 줄여서 올리고 내 멤버 문서의 coverPath 를 바꾼다 */
    fun save(context: Context, uid: String) {
        val current = _state.value
        val journeyId = current.journeyId ?: return
        val photo = current.selected ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val path = repository.saveCover(context.applicationContext, journeyId, uid, photo.uri, current.currentPath)
                _state.update { it.copy(saving = false, savedPath = path) }
            } catch (e: Exception) {
                Log.w(TAG, "대표 사진 저장 실패 (보안 규칙 배포 확인)", e)
                _state.update { it.copy(saving = false, error = "사진을 바꾸지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.") }
            }
        }
    }

    private companion object {
        const val TAG = "CoverPhotoViewModel"
    }
}
