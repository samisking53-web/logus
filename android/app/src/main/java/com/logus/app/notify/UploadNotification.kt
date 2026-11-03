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

/**
 * 오프라인 기록 자동 올리기의 폰 알림(2026-11-03 팀 결정).
 * 폰에 보관했던 기록이 인터넷이 연결되어 올라가면, 앱을 보고 있지 않을 때만 "기록 N개를 여정에 올렸어요" 알림을 띄운다
 * (앱을 보고 있으면 MainScreen 이 화면 안 안내를 띄운다). 여러 개가 올라가면 알림 하나의 숫자만 늘린다.
 * 알림을 누르면 홈(S01-A)이 열린다(촬영 알림과 같은 동작). 앱을 다시 보면 숫자를 처음으로 돌리고 알림을 지운다.
 */
object UploadNotification {
    private const val CHANNEL_ID = "log_upload"
    private const val NOTIFICATION_ID = 2
    private const val FAILED_NOTIFICATION_ID = 3
    private const val PREFS = "log_upload"
    private const val KEY_COUNT = "uploadedCount"
    private const val KEY_COINS = "uploadedCoins"

    /** 앱 화면을 보고 있는지(MainActivity 가 onStart·onStop 에서 바꾼다) */
    @Volatile
    var appVisible: Boolean = false

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "기록 올리기", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "인터넷이 없을 때 폰에 보관한 기록을 나중에 올리면 알려 줘요"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 보관했던 기록 하나가 올라갔다 */
    fun uploaded(context: Context, coinsGranted: Int) {
        if (appVisible) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        val coins = prefs.getInt(KEY_COINS, 0) + coinsGranted
        prefs.edit().putInt(KEY_COUNT, count).putInt(KEY_COINS, coins).apply()
        show(
            context,
            NOTIFICATION_ID,
            "기록 ${count}개를 여정에 올렸어요",
            "인터넷이 연결되어 폰에 보관했던 기록을 올렸어요" + if (coins > 0) " · 코인 ${coins}개" else "",
        )
    }

    /** 보관했던 기록을 올릴 수 없어 뺐다(여정에서 나갔거나 권한이 없음) */
    fun failed(context: Context) {
        show(
            context,
            FAILED_NOTIFICATION_ID,
            "기록을 올리지 못했어요",
            "여정 구성원이 아니거나 권한이 없어서 폰에 보관했던 기록을 올리지 못했어요",
        )
    }

    /** 앱을 다시 볼 때: 숫자를 처음으로 돌리고 올렸다는 알림을 지운다 */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun show(context: Context, id: Int, title: String, body: String) {
        if (!CaptureReminder.hasPermission(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(CaptureReminder.ACTION_OPEN_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // 그사이 알림 권한을 끈 경우
        }
    }
}
