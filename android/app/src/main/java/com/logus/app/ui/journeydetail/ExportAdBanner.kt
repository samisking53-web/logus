package com.logus.app.ui.journeydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.logus.app.BuildConfig
import com.logus.app.ads.RewardedAds
import com.logus.app.ui.theme.LogUsColors

// S17 DAY RECAP 일반 다운로드 중 광고(스토리보드에 없음, 2026-11-11 팀 요청: 영상을 추출하는 동안 광고를 보여 주고 아래에 추출 중 안내)
/**
 * 영상 만들기 팝업(일반 다운로드) 위쪽에 놓는 구글 AdMob 300×250 배너(중간 직사각형).
 * - 광고 단위 ID 는 BuildConfig.ADMOB_EXPORT_BANNER_UNIT_ID(app/build.gradle.kts). 지금은 구글 공개 테스트 ID 라 "Test Ad" 광고만 나온다.
 * - 광고를 불러오는 동안은 같은 크기 자리에 "광고를 불러오는 중"을 보여 주고(아래 글자가 밀리지 않게),
 *   볼 수 있는 광고가 없거나 인터넷 문제로 못 불러오면 광고 자리를 통째로 접는다(영상 만들기는 그대로 계속).
 * - 광고 위에 작은 "광고" 표시를 둬서 앱 내용과 헷갈리지 않게 한다. 광고는 자르거나 덮지 않는다(AdMob 정책).
 * - 앱을 내리면 광고를 멈추고(pause) 다시 보면 이어서(resume), 팝업이 닫히면 정리한다(destroy).
 */
@Composable
fun ExportAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 불러오는 중 / 불러옴 / 못 불러옴(접음)
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val adView = remember {
        RewardedAds.ensureInitialized(context)
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            adUnitId = BuildConfig.ADMOB_EXPORT_BANNER_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }
    if (failed) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "광고",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.width(300.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(300.dp, 250.dp), contentAlignment = Alignment.Center) {
            // 불러오는 동안 같은 크기 자리(광고가 오면 그 위에 덮인다)
            if (!loaded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(LogUsColors.iconCircle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = LogUsColors.primaryStrong, strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("광고를 불러오는 중", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            AndroidView(factory = { adView }, modifier = Modifier.size(300.dp, 250.dp))
        }
    }
}
