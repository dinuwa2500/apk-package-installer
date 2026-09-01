package com.packageinstaller.app.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packageinstaller.app.PackageInstallerApplication
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.domain.usecase.InstallPackageUseCase
import com.packageinstaller.app.domain.usecase.ScanStorageUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScannerUiState(
    val scanStats: ScanStats = ScanStats(),
    val packages: List<PackageMetadata> = emptyList(),
    val filteredPackages: List<PackageMetadata> = emptyList(),
    val filter: ScanFilter = ScanFilter(),
    val selectedPackageForInspection: PackageMetadata? = null
)

class ScannerViewModel : ViewModel() {

    private val app = PackageInstallerApplication.instance
    private val scanUseCase: ScanStorageUseCase = app.scanStorageUseCase
    private val installUseCase: InstallPackageUseCase = app.installPackageUseCase

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val installProgress = installUseCase.installProgress
    val installResult = installUseCase.lastResult

    init {
        observeScanner()
    }

    private fun observeScanner() {
        viewModelScope.launch {
            scanUseCase.scanStats.collect { stats ->
                _uiState.value = _uiState.value.copy(scanStats = stats)
            }
        }

        viewModelScope.launch {
            scanUseCase.scannedPackages.collect { packages ->
                val currentFilter = _uiState.value.filter
                val filtered = scanUseCase.applyFilter(packages, currentFilter)
                _uiState.value = _uiState.value.copy(
                    packages = packages,
                    filteredPackages = filtered
                )
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            scanUseCase.startScan()
        }
    }

    fun stopScan() {
        scanUseCase.stopScan()
    }

    fun updateSearchQuery(query: String) {
        val newFilter = _uiState.value.filter.copy(query = query)
        applyNewFilter(newFilter)
    }

    fun updateTypeFilter(type: PackageType?) {
        val newFilter = _uiState.value.filter.copy(typeFilter = type)
        applyNewFilter(newFilter)
    }

    fun updateSortOption(sort: SortOption) {
        val newFilter = _uiState.value.filter.copy(sortBy = sort)
        applyNewFilter(newFilter)
    }

    private fun applyNewFilter(filter: ScanFilter) {
        val filtered = scanUseCase.applyFilter(_uiState.value.packages, filter)
        _uiState.value = _uiState.value.copy(
            filter = filter,
            filteredPackages = filtered
        )
    }

    fun inspectPackage(metadata: PackageMetadata) {
        _uiState.value = _uiState.value.copy(selectedPackageForInspection = metadata)
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
