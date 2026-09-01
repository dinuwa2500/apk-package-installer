package com.packageinstaller.app.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.model.StorageItem
import com.packageinstaller.app.domain.repository.FileBrowserManager
import com.packageinstaller.app.domain.repository.PackageParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class FileBrowserManagerImpl(
    private val context: Context,
    private val parser: PackageParser
) : FileBrowserManager {

    private val _showHiddenDirectories = MutableStateFlow(false)
    override val showHiddenDirectories: StateFlow<Boolean> = _showHiddenDirectories.asStateFlow()

    override fun setShowHiddenDirectories(show: Boolean) {
        _showHiddenDirectories.value = show
    }

    override suspend fun listDirectory(directory: File): List<StorageItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val showHidden = _showHiddenDirectories.value
        val files = directory.listFiles() ?: return@withContext emptyList()

        val items = mutableListOf<StorageItem>()

        for (file in files) {
            val isHidden = file.name.startsWith(".")
            if (isHidden && !showHidden) {
                continue
            }

            val isDirectory = file.isDirectory
            val ext = file.extension.lowercase()
            val isPackage = !isDirectory && ext in setOf("apk", "xapk", "apks", "zip")

            val childCount = if (isDirectory) {
                try { file.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
            } else 0

            items.add(
                StorageItem(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDirectory,
                    isHidden = isHidden,
                    sizeBytes = if (isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    childCount = childCount,
                    packageType = if (isPackage) PackageType.fromExtension(ext) else null
                )
            )
        }

        // Sort: Folders first, then alphabetically
        items.sortedWith(
            compareBy<StorageItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    override suspend fun listDocumentTree(treeUri: Uri): List<StorageItem> = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val showHidden = _showHiddenDirectories.value
        val items = mutableListOf<StorageItem>()

        val files = documentFile.listFiles()
        for (doc in files) {
            val name = doc.name ?: "unknown"
            val isHidden = name.startsWith(".")
            if (isHidden && !showHidden) continue

            val isDir = doc.isDirectory
            val ext = name.substringAfterLast('.', "").lowercase()
            val isPkg = !isDir && ext in setOf("apk", "xapk", "apks")

            items.add(
                StorageItem(
                    file = File(name),
                    name = name,
                    path = doc.uri.toString(),
                    isDirectory = isDir,
                    isHidden = isHidden,
                    sizeBytes = doc.length(),
                    lastModified = doc.lastModified(),
                    childCount = 0,
                    packageType = if (isPkg) PackageType.fromExtension(ext) else null
                )
            )
        }

        items.sortedWith(
            compareBy<StorageItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }
}
