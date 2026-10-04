package com.logus.app.journey

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast

/**
 * 여정 시작일에 폰 카메라를 여는 일.
 * - 오늘 시작하는 여정을 만들면 바로, 미리 만든 여정은 시작일에 앱을 처음 열 때 폰의 카메라 앱이 열린다.
 * - 같은 여정에 대해 이 폰에서 한 번만 자동으로 연다(앱을 다시 켤 때마다 열리지 않게). 기억은 이 폰에만 남는다.
 * - 지금은 폰 기본 카메라 앱을 연다(찍은 사진은 폰 갤러리에 저장된다). 앱 안 카메라(S04)·기록 올리기(S05)를
 *   만들면 그쪽으로 바꾼다.
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

    /** 폰 카메라 앱(사진 촬영 모드)을 연다 */
    fun open(context: Context) {
        try {
            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "카메라 앱을 열 수 없어요.", Toast.LENGTH_SHORT).show()
        }
    }
}
