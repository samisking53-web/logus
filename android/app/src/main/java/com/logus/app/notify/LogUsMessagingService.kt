package com.logus.app.notify

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM 푸시를 받는 곳(AndroidManifest 에 등록).
 * - 토큰이 새로 생기거나 바뀌면(앱 재설치·데이터 삭제 등) 로그인한 사람의 기기 문서를 다시 저장한다.
 * - 앱을 보고 있을 때 온 촬영 알림은 직접 알림 줄에 띄운다(앱이 뒤에 있을 때는 FCM 이 알아서 띄운다).
 */
class LogUsMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return // 로그인 전이면 홈에 들어올 때 저장한다
        scope.launch {
            runCatching { DeviceRepository().register(uid, token) }
                .onFailure { Log.w(TAG, "새 알림 토큰 저장 실패", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        CaptureReminder.show(
            context = this,
            title = notification.title ?: "지금 기록할 시간이에요",
            body = notification.body.orEmpty(),
            tag = message.data["journeyId"]?.let { "capture_$it" },
        )
    }

    private companion object {
        const val TAG = "LogUsMessaging"
    }
}
