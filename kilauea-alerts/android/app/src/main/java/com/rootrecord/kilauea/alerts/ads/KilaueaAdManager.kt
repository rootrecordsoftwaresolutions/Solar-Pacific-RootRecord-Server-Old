package com.rootrecord.kilauea.alerts.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rootrecord.kilauea.alerts.BuildConfig
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KilaueaAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: KilaueaPreferences,
) {
    private var activity: Activity? = null
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var showWhenLoaded = false

    fun setActivity(activity: Activity?) {
        this.activity = activity
        if (activity != null) {
            preloadInterstitialIfNeeded()
        }
    }

    suspend fun recordAction() {
        if (BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.isBlank()) return
        if (prefs.authProUnlocked.first()) return

        val count = prefs.incrementAdActionCount()
        if (count >= ACTIONS_PER_INTERSTITIAL) {
            prefs.resetAdActionCount()
            showInterstitialIfReady()
        } else {
            preloadInterstitialIfNeeded()
        }
    }

    private fun preloadInterstitialIfNeeded() {
        if (interstitial != null || loadingInterstitial) return
        if (BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.isBlank()) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            preloadInterstitialIfNeeded()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitial = null
                            preloadInterstitialIfNeeded()
                        }
                    }
                    if (showWhenLoaded) {
                        showWhenLoaded = false
                        showInterstitialIfReady()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    showWhenLoaded = false
                    Log.w(TAG, "Interstitial load failed: ${error.message}")
                }
            },
        )
    }

    private fun showInterstitialIfReady() {
        val act = activity ?: return
        val ad = interstitial
        if (ad != null) {
            interstitial = null
            ad.show(act)
        } else {
            showWhenLoaded = true
            preloadInterstitialIfNeeded()
        }
    }

    companion object {
        const val ACTIONS_PER_INTERSTITIAL = 50
        private const val TAG = "KilaueaAds"
    }
}
