package com.packageinstaller.app.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packageinstaller.app.PackageInstallerApplication
import com.packageinstaller.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val showHiddenDirectories: Boolean = false,
    val autoScanOnStartup: Boolean = true,
    val themeMode: String = "system",
    val hasStoragePermission: Boolean = true,
    val hasInstallPermission: Boolean = true
)

class SettingsViewModel : ViewModel() {

    private val app = PackageInstallerApplication.instance
    private val settingsRepository: SettingsRepository = app.settingsRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.showHiddenDirectories.collect { show ->
                _uiState.value = _uiState.value.copy(showHiddenDirectories = show)
            }
        }
        viewModelScope.launch {
            settingsRepository.autoScanOnStartup.collect { auto ->
                _uiState.value = _uiState.value.copy(autoScanOnStartup = auto)
            }
        }
    }

    fun checkPermissions(context: Context) {
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val hasInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        _uiState.value = _uiState.value.copy(
            hasStoragePermission = hasStorage,
            hasInstallPermission = hasInstall
        )
    }

    fun toggleShowHiddenDirectories(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHiddenDirectories(show)
        }
    }

    fun toggleAutoScan(auto: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoScanOnStartup(auto)
        }
    }
}
