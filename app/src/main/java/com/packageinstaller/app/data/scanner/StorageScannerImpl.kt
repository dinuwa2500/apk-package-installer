package com.packageinstaller.app.data.scanner

import android.content.Context
import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.model.ScanStats
import com.packageinstaller.app.domain.repository.PackageParser
import com.packageinstaller.app.domain.repository.StorageScanner
import com.packageinstaller.app.utils.DeviceUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import java.io.File

class StorageScannerImpl(
    private val context: Context,
    private val parser: PackageParser
) : StorageScanner {

    private val _scanStats = MutableStateFlow(ScanStats())
    override val scanStats: StateFlow<ScanStats> = _scanStats.asStateFlow()

    private val _scannedPackages = MutableStateFlow<List<PackageMetadata>>(emptyList())
    override val scannedPackages: StateFlow<List<PackageMetadata>> = _scannedPackages.asStateFlow()

    private var scanJob: Job? = null

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("apk", "xapk", "apks")
        // Excluded system / cache directories to avoid scanning internal OS cache
        val EXCLUDED_DIR_NAMES = setOf("node_modules", ".gradle", "lost+found")
    }

    override suspend fun startScan(rootDirectories: List<File>?) {
        stopScan()

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            _scanStats.value = ScanStats(isScanning = true)
            val packages = mutableListOf<PackageMetadata>()
            val roots = rootDirectories ?: DeviceUtils.getAccessibleStorageRoots()

            var filesCount = 0
            var dirsCount = 0
            var hiddenDirsCount = 0

            for (root in roots) {
                if (!isActive) break
                if (root.exists() && root.canRead()) {
                    traverseDirectory(
                        dir = root,
                        onDirectoryVisited = { dir, isHidden ->
                            dirsCount++
                            if (isHidden) hiddenDirsCount++
                            _scanStats.value = _scanStats.value.copy(
                                totalDirectoriesScanned = dirsCount,
                                hiddenDirectoriesScanned = hiddenDirsCount,
                                currentPath = dir.path
                            )
                        },
                        onFileVisited = { file ->
                            filesCount++
                            if (filesCount % 20 == 0) {
                                _scanStats.value = _scanStats.value.copy(
                                    totalFilesScanned = filesCount
                                )
                            }
                        },
                        onPackageFound = { meta ->
                            packages.add(meta)
                            _scannedPackages.value = packages.toList()
                            _scanStats.value = _scanStats.value.copy(
                                packagesFound = packages.size
                            )
                        }
                    )
                }
            }

            _scanStats.value = _scanStats.value.copy(
                isScanning = false,
                totalFilesScanned = filesCount,
                totalDirectoriesScanned = dirsCount,
                hiddenDirectoriesScanned = hiddenDirsCount,
                packagesFound = packages.size,
                currentPath = ""
            )
        }
    }

    /**
     * Recursively traverses directory.
     * CRITICAL: Dot-prefixed directories (e.g. .backup, .downloads, .hidden, .apps)
     * are NOT skipped and are scanned recursively!
     */
    private suspend fun traverseDirectory(
        dir: File,
        onDirectoryVisited: (File, Boolean) -> Unit,
        onFileVisited: (File) -> Unit,
        onPackageFound: (PackageMetadata) -> Unit
    ) {
        val isHidden = dir.name.startsWith(".")
        onDirectoryVisited(dir, isHidden)

        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return

        val childDirs = mutableListOf<File>()

        for (file in files) {
            if (!coroutineContext.isActive) return

            if (file.isDirectory) {
                // Do not skip hidden directories!
                // Only skip system-level corrupted or build artifacts if needed
                if (file.name !in EXCLUDED_DIR_NAMES && !file.name.equals("Android/data", ignoreCase = true)) {
                    childDirs.add(file)
                }
            } else if (file.isFile) {
                onFileVisited(file)
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val metadata = parser.parseFile(file)
                    if (metadata != null) {
                        onPackageFound(metadata)
                    }
                }
            }
        }

        // Recursively traverse all subdirectories including hidden ones (.<folder-name>)
        for (subDir in childDirs) {
            if (!coroutineContext.isActive) return
            traverseDirectory(subDir, onDirectoryVisited, onFileVisited, onPackageFound)
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanStats.value = _scanStats.value.copy(isScanning = false)
    }

    override suspend fun rescanFile(file: File): PackageMetadata? = withContext(Dispatchers.IO) {
        val meta = parser.parseFile(file)
        if (meta != null) {
            val current = _scannedPackages.value.toMutableList()
            val index = current.indexOfFirst { it.filePath == file.absolutePath }
            if (index != -1) {
                current[index] = meta
            } else {
                current.add(0, meta)
            }
            _scannedPackages.value = current
        }
        meta
    }
}
