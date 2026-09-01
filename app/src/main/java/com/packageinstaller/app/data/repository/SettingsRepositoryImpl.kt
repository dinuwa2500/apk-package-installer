package com.packageinstaller.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.packageinstaller.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _showHiddenDirectories = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    override val showHiddenDirectories: Flow<Boolean> = _showHiddenDirectories.asStateFlow()

    private val _autoScanOnStartup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCAN, true))
    override val autoScanOnStartup: Flow<Boolean> = _autoScanOnStartup.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "system") ?: "system")
    override val themeMode: Flow<String> = _themeMode.asStateFlow()

    override suspend fun setShowHiddenDirectories(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
        _showHiddenDirectories.value = show
    }

    override suspend fun setAutoScanOnStartup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
        _autoScanOnStartup.value = enabled
    }

    override suspend fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val KEY_SHOW_HIDDEN = "key_show_hidden_directories"
        private const val KEY_AUTO_SCAN = "key_auto_scan_startup"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }
}
