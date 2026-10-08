package com.logus.app.journey

import android.content.Context

/**
 * 여정 시작일에 앱 안 카메라(S04)를 여는 일.
 * - 오늘 시작하는 여정을 만들면 바로, 미리 만든 여정은 시작일에 앱을 처음 열 때 S04 앱 내 카메라가 열린다(MainScreen).
 * - 같은 여정에 대해 이 폰에서 한 번만 자동으로 연다(앱을 다시 켤 때마다 열리지 않게). 기억은 이 폰에만 남는다.
 */
object StartDayCamera {
    private const val PREFS = "start_day_camera"

    /** 이 여정에서 아직 자동으로 연 적이 없으면 true 를 돌려주고, 연 것으로 기록한다 */
    fun claimAutoOpen(context: Context, journeyId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(journeyId, false)) return false
        prefs.edit().putBoolean(journeyId, true).apply()
        return true
    }
}
