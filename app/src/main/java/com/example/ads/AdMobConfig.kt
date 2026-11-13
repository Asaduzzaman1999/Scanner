package com.example.ads

/**
 * =========================================================================
 * DEVELOPMENT / TEST ADS MODE CONFIGURATION
 * =========================================================================
 * Uses OFFICIAL Google AdMob Test Ad Unit IDs ONLY.
 * Do NOT replace with production IDs until release preparation.
 * All app features are 100% free with NO subscriptions, NO IAP, NO paywalls.
 */
object AdMobConfig {
    const val IS_TEST_ADS_MODE: Boolean = true

    // Official Google AdMob Android Test Ad Unit IDs
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/5224354917"

    // Cooldown so interstitial test ads never annoy users after every click
    const val INTERSTITIAL_COOLDOWN_MS: Long = 120_000L
}
