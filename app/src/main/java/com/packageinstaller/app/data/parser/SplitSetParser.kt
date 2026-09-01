package com.packageinstaller.app.data.parser

import android.content.Context
import com.packageinstaller.app.domain.model.*
import java.io.File

class SplitSetParser(
    private val context: Context,
    private val apkParser: ApkParser
) {

    fun parseDirectory(directory: File): PackageMetadata? {
        if (!directory.exists() || !directory.isDirectory) return null

        val apkFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("apk", ignoreCase = true)
        }?.toList() ?: return null

        if (apkFiles.isEmpty()) return null

        return parseApkFileList(apkFiles, directory.name, directory.absolutePath)
    }

    fun parseApkFileList(apkFiles: List<File>, displayName: String, identifier: String): PackageMetadata? {
        if (apkFiles.isEmpty()) return null

        // Parse each APK to find the Base APK
        var baseMetadata: PackageMetadata? = null
        val splits = mutableListOf<SplitItem>()
        var totalSize = 0L

        for (file in apkFiles) {
            totalSize += file.length()
            val parsed = apkParser.parseApkFile(file)
            val isBase = file.name.contains("base", ignoreCase = true) || (parsed?.appName?.isNotBlank() == true && !file.name.contains("config", ignoreCase = true))

            if (isBase && baseMetadata == null) {
                baseMetadata = parsed
            }

            val splitType = when {
                isBase -> SplitType.BASE
                file.name.contains("arm") || file.name.contains("x86") -> SplitType.ABI
                file.name.contains("dpi") -> SplitType.DENSITY
                file.name.contains("lang") -> SplitType.LANGUAGE
                else -> SplitType.FEATURE
            }

            splits.add(
                SplitItem(
                    name = file.name,
                    splitType = splitType,
                    sizeBytes = file.length(),
                    file = file,
                    isRequiredForDevice = true,
                    configDescription = file.name
                )
            )
        }

        // Fallback to first parsed if no base identified
        if (baseMetadata == null) {
            baseMetadata = apkParser.parseApkFile(apkFiles.first())
        }

        val packageName = baseMetadata?.packageName ?: displayName
        val appName = baseMetadata?.appName ?: displayName

        return PackageMetadata(
            id = identifier,
            filePath = identifier,
            fileName = displayName,
            fileSize = totalSize,
            packageType = PackageType.SPLIT_SET,
            appName = appName,
            packageName = packageName,
            versionName = baseMetadata?.versionName ?: "1.0",
            versionCode = baseMetadata?.versionCode ?: 1L,
            minSdk = baseMetadata?.minSdk ?: 21,
            targetSdk = baseMetadata?.targetSdk ?: 33,
            iconBitmap = baseMetadata?.iconBitmap,
            isInstalled = baseMetadata?.isInstalled ?: false,
            installedVersionName = baseMetadata?.installedVersionName,
            installedVersionCode = baseMetadata?.installedVersionCode,
            splits = splits,
            obbFiles = emptyList(),
            permissions = baseMetadata?.permissions ?: emptyList(),
            signatures = baseMetadata?.signatures ?: emptyList(),
            supportedAbis = baseMetadata?.supportedAbis ?: emptyList(),
            compatibility = baseMetadata?.compatibility,
            lastModified = apkFiles.maxOfOrNull { it.lastModified() } ?: System.currentTimeMillis()
        )
    }
}
