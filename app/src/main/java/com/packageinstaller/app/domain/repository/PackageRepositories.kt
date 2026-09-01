package com.packageinstaller.app.domain.repository

import android.net.Uri
import com.packageinstaller.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Storage Scanner Repository
 */
interface StorageScanner {
    val scanStats: StateFlow<ScanStats>
    val scannedPackages: StateFlow<List<PackageMetadata>>
    
    /**
     * Recursively scans accessible device storage for .apk, .xapk, .apks, and split sets.
     * Note: Must traverse hidden directories starting with '.' (e.g. .backup, .downloads, .hidden).
     */
    suspend fun startScan(rootDirectories: List<File>? = null)
    fun stopScan()
    suspend fun rescanFile(file: File): PackageMetadata?
}

/**
 * Package Parser interface
 */
interface PackageParser {
    suspend fun parseFile(file: File): PackageMetadata?
    suspend fun parseFiles(files: List<File>): PackageMetadata?
    suspend fun parseUri(uri: Uri): PackageMetadata?
    suspend fun parseMultipleUris(uris: List<Uri>): PackageMetadata?
    suspend fun parseSplitDirectory(directory: File): PackageMetadata?
}

/**
 * Package Installer Manager
 */
interface PackageInstallerManager {
    val installProgress: StateFlow<InstallProgress>
    val lastResult: StateFlow<InstallResult?>

    suspend fun installPackage(
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit = {}
    ): InstallResult

    suspend fun installMultipleApks(
        apkFiles: List<File>,
        obbFiles: List<ObbFile> = emptyList(),
        onProgress: (InstallProgress) -> Unit = {}
    ): InstallResult

    fun cancelInstall()
}

/**
 * File Browser Manager for directory navigation
 */
interface FileBrowserManager {
    val showHiddenDirectories: StateFlow<Boolean>
    fun setShowHiddenDirectories(show: Boolean)
    suspend fun listDirectory(directory: File): List<StorageItem>
    suspend fun listDocumentTree(treeUri: Uri): List<StorageItem>
}

/**
 * User Settings Repository
 */
interface SettingsRepository {
    val showHiddenDirectories: Flow<Boolean>
    suspend fun setShowHiddenDirectories(show: Boolean)
    val autoScanOnStartup: Flow<Boolean>
    suspend fun setAutoScanOnStartup(enabled: Boolean)
    val themeMode: Flow<String> // "system", "dark", "light"
    suspend fun setThemeMode(mode: String)
}
