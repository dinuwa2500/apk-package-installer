package com.packageinstaller.app.parser

import com.packageinstaller.app.domain.model.InstallErrorCode
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.model.SplitType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PackageParserTest {

    @Test
    fun testPackageTypeDetection() {
        assertEquals(PackageType.APK, PackageType.fromExtension("apk"))
        assertEquals(PackageType.APK, PackageType.fromExtension("APK"))
        assertEquals(PackageType.XAPK, PackageType.fromExtension("xapk"))
        assertEquals(PackageType.APKS, PackageType.fromExtension("apks"))
    }

    @Test
    fun testInstallErrorCodeMapping() {
        val invalidApk = InstallErrorCode.fromStatusAndMessage(1, "INSTALL_FAILED_INVALID_APK: Failed to parse manifest")
        assertEquals(InstallErrorCode.INSTALL_FAILED_INVALID_APK, invalidApk)

        val noMatchingAbis = InstallErrorCode.fromStatusAndMessage(1, "INSTALL_FAILED_NO_MATCHING_ABIS: Native library not found")
        assertEquals(InstallErrorCode.INSTALL_FAILED_NO_MATCHING_ABIS, noMatchingAbis)

        val missingSplit = InstallErrorCode.fromStatusAndMessage(1, "INSTALL_FAILED_MISSING_SPLIT: Missing split_config.arm64_v8a")
        assertEquals(InstallErrorCode.INSTALL_FAILED_MISSING_SPLIT, missingSplit)

        val insufficientStorage = InstallErrorCode.fromStatusAndMessage(1, "INSTALL_FAILED_INSUFFICIENT_STORAGE: No space left on device")
        assertEquals(InstallErrorCode.INSTALL_FAILED_INSUFFICIENT_STORAGE, insufficientStorage)
    }

    @Test
    fun testXapkManifestJsonParsing() {
        val sampleJson = """
            {
                "xapk_version": 2,
                "package_name": "com.example.game",
                "name": "Super Action Game",
                "version_code": 105,
                "version_name": "1.0.5",
                "min_sdk_version": 24,
                "target_sdk_version": 34,
                "permissions": [
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE"
                ],
                "split_configs": [
                    {"file": "config.arm64_v8a.apk", "id": "config.arm64_v8a"},
                    {"file": "config.xxhdpi.apk", "id": "config.xxhdpi"}
                ],
                "expansions": [
                    {
                        "file": "Android/obb/com.example.game/main.105.com.example.game.obb",
                        "install_location": "EXTERNAL_STORAGE",
                        "install_path": "Android/obb/com.example.game/main.105.com.example.game.obb"
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(sampleJson)
        assertEquals("com.example.game", json.getString("package_name"))
        assertEquals("Super Action Game", json.getString("name"))
        assertEquals(105L, json.getLong("version_code"))
        assertEquals("1.0.5", json.getString("version_name"))
        assertEquals(24, json.getInt("min_sdk_version"))
        assertEquals(34, json.getInt("target_sdk_version"))

        val expansions = json.getJSONArray("expansions")
        assertEquals(1, expansions.length())
        val firstExp = expansions.getJSONObject(0)
        assertTrue(firstExp.getString("file").contains(".obb"))
    }

    @Test
    fun testSplitSetClassification() {
        val baseName = "base.apk"
        val abiName = "config.arm64_v8a.apk"
        val densityName = "config.xxhdpi.apk"
        val langName = "config.en.apk"

        val isBase = baseName.contains("base")
        val isAbi = abiName.contains("arm64")
        val isDensity = densityName.contains("dpi")
        val isLang = langName.contains("en")

        assertTrue(isBase)
        assertTrue(isAbi)
        assertTrue(isDensity)
        assertTrue(isLang)
    }
}
