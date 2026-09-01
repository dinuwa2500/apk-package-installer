package com.packageinstaller.app.data.parser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.utils.DeviceUtils
import com.packageinstaller.app.utils.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ApksParser(
    private val context: Context,
    private val apkParser: ApkParser
) {

    fun parseApksFile(apksFile: File): PackageMetadata? {
        if (!apksFile.exists() || !apksFile.canRead()) return null

        try {
            val splits = mutableListOf<SplitItem>()
            val supportedAbis = mutableListOf<String>()
            var tempBaseApk: File? = null

            ZipFile(apksFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val splitType = classifySplit(name)
                        if (splitType == SplitType.ABI) {
                            val abi = extractAbi(name)
                            if (abi != null && abi !in supportedAbis) {
                                supportedAbis.add(abi)
                            }
                        }

                        val isRequired = isRequiredForDevice(name, splitType)
                        splits.add(
                            SplitItem(
                                name = name,
                                splitType = splitType,
                                sizeBytes = entry.size.coerceAtLeast(entry.compressedSize),
                                isRequiredForDevice = isRequired,
                                configDescription = name
                            )
                        )

                        // Extract base APK temporarily to read rich metadata
                        if (tempBaseApk == null && (splitType == SplitType.BASE || name.contains("base-master") || name.contains("master.apk"))) {
                            val cacheDir = File(context.cacheDir, "apks_inspect").also { it.mkdirs() }
                            tempBaseApk = File(cacheDir, "temp_base_${System.currentTimeMillis()}.apk")
                            zip.getInputStream(entry).use { inStream ->
                                FileOutputStream(tempBaseApk).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                        }
                    }
                }
            }

            // Parse base metadata from extracted temporary base APK if available
            var parsedBaseInfo: PackageMetadata? = null
            if (tempBaseApk != null && tempBaseApk!!.exists()) {
                parsedBaseInfo = apkParser.parseApkFile(tempBaseApk!!)
                tempBaseApk?.delete()
            }

            val packageName = parsedBaseInfo?.packageName ?: apksFile.nameWithoutExtension
            val appName = parsedBaseInfo?.appName ?: apksFile.nameWithoutExtension
            val versionName = parsedBaseInfo?.versionName ?: "1.0"
            val versionCode = parsedBaseInfo?.versionCode ?: 1L
            val minSdk = parsedBaseInfo?.minSdk ?: 21
            val targetSdk = parsedBaseInfo?.targetSdk ?: 33
            val iconBitmap = parsedBaseInfo?.iconBitmap
            val permissions = parsedBaseInfo?.permissions ?: emptyList()

            // Check if installed
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
                    if (!hasMatchingAbi) add("No matching CPU architecture in APKS archive")
                }
            )

            return PackageMetadata(
                id = apksFile.absolutePath,
                filePath = apksFile.absolutePath,
                fileName = apksFile.name,
                fileSize = apksFile.length(),
                packageType = PackageType.APKS,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                iconBitmap = iconBitmap,
                isInstalled = isInstalled,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                splits = splits,
                obbFiles = emptyList(),
                permissions = permissions,
                signatures = emptyList(),
                supportedAbis = supportedAbis,
                compatibility = compatibility,
                lastModified = apksFile.lastModified()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun classifySplit(name: String): SplitType {
        val lower = name.lowercase()
        return when {
            lower.contains("master") || lower.contains("base-master") || (lower.contains("base") && !lower.contains("config")) -> SplitType.BASE
            lower.contains("arm64") || lower.contains("armeabi") || lower.contains("x86") -> SplitType.ABI
            lower.contains("dpi") || lower.contains("hdpi") || lower.contains("mdpi") || lower.contains("ldpi") -> SplitType.DENSITY
            lower.contains("lang") || lower.matches(Regex(".*-(en|es|fr|de|zh|ru|ar|hi|pt|ja|ko)\\.apk")) -> SplitType.LANGUAGE
            else -> SplitType.FEATURE
        }
    }

    private fun extractAbi(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.contains("arm64_v8a") || lower.contains("arm64-v8a") -> "arm64-v8a"
            lower.contains("armeabi_v7a") || lower.contains("armeabi-v7a") -> "armeabi-v7a"
            lower.contains("x86_64") -> "x86_64"
            lower.contains("x86") -> "x86"
            else -> null
        }
    }

    private fun isRequiredForDevice(name: String, splitType: SplitType): Boolean {
        when (splitType) {
            SplitType.BASE -> return true
            SplitType.ABI -> {
                val abi = extractAbi(name) ?: return true
                return abi in DeviceUtils.getDeviceSupportedAbis()
            }
            SplitType.DENSITY -> {
                val density = DeviceUtils.getDeviceDensityBucket(context)
                return name.lowercase().contains(density)
            }
            SplitType.LANGUAGE -> {
                val locales = DeviceUtils.getDeviceLocales()
                return locales.any { name.lowercase().contains(it) }
            }
            else -> return true
        }
    }
}
