package com.logus.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.logus.app.MainActivity
import com.logus.app.R

/**
 * 촬영 알림(공통 알림) 앱 쪽 공통 값과 도우미.
 * 서버 함수 sendCaptureReminders(functions/src/sendCaptureReminders.ts)가 여정 기간 동안 폰 시각 09~23시에
 * 공통 알림 간격마다 FCM 푸시를 보낸다. 알림을 누르면 앱 홈(S01-A)이 열린다(2026-10-29 팀 결정).
 */
object CaptureReminder {
    /** 알림 채널 ID(서버 REMINDER_CHANNEL_ID 와 같아야 한다) */
    const val CHANNEL_ID = "capture_reminder"

    /** 알림을 누르면 MainActivity 를 이 동작으로 연다(서버 REMINDER_CLICK_ACTION 과 같아야 한다) */
    const val ACTION_OPEN_HOME = "com.logus.app.CAPTURE_REMINDER"

    private const val PREFS = "capture_reminder"
    private const val KEY_ASKED_PERMISSION = "askedPermission"

    /** 알림 채널 만들기(앱을 켤 때마다 불러도 된다. 이미 있으면 그대로) */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "촬영 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "여정 중 함께 기록할 시간을 공통 알림 간격마다 알려 줘요"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 안드로이드 13 이상에서 알림 권한을 받았는지(12 이하는 늘 true) */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 알림 권한을 이미 한 번 물어봤는지(계속 묻지 않으려고 기억해 둔다) */
    fun askedPermission(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_PERMISSION, false)

    fun markAskedPermission(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED_PERMISSION, true).apply()
    }

    /**
     * 앱을 보고 있을 때 온 푸시는 안드로이드가 알림 줄에 자동으로 띄우지 않으므로 직접 띄운다.
     * (앱이 꺼져 있거나 뒤에 있을 때는 FCM 이 같은 채널로 알아서 띄운다)
     */
    fun show(context: Context, title: String, body: String, tag: String?) {
        if (!hasPermission(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 1, notification)
        } catch (e: SecurityException) {
            // 그사이 권한을 끈 경우
        }
    }
}
