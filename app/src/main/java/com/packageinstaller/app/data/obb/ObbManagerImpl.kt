package com.packageinstaller.app.data.obb

import android.content.Context
import android.os.Build
import android.os.Environment
import com.packageinstaller.app.domain.model.ObbFile
import com.packageinstaller.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class ObbManagerImpl(
    private val context: Context
) {

    /**
     * Extracts and deploys OBB files from a container archive to Android/obb/<package-name>/
     */
    suspend fun deployObbFiles(
        archiveFile: File,
        obbFiles: List<ObbFile>,
        onProgress: (progressFraction: Float, fileName: String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (obbFiles.isEmpty()) return@withContext Result.success(Unit)

        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val obbRoot = File(externalStorage, "Android/obb")

            ZipFile(archiveFile).use { zip ->
                var totalBytes = obbFiles.sumOf { it.sizeBytes }
                if (totalBytes <= 0L) totalBytes = 1L
                var overallCopiedBytes = 0L

                for (obb in obbFiles) {
                    val packageObbDir = File(obbRoot, obb.packageName)

                    // Ensure target directory exists
                    if (!packageObbDir.exists()) {
                        val created = packageObbDir.mkdirs()
                        if (!created && !packageObbDir.exists()) {
                            // Directory creation failed (likely Android 11+ scoped storage restriction)
                            return@withContext Result.failure(
                                SecurityException(
                                    "Cannot create directory: ${packageObbDir.absolutePath}. Android 11+ restricts direct access to Android/obb. Please grant All Files Access or use the SAF folder selector."
                                )
                            )
                        }
                    }

                    val destFile = File(packageObbDir, obb.filename)
                    val entry = zip.getEntry(obb.sourcePathInArchive)
                        ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(obb.filename, ignoreCase = true) }

                    if (entry == null) {
                        return@withContext Result.failure(
                            IllegalArgumentException("OBB entry '${obb.filename}' not found in archive")
                        )
                    }

                    zip.getInputStream(entry).use { inStream ->
                        FileOutputStream(destFile).use { outStream ->
                            val buffer = ByteArray(128 * 1024)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                                overallCopiedBytes += read
                                val fraction = (overallCopiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                onProgress(fraction, obb.filename)
                            }
                            outStream.flush()
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
