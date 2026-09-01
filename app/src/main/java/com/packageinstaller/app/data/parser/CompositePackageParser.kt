package com.packageinstaller.app.data.parser

import android.content.Context
import android.net.Uri
import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.repository.PackageParser
import com.packageinstaller.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CompositePackageParser(
    private val context: Context,
    private val apkParser: ApkParser = ApkParser(context),
    private val xapkParser: XapkParser = XapkParser(context, apkParser),
    private val apksParser: ApksParser = ApksParser(context, apkParser),
    private val splitSetParser: SplitSetParser = SplitSetParser(context, apkParser)
) : PackageParser {

    override suspend fun parseFile(file: File): PackageMetadata? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        if (file.isDirectory) {
            return@withContext splitSetParser.parseDirectory(file)
        }

        when (file.extension.lowercase()) {
            "apk" -> apkParser.parseApkFile(file)
            "xapk" -> xapkParser.parseXapkFile(file)
            "apks" -> apksParser.parseApksFile(file)
            "zip" -> {
                // Check if it's an APKS or XAPK or Split ZIP
                val asXapk = xapkParser.parseXapkFile(file)
                if (asXapk != null) return@withContext asXapk

                val asApks = apksParser.parseApksFile(file)
                if (asApks != null) return@withContext asApks

                null
            }
            else -> null
        }
    }

    override suspend fun parseUri(uri: Uri): PackageMetadata? = withContext(Dispatchers.IO) {
        val fileName = FileUtils.getFileNameFromUri(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        // Copy stream into cache directory to inspect
        val cacheDir = File(context.cacheDir, "parsed_temp").also { it.mkdirs() }
        val tempFile = File(cacheDir, "temp_inspect_${System.currentTimeMillis()}.$ext")

        val copied = FileUtils.copyUriToTempFile(context, uri, tempFile)
        if (!copied || !tempFile.exists()) {
            tempFile.delete()
            return@withContext null
        }

        val metadata = parseFile(tempFile)?.copy(
            id = uri.toString(),
            fileUri = uri,
            fileName = fileName,
            filePath = tempFile.absolutePath
        )

        metadata
    }

    override suspend fun parseFiles(files: List<File>): PackageMetadata? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null
        if (files.size == 1) return@withContext parseFile(files.first())

        val displayName = files.first().nameWithoutExtension
        splitSetParser.parseApkFileList(files, displayName, files.first().parent ?: displayName)
    }

    override suspend fun parseMultipleUris(uris: List<Uri>): PackageMetadata? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        if (uris.size == 1) return@withContext parseUri(uris.first())

        val cacheDir = File(context.cacheDir, "parsed_splits_${System.currentTimeMillis()}").also { it.mkdirs() }
        val tempFiles = mutableListOf<File>()

        for (uri in uris) {
            val fileName = FileUtils.getFileNameFromUri(context, uri)
            val destFile = File(cacheDir, fileName)
            if (FileUtils.copyUriToTempFile(context, uri, destFile)) {
                tempFiles.add(destFile)
            }
        }

        if (tempFiles.isEmpty()) return@withContext null

        val displayName = FileUtils.getFileNameFromUri(context, uris.first()).substringBeforeLast('.')
        val metadata = splitSetParser.parseApkFileList(tempFiles, displayName, cacheDir.absolutePath)
        metadata
    }

    override suspend fun parseSplitDirectory(directory: File): PackageMetadata? = withContext(Dispatchers.IO) {
        splitSetParser.parseDirectory(directory)
    }
}
