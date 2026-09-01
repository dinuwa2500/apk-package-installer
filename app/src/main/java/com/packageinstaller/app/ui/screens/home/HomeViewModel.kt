package com.packageinstaller.app.ui.screens.home

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packageinstaller.app.PackageInstallerApplication
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.domain.usecase.InstallPackageUseCase
import com.packageinstaller.app.domain.usecase.ParsePackageUseCase
import com.packageinstaller.app.domain.usecase.ScanStorageUseCase
import com.packageinstaller.app.utils.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val recentPackages: List<PackageMetadata> = emptyList(),
    val totalPackagesFound: Int = 0,
    val availableStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val isScanning: Boolean = false,
    val hasStoragePermission: Boolean = true,
    val hasInstallPermission: Boolean = true,
    val selectedPackageForInspection: PackageMetadata? = null,
    val isInspectingUri: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val app = PackageInstallerApplication.instance
    private val scanUseCase: ScanStorageUseCase = app.scanStorageUseCase
    private val parseUseCase: ParsePackageUseCase = app.parsePackageUseCase
    private val installUseCase: InstallPackageUseCase = app.installPackageUseCase

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val installProgress = installUseCase.installProgress
    val installResult = installUseCase.lastResult

    init {
        loadStorageInfo()
        observeScanner()
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

    private fun loadStorageInfo() {
        val available = DeviceUtils.getAvailableStorageBytes()
        val total = DeviceUtils.getTotalStorageBytes()
        _uiState.value = _uiState.value.copy(
            availableStorageBytes = available,
            totalStorageBytes = total
        )
    }

    private fun observeScanner() {
        viewModelScope.launch {
            scanUseCase.scannedPackages.collect { packages ->
                _uiState.value = _uiState.value.copy(
                    recentPackages = packages.take(10),
                    totalPackagesFound = packages.size
                )
            }
        }
        viewModelScope.launch {
            scanUseCase.scanStats.collect { stats ->
                _uiState.value = _uiState.value.copy(
                    isScanning = stats.isScanning
                )
            }
        }
    }

    fun inspectPackage(metadata: PackageMetadata) {
        _uiState.value = _uiState.value.copy(selectedPackageForInspection = metadata)
    }

    fun inspectUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInspectingUri = true)
            val meta = parseUseCase.fromUri(uri)
            _uiState.value = _uiState.value.copy(
                isInspectingUri = false,
                selectedPackageForInspection = meta
            )
        }
    }

    fun inspectMultipleUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            inspectUri(uris.first())
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInspectingUri = true)
            val meta = parseUseCase.fromMultipleUris(uris)
            _uiState.value = _uiState.value.copy(
                isInspectingUri = false,
                selectedPackageForInspection = meta
            )
        }
    }

    fun dismissInspector() {
        _uiState.value = _uiState.value.copy(selectedPackageForInspection = null)
    }

    fun installPackage(metadata: PackageMetadata) {
        viewModelScope.launch {
            installUseCase.execute(metadata)
        }
    }

    fun cancelInstall() {
        installUseCase.cancel()
    }
}
