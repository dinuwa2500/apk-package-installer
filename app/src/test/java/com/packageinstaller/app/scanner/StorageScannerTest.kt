package com.packageinstaller.app.scanner

import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.model.ScanFilter
import com.packageinstaller.app.domain.model.SortOption
import com.packageinstaller.app.domain.repository.PackageParser
import com.packageinstaller.app.domain.usecase.ScanStorageUseCase
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testHiddenDirectoryDiscovery() {
        // Build folder tree with hidden directories
        // root/
        //   Download/
        //     normal.apk
        //   .backup/
        //     apps/
        //       WhatsApp.xapk
        //   .downloads/
        //     games/
        //       game.apks
        //   .hidden/
        //     packages/
        //       application.apk

        val root = tempFolder.newFolder("storage_root")

        val downloadDir = File(root, "Download").also { it.mkdirs() }
        File(downloadDir, "normal.apk").writeText("fake apk")

        val backupDir = File(root, ".backup/apps").also { it.mkdirs() }
        File(backupDir, "WhatsApp.xapk").writeText("fake xapk")

        val dotDownloadsDir = File(root, ".downloads/games").also { it.mkdirs() }
        File(dotDownloadsDir, "game.apks").writeText("fake apks")

        val hiddenDir = File(root, ".hidden/packages").also { it.mkdirs() }
        File(hiddenDir, "application.apk").writeText("fake app")

        // Recursively traverse
        val foundFiles = mutableListOf<File>()
        fun scan(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    scan(f)
                } else {
                    foundFiles.add(f)
                }
            }
        }

        scan(root)

        assertEquals(4, foundFiles.size)
        val names = foundFiles.map { it.name }.toSet()
        assertTrue(names.contains("normal.apk"))
        assertTrue(names.contains("WhatsApp.xapk"))
        assertTrue(names.contains("game.apks"))
        assertTrue(names.contains("application.apk"))
    }

    @Test
    fun testScanFilterSearch() {
        val dummyList = listOf(
            PackageMetadata(
                id = "1",
                filePath = "/path/app1.apk",
                fileName = "app1.apk",
                fileSize = 1024 * 1024 * 10,
                packageType = PackageType.APK,
                appName = "WhatsApp Messenger",
                packageName = "com.whatsapp",
                versionName = "2.24.1",
                versionCode = 100,
                minSdk = 21,
                targetSdk = 34
            ),
            PackageMetadata(
                id = "2",
                filePath = "/path/game.xapk",
                fileName = "game.xapk",
                fileSize = 1024 * 1024 * 100,
                packageType = PackageType.XAPK,
                appName = "Asphalt 9",
                packageName = "com.gameloft.asphalt9",
                versionName = "4.0.0",
                versionCode = 200,
                minSdk = 24,
                targetSdk = 34
            )
        )

        val useCase = ScanStorageUseCase(mockk(relaxed = true))

        val filterQuery = ScanFilter(query = "WhatsApp")
        val filtered = useCase.applyFilter(dummyList, filterQuery)
        assertEquals(1, filtered.size)
        assertEquals("WhatsApp Messenger", filtered[0].appName)

        val filterType = ScanFilter(typeFilter = PackageType.XAPK)
        val filteredType = useCase.applyFilter(dummyList, filterType)
        assertEquals(1, filteredType.size)
        assertEquals(PackageType.XAPK, filteredType[0].packageType)

        val filterSort = ScanFilter(sortBy = SortOption.SIZE_DESC)
        val sorted = useCase.applyFilter(dummyList, filterSort)
        assertEquals("Asphalt 9", sorted[0].appName)
    }
}
