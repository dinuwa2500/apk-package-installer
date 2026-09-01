package com.packageinstaller.app.data.parser

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.utils.DeviceUtils
import com.packageinstaller.app.utils.SecurityValidator
import java.io.File
import java.util.zip.ZipFile

class ApkParser(
    private val context: Context
) {

    fun parseApkFile(apkFile: File): PackageMetadata? {
        if (!apkFile.exists() || !apkFile.canRead()) return null

        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_CONFIGURATIONS or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)

            val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            val appInfo = packageInfo?.applicationInfo

            val packageName = packageInfo?.packageName ?: apkFile.nameWithoutExtension
            val versionName = packageInfo?.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 1L
            }

            var appName = apkFile.nameWithoutExtension
            var iconBitmap: Bitmap? = null

            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                try {
                    val label = appInfo.loadLabel(pm).toString()
                    if (label.isNotBlank() && !label.startsWith("com.")) {
                        appName = label
                    }
                } catch (e: Exception) {
                    // fallback
                }

                try {
                    val iconDrawable = appInfo.loadIcon(pm)
                    iconBitmap = drawableToBitmap(iconDrawable)
                } catch (e: Exception) {
                    // fallback
                }
            }

            // Extract Native ABIs & Split Info from ZIP entries
            val nativeAbis = mutableListOf<String>()
            var splitName: String? = null
            var isBaseApk = true

            try {
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (name.startsWith("lib/") && name.split("/").size > 2) {
                            val abi = name.split("/")[1]
                            if (abi !in nativeAbis) {
                                nativeAbis.add(abi)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback
            }

            // Check if installed
            var isInstalled = false
            var installedVersionName: String? = null
            var installedVersionCode: Long? = null
            try {
                val installed = pm.getPackageInfo(packageName, 0)
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

            // Permissions
            val permissions = packageInfo?.requestedPermissions?.map {
                SecurityValidator.mapPermission(it)
            } ?: emptyList()

            // Signatures
            val signatures = SecurityValidator.extractSignatures(apkFile)

            // Min & Target SDK
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion ?: 21
            } else {
                21
            }
            val targetSdk = appInfo?.targetSdkVersion ?: 33

            val splits = listOf(
                SplitItem(
                    name = apkFile.name,
                    splitType = SplitType.BASE,
                    sizeBytes = apkFile.length(),
                    file = apkFile,
                    isRequiredForDevice = true
                )
            )

            // Device compatibility
            val supportedAbis = DeviceUtils.getDeviceSupportedAbis()
            val hasCompatibleAbi = nativeAbis.isEmpty() || nativeAbis.any { it in supportedAbis }
            val minSdkMet = Build.VERSION.SDK_INT >= minSdk

            val compatibility = DeviceCompatibility(
                isCompatible = hasCompatibleAbi && minSdkMet,
                supportedAbis = supportedAbis,
                packageAbis = nativeAbis,
                minSdkMet = minSdkMet,
                minSdk = minSdk,
                targetSdk = targetSdk,
                currentSdk = Build.VERSION.SDK_INT,
                issues = buildList {
                    if (!minSdkMet) add("Requires Android SDK $minSdk (Device has ${Build.VERSION.SDK_INT})")
                    if (!hasCompatibleAbi) add("Incompatible ABIs: [${nativeAbis.joinToString()}]")
                }
            )

            return PackageMetadata(
                id = apkFile.absolutePath,
                filePath = apkFile.absolutePath,
                fileName = apkFile.name,
                fileSize = apkFile.length(),
                packageType = PackageType.APK,
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
                signatures = signatures,
                supportedAbis = nativeAbis,
                compatibility = compatibility,
                lastModified = apkFile.lastModified()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
