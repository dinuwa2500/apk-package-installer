package com.packageinstaller.app.ui.screens.browser

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packageinstaller.app.PackageInstallerApplication
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.domain.repository.FileBrowserManager
import com.packageinstaller.app.domain.repository.SettingsRepository
import com.packageinstaller.app.domain.usecase.InstallPackageUseCase
import com.packageinstaller.app.domain.usecase.ParsePackageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class BrowserFilterOption(val displayName: String) {
    ALL("All Files"),
    FOLDERS("Folders Only"),
    PACKAGES("All Packages"),
    APK("APK Only"),
    XAPK("XAPK Only"),
    APKS("APKS Only")
}

data class BrowserUiState(
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val items: List<StorageItem> = emptyList(),
    val filteredItems: List<StorageItem> = emptyList(),
    val showHiddenDirectories: Boolean = false,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val filterOption: BrowserFilterOption = BrowserFilterOption.ALL,
    val sortBy: SortOption = SortOption.NAME_ASC,
    val isMultiSelectMode: Boolean = false,
    val selectedFiles: Set<File> = emptySet(),
    val selectedPackageForInspection: PackageMetadata? = null,
    val isAnalyzing: Boolean = false
)

class FileBrowserViewModel : ViewModel() {

    private val app = PackageInstallerApplication.instance
    private val browserManager: FileBrowserManager = app.fileBrowserManager
    private val settingsRepository: SettingsRepository = app.settingsRepository
    private val parseUseCase: ParsePackageUseCase = app.parsePackageUseCase
    private val installUseCase: InstallPackageUseCase = app.installPackageUseCase

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val installProgress = installUseCase.installProgress
    val installResult = installUseCase.lastResult

    init {
        viewModelScope.launch {
            settingsRepository.showHiddenDirectories.collect { show ->
                _uiState.value = _uiState.value.copy(showHiddenDirectories = show)
                browserManager.setShowHiddenDirectories(show)
                loadDirectory(_uiState.value.currentDirectory)
            }
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                isLoading = true,
                selectedFiles = emptySet()
            )
            val items = browserManager.listDirectory(directory)
            val filtered = applyFilterAndSort(items, _uiState.value.searchQuery, _uiState.value.filterOption, _uiState.value.sortBy)
            _uiState.value = _uiState.value.copy(
                items = items,
                filteredItems = filtered,
                isLoading = false
            )
        }
    }

    fun toggleShowHiddenDirectories(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHiddenDirectories(show)
        }
    }

    fun updateSearchQuery(query: String) {
        val filtered = applyFilterAndSort(_uiState.value.items, query, _uiState.value.filterOption, _uiState.value.sortBy)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredItems = filtered
        )
    }

    fun updateFilterOption(filterOption: BrowserFilterOption) {
        val filtered = applyFilterAndSort(_uiState.value.items, _uiState.value.searchQuery, filterOption, _uiState.value.sortBy)
        _uiState.value = _uiState.value.copy(
            filterOption = filterOption,
            filteredItems = filtered
        )
    }

    fun updateSortOption(sortOption: SortOption) {
        val filtered = applyFilterAndSort(_uiState.value.items, _uiState.value.searchQuery, _uiState.value.filterOption, sortOption)
        _uiState.value = _uiState.value.copy(
            sortBy = sortOption,
            filteredItems = filtered
        )
    }

    private fun applyFilterAndSort(
        items: List<StorageItem>,
        query: String,
        filter: BrowserFilterOption,
        sort: SortOption
    ): List<StorageItem> {
        var result = items

        // Search query
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) }
        }

        // Category filter
        result = when (filter) {
            BrowserFilterOption.ALL -> result
            BrowserFilterOption.FOLDERS -> result.filter { it.isDirectory }
            BrowserFilterOption.PACKAGES -> result.filter { it.packageType != null }
            BrowserFilterOption.APK -> result.filter { it.packageType == PackageType.APK }
            BrowserFilterOption.XAPK -> result.filter { it.packageType == PackageType.XAPK }
            BrowserFilterOption.APKS -> result.filter { it.packageType == PackageType.APKS }
        }

        // Sort (keeping directories on top for navigation)
        result = when (sort) {
            SortOption.NAME_ASC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            SortOption.NAME_DESC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortOption.DATE_DESC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortOption.DATE_ASC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenBy { it.lastModified })
            SortOption.SIZE_DESC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenByDescending { it.sizeBytes })
            SortOption.SIZE_ASC -> result.sortedWith(compareBy<StorageItem> { !it.isDirectory }.thenBy { it.sizeBytes })
        }

        return result
    }

    fun toggleMultiSelectMode() {
        val newMode = !_uiState.value.isMultiSelectMode
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = newMode,
            selectedFiles = if (newMode) emptySet() else emptySet()
        )
    }

    fun toggleSelectFile(file: File) {
        val current = _uiState.value.selectedFiles.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _uiState.value = _uiState.value.copy(
            selectedFiles = current,
            isMultiSelectMode = current.isNotEmpty()
        )
    }

    fun selectAllApksInCurrentFolder() {
        val apks = _uiState.value.items.filter { it.packageType == PackageType.APK }.map { it.file }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedFiles = apks,
            isMultiSelectMode = apks.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedFiles = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun navigateUp(): Boolean {
        val parent = _uiState.value.currentDirectory.parentFile
        if (parent != null && parent.canRead()) {
            loadDirectory(parent)
            return true
        }
        return false
    }

    fun onItemClick(item: StorageItem) {
        if (_uiState.value.isMultiSelectMode) {
            if (!item.isDirectory) {
                toggleSelectFile(item.file)
            } else {
                loadDirectory(item.file)
            }
            return
        }

        if (item.isDirectory) {
            loadDirectory(item.file)
        } else if (item.packageType != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isAnalyzing = true)
                val meta = parseUseCase.fromFile(item.file)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    selectedPackageForInspection = meta
                )
            }
        }
    }

    fun inspectSelectedSplits() {
        val selected = _uiState.value.selectedFiles.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val meta = if (selected.size == 1) {
                parseUseCase.fromFile(selected.first())
            } else {
                parseUseCase.fromFiles(selected)
            }
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                selectedPackageForInspection = meta
            )
        }
    }

    fun installFolderAsSplits(directory: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val meta = parseUseCase.fromSplitDirectory(directory)
            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
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
