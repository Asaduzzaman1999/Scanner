package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.stringsFor
import com.example.security.AppLockManager
import com.example.ui.components.AppLockGateScreen
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.DocScanTheme
import com.example.viewmodel.AppViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val prefs by appViewModel.userPrefs.collectAsStateWithLifecycle()
            val isUnlocked by appViewModel.isAppUnlocked.collectAsStateWithLifecycle()
            val strings = stringsFor(prefs.language)

            DocScanTheme(themeMode = prefs.themeMode) {
                CompositionLocalProvider(LocalAppStrings provides strings) {
                    val needsLock = prefs.isAppLockEnabled && !isUnlocked
                    if (needsLock) {
                        val bioAvailable = prefs.biometricLockEnabled && AppLockManager.canUseBiometric(this)
                        LaunchedEffect(bioAvailable) {
                            if (bioAvailable) {
                                AppLockManager.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    title = strings.appName,
                                    subtitle = strings.biometricLock,
                                    negativeButtonText = strings.cancel,
                                    onSuccess = { appViewModel.unlockWithBiometric() },
                                    onError = {}
                                )
                            }
                        }
                        AppLockGateScreen(
                            biometricAvailable = bioAvailable,
                            onVerifyPin = { pin -> appViewModel.verifyAndUnlockApp(pin) },
                            onRequestBiometric = {
                                AppLockManager.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    title = strings.appName,
                                    subtitle = strings.biometricLock,
                                    negativeButtonText = strings.cancel,
                                    onSuccess = { appViewModel.unlockWithBiometric() },
                                    onError = {}
                                )
                            }
                        )
                    } else {
                        AppNavHost(appViewModel = appViewModel)
                    }
                }
            }
        }
    }
}
