package com.logus.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.logus.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 광고를 끝까지 봐서 보상을 받았는지 / 중간에 닫았는지 / 보여 주지 못했는지 */
enum class AdOutcome { EARNED, CLOSED_EARLY, FAILED }

/**
 * 구글 AdMob 보상형 광고를 불러오고 보여 준다(구글 공식 라이브러리 play-services-ads 를 코루틴으로 감쌌다).
 * 광고 단위 ID 는 BuildConfig.ADMOB_REWARDED_UNIT_ID(app/build.gradle.kts). 지금은 구글이 공개한 테스트 ID 라서
 * "Test Ad" 표시가 있는 테스트 광고만 나오고 수익은 없다. 출시 전에 AdMob 콘솔에서 만든 ID 로 바꾼다.
 */
object RewardedAds {
    @Volatile private var initialized = false

    /** 광고 라이브러리 준비(앱에서 처음 광고를 부를 때 한 번) */
    private fun ensureInitialized(context: Context) {
        if (initialized) return
        initialized = true
        MobileAds.initialize(context.applicationContext) {}
    }

    /** 광고 한 편을 불러온다(메인 스레드에서 부른다). 볼 수 있는 광고가 없거나 인터넷 문제면 null */
    suspend fun load(context: Context): RewardedAd? = suspendCancellableCoroutine { cont ->
        ensureInitialized(context)
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    /**
     * 광고를 전체 화면으로 보여 주고 닫힐 때까지 기다린다. 끝까지 봐서 보상을 받았으면 EARNED.
     * uid·viewId 는 나중에 AdMob 서버 측 보상 확인(SSV)으로 바꿀 때 서버가 "누구의 어느 광고"인지 알 수 있게 미리 넣어 둔다.
     */
    suspend fun show(activity: Activity, ad: RewardedAd, uid: String, viewId: String): AdOutcome =
        suspendCancellableCoroutine { cont ->
            var earned = false
            ad.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder().setUserId(uid).setCustomData(viewId).build(),
            )
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(if (earned) AdOutcome.EARNED else AdOutcome.CLOSED_EARLY)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(AdOutcome.FAILED)
                }
            }
            ad.show(activity) { earned = true }
        }
}
