package com.packageinstaller.app.domain.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * Supported package container types
 */
enum class PackageType(val displayName: String, val extension: String) {
    APK("Standard APK", "apk"),
    XAPK("XAPK Bundle", "xapk"),
    APKS("APKS Archive", "apks"),
    SPLIT_SET("Split APK Set", "directory");

    companion object {
        fun fromExtension(ext: String): PackageType {
            return when (ext.lowercase()) {
                "apk" -> APK
                "xapk" -> XAPK
                "apks" -> APKS
                else -> APK
            }
        }

        fun fromFile(file: File): PackageType {
            if (file.isDirectory) return SPLIT_SET
            return fromExtension(file.extension)
        }
    }
}

/**
 * Information regarding a split APK component
 */
data class SplitItem(
    val name: String,
    val splitType: SplitType,
    val sizeBytes: Long,
    val file: File? = null,
    val uri: Uri? = null,
    val isRequiredForDevice: Boolean = true,
    val configDescription: String = ""
)

enum class SplitType {
    BASE,
    ABI,
    DENSITY,
    LANGUAGE,
    FEATURE,
    UNKNOWN
}

/**
 * Information regarding an OBB expansion file
 */
data class ObbFile(
    val filename: String,
    val packageName: String,
    val sizeBytes: Long,
    val isMain: Boolean, // true for main.X, false for patch.X
    val versionCode: Long,
    val sourcePathInArchive: String,
    val targetDirectory: String = "Android/obb/$packageName",
    val extractedFile: File? = null
)

/**
 * Signature and certificate information extracted from the package
 */
data class SignatureInfo(
    val sha256Fingerprint: String,
    val issuer: String = "",
    val subject: String = "",
    val serialNumber: String = "",
    val isValid: Boolean = true
)

/**
 * Permission requested by the package
 */
data class AppPermission(
    val name: String,
    val simpleName: String = name.substringAfterLast('.'),
    val isDangerous: Boolean = false,
    val description: String = ""
)

/**
 * Device Compatibility Status
 */
data class DeviceCompatibility(
    val isCompatible: Boolean,
    val issues: List<String> = emptyList(),
    val supportedAbis: List<String> = emptyList(),
    val packageAbis: List<String> = emptyList(),
    val minSdkMet: Boolean = true,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val currentSdk: Int = 0
)

/**
 * Complete metadata for a package (APK, XAPK, APKS, or Split Set)
 */
data class PackageMetadata(
    val id: String,
    val filePath: String,
    val fileUri: Uri? = null,
    val fileName: String,
    val fileSize: Long,
    val packageType: PackageType,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val iconBitmap: Bitmap? = null,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val splits: List<SplitItem> = emptyList(),
    val obbFiles: List<ObbFile> = emptyList(),
    val permissions: List<AppPermission> = emptyList(),
    val signatures: List<SignatureInfo> = emptyList(),
    val supportedAbis: List<String> = emptyList(),
    val compatibility: DeviceCompatibility? = null,
    val lastModified: Long = System.currentTimeMillis()
)
