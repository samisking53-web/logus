package com.logus.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.logus.app.MainActivity
import com.logus.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * DAY RECAP 탐색 공개 동의 알림(2026-11-08).
 * 구성원 한 사람이 DAY RECAP 을 탐색에 올리자고 하면 서버(requestRecapPublish)가 다른 구성원에게 푸시를 보낸다.
 * 알림 배너를 누르면 앱이 열리고 업로드 동의 팝업(RecapConsentDialog)이 뜬다.
 * 모두 동의해서 올라갔을 때·누군가 거절했을 때도 같은 채널로 알린다.
 */
object RecapConsent {
    /** 알림 채널 ID(서버 CONSENT_CHANNEL_ID 와 같아야 한다) */
    const val CHANNEL_ID = "recap_consent"

    /** 알림을 누르면 MainActivity 를 이 동작으로 연다(서버 CONSENT_CLICK_ACTION, AndroidManifest 와 같아야 한다) */
    const val ACTION_OPEN_CONSENT = "com.logus.app.RECAP_CONSENT"

    /** 서버가 보내는 푸시 종류(data.type) */
    val TYPES = setOf("recapConsent", "recapPublished", "recapRejected")

    private val _arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 앱을 보고 있을 때 동의 요청 푸시가 오면 알린다 → MainScreen 이 바로 업로드 동의 팝업을 띄운다 */
    val arrived: SharedFlow<Unit> = _arrived

    fun notifyArrived() {
        _arrived.tryEmit(Unit)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "DAY RECAP 공개 동의", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "구성원이 DAY RECAP을 탐색에 올리려고 할 때 동의를 받아요"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 앱을 보고 있을 때 온 푸시는 직접 알림 줄에 띄운다(앱이 뒤에 있을 때는 FCM 이 같은 채널로 띄운다) */
    fun show(context: Context, title: String, body: String, tag: String?) {
        if (!CaptureReminder.hasPermission(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_CONSENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 2, notification)
        } catch (e: SecurityException) {
            // 그사이 권한을 끈 경우
        }
    }
}
