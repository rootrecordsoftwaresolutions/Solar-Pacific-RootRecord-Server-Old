package com.rootrecord.rootmc.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.rootrecord.rootmc.RootMcApplication
import com.rootrecord.rootmc.BuildConfig
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Anchored adaptive banner above the bottom nav (same pattern as Kīlauea Alerts).
 * [BuildConfig.ADMOB_BANNER_AD_UNIT_ID] is Google's sample unit in `debug` and the
 * production RootMC units in `release` (override via local.properties).
 */
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val unitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    if (unitId.isBlank()) return

    var adsReady by remember { mutableStateOf(RootMcApplication.isMobileAdsReady()) }
    LaunchedEffect(Unit) {
        if (!adsReady) {
            repeat(40) {
                if (RootMcApplication.isMobileAdsReady()) {
                    adsReady = true
                    return@LaunchedEffect
                }
                delay(100)
            }
            adsReady = true
        }
    }
    if (!adsReady) return

    val context = LocalContext.current
    val widthDp =
        (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { ctx ->
            runCatching {
                AdView(ctx).apply {
                    setAdSize(adSize)
                    adUnitId = unitId
                    loadAd(AdRequest.Builder().build())
                }
            }.getOrElse { android.view.View(ctx) }
        },
        onRelease = { view ->
            if (view is AdView) view.destroy()
        },
    )
}
