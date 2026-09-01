package com.packageinstaller.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.*
import java.text.DecimalFormat

object FileUtils {

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        val index = digitGroups.coerceIn(0, units.size - 1)
        return "${format.format(sizeBytes / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "unknown_package"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        if (name == "unknown_package") {
            uri.lastPathSegment?.let { segment ->
                name = segment.substringAfterLast('/')
            }
        }
        return name
    }

    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            return cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        return 0L
    }

    fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        destFile: File,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Boolean {
        return try {
            val totalBytes = getFileSizeFromUri(context, uri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    copyStreamWithProgress(input, output, totalBytes, onProgress)
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (bytesCopied: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Boolean {
        val buffer = ByteArray(64 * 1024) // 64KB buffer
        var bytesCopied = 0L
        var read: Int
        var lastReportedPercent = -1

        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            bytesCopied += read

            if (totalBytes > 0) {
                val percent = ((bytesCopied * 100) / totalBytes).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgress(bytesCopied, totalBytes)
                }
            } else {
                onProgress(bytesCopied, totalBytes)
            }
        }
        output.flush()
        return true
    }

    fun clearDirectory(dir: File) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    clearDirectory(file)
                }
                file.delete()
            }
        }
    }
}
