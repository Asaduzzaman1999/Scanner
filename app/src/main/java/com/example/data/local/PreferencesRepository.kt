package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppLanguage
import com.example.core.model.CompressionPreset
import com.example.core.model.PageSizeOption
import com.example.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docscan_prefs")

data class UserPreferences(
    val language: AppLanguage = AppLanguage.EN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultCompression: CompressionPreset = CompressionPreset.HIGH_QUALITY,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false
) {
    val isAppLockEnabled: Boolean get() = !pinHash.isNullOrBlank()
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_COMPRESSION = stringPreferencesKey("default_compression")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.EN,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE]?.let {
                runCatching { PageSizeOption.valueOf(it) }.getOrNull()
            } ?: PageSizeOption.A4,
            defaultCompression = prefs[Keys.DEFAULT_COMPRESSION]?.let {
                runCatching { CompressionPreset.valueOf(it) }.getOrNull()
            } ?: CompressionPreset.HIGH_QUALITY,
            pinHash = prefs[Keys.PIN_HASH],
            biometricLockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultCompression(preset: CompressionPreset) {
        context.dataStore.edit { it[Keys.DEFAULT_COMPRESSION] = preset.name }
    }

    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit {
            if (hash == null) {
                it.remove(Keys.PIN_HASH)
                it[Keys.BIOMETRIC_ENABLED] = false
            } else {
                it[Keys.PIN_HASH] = hash
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }
}
