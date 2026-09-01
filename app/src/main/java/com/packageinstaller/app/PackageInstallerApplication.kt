package com.packageinstaller.app

import android.app.Application
import com.packageinstaller.app.data.installer.PackageInstallerManagerImpl
import com.packageinstaller.app.data.parser.CompositePackageParser
import com.packageinstaller.app.data.repository.SettingsRepositoryImpl
import com.packageinstaller.app.data.scanner.FileBrowserManagerImpl
import com.packageinstaller.app.data.scanner.StorageScannerImpl
import com.packageinstaller.app.domain.repository.*
import com.packageinstaller.app.domain.usecase.GetDeviceCompatUseCase
import com.packageinstaller.app.domain.usecase.InstallPackageUseCase
import com.packageinstaller.app.domain.usecase.ParsePackageUseCase
import com.packageinstaller.app.domain.usecase.ScanStorageUseCase

class PackageInstallerApplication : Application() {

    lateinit var packageParser: PackageParser
        private set
    lateinit var packageInstallerManager: PackageInstallerManager
        private set
    lateinit var storageScanner: StorageScanner
        private set
    lateinit var fileBrowserManager: FileBrowserManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var scanStorageUseCase: ScanStorageUseCase
        private set
    lateinit var parsePackageUseCase: ParsePackageUseCase
        private set
    lateinit var installPackageUseCase: InstallPackageUseCase
        private set
    lateinit var getDeviceCompatUseCase: GetDeviceCompatUseCase
        private set

    companion object {
        lateinit var instance: PackageInstallerApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        packageParser = CompositePackageParser(this)
        packageInstallerManager = PackageInstallerManagerImpl(this)
        storageScanner = StorageScannerImpl(this, packageParser)
        fileBrowserManager = FileBrowserManagerImpl(this, packageParser)
        settingsRepository = SettingsRepositoryImpl(this)

        scanStorageUseCase = ScanStorageUseCase(storageScanner)
        parsePackageUseCase = ParsePackageUseCase(packageParser)
        installPackageUseCase = InstallPackageUseCase(packageInstallerManager)
        getDeviceCompatUseCase = GetDeviceCompatUseCase()
    }
}
