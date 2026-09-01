package com.packageinstaller.app.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

object DeviceUtils {

    fun getDeviceSupportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS.toList()
    }

    fun getDevicePrimaryAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    fun getDeviceDensityDpi(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }

    fun getDeviceDensityBucket(context: Context): String {
        return when (val dpi = getDeviceDensityDpi(context)) {
            in 0..120 -> "ldpi"
            in 121..160 -> "mdpi"
            in 161..240 -> "hdpi"
            in 241..320 -> "xhdpi"
            in 321..480 -> "xxhdpi"
            in 481..640 -> "xxxhdpi"
            else -> "xxhdpi"
        }
    }

    fun getDeviceLocales(): List<String> {
        val locales = mutableListOf<String>()
        val defaultLocale = Locale.getDefault()
        locales.add(defaultLocale.language)
        locales.add(defaultLocale.toLanguageTag().lowercase())
        return locales.distinct()
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun getAccessibleStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists() && primary.canRead()) {
                roots.add(primary)
            }
        } catch (e: Exception) {
            // ignore
        }

        // Add standard public directories
        val standardDirs = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM
        )

        for (type in standardDirs) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(type)
                if (dir != null && dir.exists() && dir.canRead() && !roots.contains(dir)) {
                    roots.add(dir)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        return roots.ifEmpty {
            listOf(Environment.getExternalStorageDirectory())
        }
    }
}
