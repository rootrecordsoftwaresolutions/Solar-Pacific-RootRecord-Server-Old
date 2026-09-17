package com.rootrecord.kilauea.alerts.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.rootrecord.kilauea.alerts.BuildConfig

/**
 * Anchored adaptive banner. [BuildConfig.ADMOB_BANNER_AD_UNIT_ID] is Google's sample unit in
 * `debug` and the production Kīlauea banner unit in `release`.
 */
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp =
        (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adSize)
                adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
