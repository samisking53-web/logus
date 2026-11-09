package com.logus.app.notify

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 알림함이 바뀌었을 수 있다는 신호. 앱을 보고 있을 때 푸시(촬영 알림·DAY RECAP 공개 알림)가 오면
 * LogUsMessagingService 가 보내고, MainScreen 이 홈 종 버튼의 안 읽은 수를 다시 센다.
 */
object NotificationInbox {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changed: SharedFlow<Unit> = _changed

    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}
