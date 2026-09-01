package com.packageinstaller.app.data.parser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.utils.DeviceUtils
import com.packageinstaller.app.utils.SecurityValidator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class XapkParser(
    private val context: Context,
    private val apkParser: ApkParser
) {

    fun parseXapkFile(xapkFile: File): PackageMetadata? {
        if (!xapkFile.exists() || !xapkFile.canRead()) return null

        try {
            ZipFile(xapkFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json")
                var manifestJson: JSONObject? = null
                if (manifestEntry != null) {
                    zip.getInputStream(manifestEntry).use { input ->
                        val text = input.bufferedReader().readText()
                        manifestJson = JSONObject(text)
                    }
                }

                // Parse App Details
                var packageName = manifestJson?.optString("package_name") ?: ""
                var appName = manifestJson?.optString("name") ?: ""
                var versionName = manifestJson?.optString("version_name") ?: ""
                var versionCode = manifestJson?.optLong("version_code") ?: 0L
                var minSdk = manifestJson?.optInt("min_sdk_version", 21) ?: 21
                var targetSdk = manifestJson?.optInt("target_sdk_version", 33) ?: 33

                // Extract Icon
                var iconBitmap: Bitmap? = null
                val iconEntry = zip.getEntry("icon.png") ?: zip.getEntry("icon.webp")
                if (iconEntry != null) {
                    zip.getInputStream(iconEntry).use { input ->
                        iconBitmap = BitmapFactory.decodeStream(input)
                    }
                }

                // Detect Splits and OBBs
                val splits = mutableListOf<SplitItem>()
                val obbFiles = mutableListOf<ObbFile>()
                val supportedAbis = mutableListOf<String>()

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name

                    // APK entries
                    if (entryName.endsWith(".apk", ignoreCase = true)) {
                        val splitType = classifySplit(entryName)
                        val isAbiSplit = splitType == SplitType.ABI
                        if (isAbiSplit) {
                            val abi = extractAbiFromSplitName(entryName)
                            if (abi != null && abi !in supportedAbis) {
                                supportedAbis.add(abi)
                            }
                        }

                        splits.add(
                            SplitItem(
                                name = entryName,
                                splitType = splitType,
                                sizeBytes = entry.size.coerceAtLeast(entry.compressedSize),
                                isRequiredForDevice = isRequiredForCurrentDevice(entryName, splitType),
                                configDescription = entryName
                            )
                        )
                    }

                    // OBB entries
                    if (entryName.endsWith(".obb", ignoreCase = true)) {
                        val fileName = entryName.substringAfterLast('/')
                        val isMain = fileName.startsWith("main.", ignoreCase = true)
                        obbFiles.add(
                            ObbFile(
                                filename = fileName,
                                packageName = packageName.ifEmpty { extractPackageFromObbName(fileName) },
                                sizeBytes = entry.size.coerceAtLeast(entry.compressedSize),
                                isMain = isMain,
                                versionCode = versionCode,
                                sourcePathInArchive = entryName
                            )
                        )
                    }
                }

                // If packageName was empty, look inside splits or OBB
                if (packageName.isEmpty()) {
                    val baseApkSplit = splits.firstOrNull { it.splitType == SplitType.BASE } ?: splits.firstOrNull()
                    if (baseApkSplit != null) {
                        packageName = baseApkSplit.name.removeSuffix(".apk")
                    }
                }
                if (appName.isEmpty()) {
                    appName = xapkFile.nameWithoutExtension
                }

                // Check installation status
                var isInstalled = false
                var installedVersionName: String? = null
                var installedVersionCode: Long? = null
                try {
                    val installed = context.packageManager.getPackageInfo(packageName, 0)
                    isInstalled = true
                    installedVersionName = installed.versionName
                    installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        installed.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        installed.versionCode.toLong()
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    isInstalled = false
                }

                // Permissions from manifest.json
                val permissions = mutableListOf<AppPermission>()
                val permsArray = manifestJson?.optJSONArray("permissions")
                if (permsArray != null) {
                    for (i in 0 until permsArray.length()) {
                        val permName = permsArray.optString(i)
                        if (permName.isNotBlank()) {
                            permissions.add(SecurityValidator.mapPermission(permName))
                        }
                    }
                }

                // Compatibility Check
                val deviceAbis = DeviceUtils.getDeviceSupportedAbis()
                val hasMatchingAbi = supportedAbis.isEmpty() || supportedAbis.any { it in deviceAbis }
                val minSdkMet = Build.VERSION.SDK_INT >= minSdk

                val compatibility = DeviceCompatibility(
                    isCompatible = hasMatchingAbi && minSdkMet,
                    supportedAbis = deviceAbis,
                    packageAbis = supportedAbis,
                    minSdkMet = minSdkMet,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    currentSdk = Build.VERSION.SDK_INT,
                    issues = buildList {
                        if (!minSdkMet) add("Requires Android SDK $minSdk (Device has ${Build.VERSION.SDK_INT})")
                        if (!hasMatchingAbi) add("No matching CPU architecture found in XAPK bundle")
                    }
                )

                return PackageMetadata(
                    id = xapkFile.absolutePath,
                    filePath = xapkFile.absolutePath,
                    fileName = xapkFile.name,
                    fileSize = xapkFile.length(),
                    packageType = PackageType.XAPK,
                    appName = appName,
                    packageName = packageName,
                    versionName = versionName.ifEmpty { "1.0" },
                    versionCode = versionCode.coerceAtLeast(1L),
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    iconBitmap = iconBitmap,
                    isInstalled = isInstalled,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    splits = splits,
                    obbFiles = obbFiles,
                    permissions = permissions,
                    signatures = emptyList(),
                    supportedAbis = supportedAbis,
                    compatibility = compatibility,
                    lastModified = xapkFile.lastModified()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun classifySplit(name: String): SplitType {
        val lower = name.lowercase()
        return when {
            lower.contains("base") || !lower.contains("config") -> SplitType.BASE
            lower.contains("arm64") || lower.contains("armeabi") || lower.contains("x86") -> SplitType.ABI
            lower.contains("dpi") || lower.contains("hdpi") || lower.contains("mdpi") || lower.contains("ldpi") -> SplitType.DENSITY
            lower.contains("config.") && (lower.endsWith(".en.apk") || lower.contains("lang")) -> SplitType.LANGUAGE
            else -> SplitType.FEATURE
        }
    }

    private fun extractAbiFromSplitName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.contains("arm64_v8a") || lower.contains("arm64-v8a") -> "arm64-v8a"
            lower.contains("armeabi_v7a") || lower.contains("armeabi-v7a") -> "armeabi-v7a"
            lower.contains("x86_64") -> "x86_64"
            lower.contains("x86") -> "x86"
            else -> null
        }
    }

    private fun extractPackageFromObbName(name: String): String {
        // Example: main.123.com.example.app.obb
        val parts = name.split(".")
        return if (parts.size >= 4) {
            parts.subList(2, parts.size - 1).joinToString(".")
        } else {
            ""
        }
    }

    private fun isRequiredForCurrentDevice(name: String, splitType: SplitType): Boolean {
        when (splitType) {
            SplitType.BASE -> return true
            SplitType.ABI -> {
                val abi = extractAbiFromSplitName(name) ?: return true
                return abi in DeviceUtils.getDeviceSupportedAbis()
            }
            SplitType.DENSITY -> {
                val deviceDensity = DeviceUtils.getDeviceDensityBucket(context)
                return name.lowercase().contains(deviceDensity)
            }
            SplitType.LANGUAGE -> {
                val locales = DeviceUtils.getDeviceLocales()
                return locales.any { name.lowercase().contains(it) }
            }
            else -> return true
        }
    }
}
