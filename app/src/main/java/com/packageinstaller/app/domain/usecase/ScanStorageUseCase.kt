package com.packageinstaller.app.domain.usecase

import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.model.ScanFilter
import com.packageinstaller.app.domain.model.SortOption
import com.packageinstaller.app.domain.repository.StorageScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScanStorageUseCase(
    private val scanner: StorageScanner
) {
    val scanStats = scanner.scanStats
    val scannedPackages = scanner.scannedPackages

    suspend fun startScan() {
        scanner.startScan()
    }

    fun stopScan() {
        scanner.stopScan()
    }

    fun getFilteredPackages(filterFlow: Flow<ScanFilter>): Flow<List<PackageMetadata>> {
        return scanner.scannedPackages.map { list ->
            list
        }
    }

    fun applyFilter(packages: List<PackageMetadata>, filter: ScanFilter): List<PackageMetadata> {
        var result = packages

        // Query filter
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim().lowercase()
            result = result.filter {
                it.appName.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.fileName.lowercase().contains(q)
            }
        }

        // Type filter
        if (filter.typeFilter != null) {
            result = result.filter { it.packageType == filter.typeFilter }
        }

        // Compatibility filter
        if (filter.onlyCompatible) {
            result = result.filter { it.compatibility?.isCompatible != false }
        }

        // Sort
        result = when (filter.sortBy) {
            SortOption.DATE_DESC -> result.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> result.sortedBy { it.lastModified }
            SortOption.NAME_ASC -> result.sortedBy { it.appName.lowercase() }
            SortOption.NAME_DESC -> result.sortedByDescending { it.appName.lowercase() }
            SortOption.SIZE_DESC -> result.sortedByDescending { it.fileSize }
            SortOption.SIZE_ASC -> result.sortedBy { it.fileSize }
        }

        return result
    }
}
