package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ads.AdMobConfig
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.AppLanguage
import com.example.core.model.CompressionPreset
import com.example.core.model.PageSizeOption
import com.example.core.model.ThemeMode
import com.example.data.local.UserPreferences
import com.example.security.AppLockManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDefaultPageSizeChange: (PageSizeOption) -> Unit,
    onDefaultCompressionChange: (CompressionPreset) -> Unit,
    onSetPin: (String?) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onCleanTempFiles: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val biometricSupported = remember { AppLockManager.canUseBiometric(context) }
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Language Card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = prefs.language == AppLanguage.EN,
                            onClick = { onLanguageChange(AppLanguage.EN) },
                            label = { Text("English") },
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                        FilterChip(
                            selected = prefs.language == AppLanguage.BN,
                            onClick = { onLanguageChange(AppLanguage.BN) },
                            label = { Text("বাংলা (Bengali)") },
                            modifier = Modifier.testTag("settings_lang_bn")
                        )
                    }
                }
            }

            // Appearance Card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.themeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeChange(ThemeMode.SYSTEM) },
                            label = { Text(s.themeSystem) }
                        )
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeChange(ThemeMode.LIGHT) },
                            label = { Text(s.themeLight) }
                        )
                        FilterChip(
                            selected = prefs.themeMode == ThemeMode.DARK,
                            onClick = { onThemeChange(ThemeMode.DARK) },
                            label = { Text(s.themeDark) }
                        )
                    }
                }
            }

            // Defaults Card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.pageSize, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PageSizeOption.A4, PageSizeOption.A5, PageSizeOption.LETTER, PageSizeOption.LEGAL).forEach { sz ->
                            FilterChip(
                                selected = prefs.defaultPageSize == sz,
                                onClick = { onDefaultPageSizeChange(sz) },
                                label = { Text(sz.label) }
                            )
                        }
                    }
                    Text(s.quality, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.defaultCompression == CompressionPreset.HIGH_QUALITY,
                            onClick = { onDefaultCompressionChange(CompressionPreset.HIGH_QUALITY) },
                            label = { Text(s.highQuality) }
                        )
                        FilterChip(
                            selected = prefs.defaultCompression == CompressionPreset.BALANCED,
                            onClick = { onDefaultCompressionChange(CompressionPreset.BALANCED) },
                            label = { Text(s.balanced) }
                        )
                        FilterChip(
                            selected = prefs.defaultCompression == CompressionPreset.SMALL_SIZE,
                            onClick = { onDefaultCompressionChange(CompressionPreset.SMALL_SIZE) },
                            label = { Text(s.smallSize) }
                        )
                    }
                }
            }

            // Security & Privacy Card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text(s.appLockTitle) },
                        supportingContent = { Text(if (prefs.isAppLockEnabled) "4-digit PIN active" else "Disabled") },
                        leadingContent = { Icon(Icons.Filled.Lock, null) },
                        trailingContent = {
                            Switch(
                                checked = prefs.isAppLockEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) showPinDialog = true else onSetPin(null)
                                }
                            )
                        }
                    )
                    if (prefs.isAppLockEnabled && biometricSupported) {
                        ListItem(
                            headlineContent = { Text(s.biometricLock) },
                            leadingContent = { Icon(Icons.Filled.Fingerprint, null) },
                            trailingContent = {
                                Switch(checked = prefs.biometricLockEnabled, onCheckedChange = onToggleBiometric)
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text(s.clearTempFiles) },
                        leadingContent = { Icon(Icons.Filled.CleaningServices, null) },
                        modifier = Modifier.clickable { onCleanTempFiles() }
                    )
                }
            }

            // Backup & Storage Architecture Card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.backupArchTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(s.backupArchSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Local Storage: PDF Maker/{Scanned Documents, NID, Passport, Certificates, Receipts, Images, Trash}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Info & AdMob Test Mode Notice
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text(s.about) },
                        supportingContent = {
                            Text(if (AdMobConfig.IS_TEST_ADS_MODE) s.testAdsBadge else s.offlineBadge)
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null) },
                        modifier = Modifier.clickable { onOpenAbout() }
                    )
                    ListItem(
                        headlineContent = { Text(s.privacyPolicy) },
                        leadingContent = { Icon(Icons.Filled.PrivacyTip, null) },
                        modifier = Modifier.clickable { onOpenPrivacy() }
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(s.setPin) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("4-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = pin.length == 4,
                    onClick = {
                        onSetPin(pin)
                        showPinDialog = false
                    }
                ) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text(s.cancel) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val s = LocalAppStrings.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.about) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(s.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Version 1.0.0 • ${s.offlineBadge}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "DocScan PDF is a complete, free, offline-first document scanner and PDF workbench. " +
                    "All core features—including single & batch scanning, auto-edge crop, perspective correction, shadow cleanup, " +
                    "NID Front+Back dual-side layout, 18 document templates, full PDF editing, compression, encryption, OCR, and printing—are unlocked for everyone with zero subscriptions or paywalls.",
                style = MaterialTheme.typography.bodyLarge
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.testAdsBadge, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "This build uses Google AdMob official test ad unit IDs exclusively for development verification.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val s = LocalAppStrings.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.privacyPolicy) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(s.privacyPolicy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "1. On-Device Processing: All camera scanning, auto-cropping, image enhancement, OCR text recognition, and PDF generation run locally on your Android device. Your sensitive NIDs, passports, certificates, and documents are never uploaded to any server.\n\n" +
                    "2. Local Storage & Vault: Documents are stored inside your app's private 'PDF Maker/' directory. You can protect individual PDFs with AES/RC4 password encryption and lock the entire app using a 4-digit PIN or biometric authentication.\n\n" +
                    "3. Least-Privilege Permissions: Camera permission is used solely for scanning, and Android Photo Picker is used for importing gallery images without broad storage permissions.\n\n" +
                    "4. Temporary Files: Temporary scan caches are cleaned automatically and can be purged anytime from Settings.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
