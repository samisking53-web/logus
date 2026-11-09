package com.logus.app.notify

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logus.app.journey.Journey
import com.logus.app.journey.JourneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 알림 화면 상태. unreadCount 는 홈 종 버튼 배지와 화면 위 "알림 3" 에 쓴다 */
data class NotificationsUiState(
    val loading: Boolean = false,
    val items: List<AppNotification> = emptyList(),
    val error: String? = null,
    val unreadCount: Int = 0,
)

/**
 * 알림 목록(2026-11-09 팀 그림) 상태 관리.
 * - 홈에 들어올 때·앱으로 돌아올 때·앱을 보는 중 푸시가 올 때 안 읽은 수만 센다(refreshCount)
 * - 종을 누르면 최근 50개를 읽는다(open). 누르면 그 알림만, "모두 읽음"이면 전부 읽음으로(화면을 먼저 바꾸고 서버에 보냄)
 */
class NotificationsViewModel(
    private val repository: NotificationRepository = NotificationRepository(),
    private val journeys: JourneyRepository = JourneyRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    /** 홈 종 버튼 배지: 안 읽은 알림 수를 다시 센다 */
    fun refreshCount(uid: String) {
        viewModelScope.launch {
            runCatching { repository.unreadCount(uid) }
                .onSuccess { count -> _state.update { it.copy(unreadCount = count) } }
                .onFailure { Log.w(TAG, "안 읽은 알림 수 세기 실패 (보안 규칙 배포 확인)", it) }
        }
    }

    /** 알림 화면을 열 때: 최근 알림을 읽는다 */
    fun open(uid: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.loadRecent(uid)
                val unreadShown = items.count { !it.read }
                _state.update {
                    it.copy(
                        loading = false,
                        items = items,
                        // 50개를 다 채웠으면 더 오래된 안 읽은 알림이 있을 수 있어 서버 수를 그대로 둔다
                        unreadCount = if (items.size < NotificationRepository.LIST_LIMIT) unreadShown else maxOf(unreadShown, it.unreadCount),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "알림 읽기 실패", e)
                _state.update { it.copy(loading = false, error = "알림을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.") }
            }
        }
    }

    /** 알림 하나를 눌렀을 때: 읽음으로 */
    fun markRead(uid: String, item: AppNotification) {
        if (item.read) return
        _state.update { s ->
            s.copy(
                items = s.items.map { if (it.id == item.id) it.copy(read = true) else it },
                unreadCount = (s.unreadCount - 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            runCatching { repository.markRead(uid, item.id) }.onFailure { Log.w(TAG, "읽음 표시 실패", it) }
        }
    }

    /** "모두 읽음" */
    fun markAllRead(uid: String) {
        if (_state.value.unreadCount == 0 && _state.value.items.none { !it.read }) return
        _state.update { s -> s.copy(items = s.items.map { it.copy(read = true) }, unreadCount = 0) }
        viewModelScope.launch {
            runCatching { repository.markAllRead(uid) }.onFailure {
                Log.w(TAG, "모두 읽음 실패", it)
                _state.update { s -> s.copy(error = "모두 읽음으로 바꾸지 못했어요. 다시 해 주세요.") }
                open(uid)
            }
        }
    }

    /** 끝난 여정의 알림을 눌렀을 때: 여정 문서를 읽는다(지워졌거나 나간 여정이면 null) */
    fun findJourney(journeyId: String, onResult: (Journey?) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { journeys.loadJourney(journeyId) }.getOrNull())
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private companion object {
        const val TAG = "NotificationsViewModel"
    }
}
