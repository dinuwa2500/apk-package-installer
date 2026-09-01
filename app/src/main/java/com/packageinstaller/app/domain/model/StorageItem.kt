package com.packageinstaller.app.domain.model

import java.io.File

/**
 * Item in the storage / file browser
 */
data class StorageItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val isHidden: Boolean = file.name.startsWith("."),
    val sizeBytes: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val childCount: Int = 0,
    val packageType: PackageType? = if (file.isDirectory) null else PackageType.fromExtension(file.extension),
    val parsedMetadata: PackageMetadata? = null
)

/**
 * Filter options for scanning and searching packages
 */
data class ScanFilter(
    val query: String = "",
    val typeFilter: PackageType? = null,
    val onlyCompatible: Boolean = false,
    val sortBy: SortOption = SortOption.DATE_DESC
)

enum class SortOption(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

/**
 * Storage scan statistics
 */
data class ScanStats(
    val totalFilesScanned: Int = 0,
    val totalDirectoriesScanned: Int = 0,
    val hiddenDirectoriesScanned: Int = 0,
    val packagesFound: Int = 0,
    val isScanning: Boolean = false,
    val currentPath: String = ""
)
