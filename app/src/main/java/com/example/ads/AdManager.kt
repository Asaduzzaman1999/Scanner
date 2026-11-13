package com.example.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialShownAt: Long = 0L
    private var initialized: Boolean = false

    fun initialize() {
        if (initialized) return
        initialized = true
        runCatching {
            MobileAds.initialize(context) {
                preloadTestInterstitial()
            }
        }
    }

    fun preloadTestInterstitial() {
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                AdMobConfig.INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                    }
                }
            )
        }
    }

    /**
     * Shows a Test Interstitial ONLY during non-critical transitions (e.g. after saving a PDF),
     * respecting cooldown so it never interrupts scanning or fires on every click.
     */
    fun maybeShowTestInterstitial(activity: Activity?, onContinue: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd
        if (activity != null && ad != null && now - lastInterstitialShownAt >= AdMobConfig.INTERSTITIAL_COOLDOWN_MS) {
            lastInterstitialShownAt = now
            interstitialAd = null
            runCatching {
                ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        preloadTestInterstitial()
                        onContinue()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        preloadTestInterstitial()
                        onContinue()
                    }
                }
                ad.show(activity)
            }.onFailure {
                onContinue()
            }
        } else {
            onContinue()
        }
    }
}

@Composable
fun TestBannerAdView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Test Ad",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp)
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdMobConfig.BANNER_AD_UNIT_ID
                    runCatching { loadAd(AdRequest.Builder().build()) }
                }
            }
        )
    }
}
