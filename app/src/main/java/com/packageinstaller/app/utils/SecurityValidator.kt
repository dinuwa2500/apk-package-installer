package com.packageinstaller.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.packageinstaller.app.domain.model.AppPermission
import com.packageinstaller.app.domain.model.SignatureInfo
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile

object SecurityValidator {

    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.READ_PHONE_STATE",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE"
    )

    fun mapPermission(name: String): AppPermission {
        return AppPermission(
            name = name,
            simpleName = name.substringAfterLast('.'),
            isDangerous = DANGEROUS_PERMISSIONS.contains(name)
        )
    }

    /**
     * Extracts X.509 signatures and SHA-256 fingerprints from an APK file
     */
    fun extractSignatures(apkFile: File): List<SignatureInfo> {
        val signatures = mutableListOf<SignatureInfo>()
        try {
            val jarFile = JarFile(apkFile)
            val manifestEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF")
            if (manifestEntry != null) {
                // Trigger certificate verification by reading an entry
                val buffer = ByteArray(8192)
                jarFile.getInputStream(manifestEntry).use { input ->
                    while (input.read(buffer) != -1) {
                        // reading to end
                    }
                }
                val certs = manifestEntry.certificates
                if (certs != null) {
                    for (cert in certs) {
                        if (cert is X509Certificate) {
                            val md = MessageDigest.getInstance("SHA-256")
                            val fingerprint = md.digest(cert.encoded)
                                .joinToString(":") { String.format("%02X", it) }
                            signatures.add(
                                SignatureInfo(
                                    sha256Fingerprint = fingerprint,
                                    issuer = cert.issuerDN.name,
                                    subject = cert.subjectDN.name,
                                    serialNumber = cert.serialNumber.toString(16)
                                )
                            )
                        }
                    }
                }
            }
            jarFile.close()
        } catch (e: Exception) {
            // fallback
        }
        return signatures
    }

    /**
     * Validates whether an existing package on device has matching signature
     */
    fun checkSignatureMatch(context: Context, packageName: String, apkFile: File): Boolean {
        try {
            val pm = context.packageManager
            val installedPackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            } ?: return true // App not installed, so no mismatch

            val archivePackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: return true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val installedSignatures = installedPackageInfo.signingInfo?.apkContentsSigners
                val archiveSignatures = archivePackageInfo.signingInfo?.apkContentsSigners
                if (installedSignatures != null && archiveSignatures != null) {
                    val installedDigest = MessageDigest.getInstance("SHA-256").digest(installedSignatures[0].toByteArray())
                    val archiveDigest = MessageDigest.getInstance("SHA-256").digest(archiveSignatures[0].toByteArray())
                    return installedDigest.contentEquals(archiveDigest)
                }
            } else {
                @Suppress("DEPRECATION")
                val installedSig = installedPackageInfo.signatures?.firstOrNull()
                @Suppress("DEPRECATION")
                val archiveSig = archivePackageInfo.signatures?.firstOrNull()
                if (installedSig != null && archiveSig != null) {
                    return installedSig == archiveSig
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return true // Not installed
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }
}
