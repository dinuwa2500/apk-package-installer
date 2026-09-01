package com.packageinstaller.app.domain.usecase

import com.packageinstaller.app.domain.model.InstallProgress
import com.packageinstaller.app.domain.model.InstallResult
import com.packageinstaller.app.domain.model.ObbFile
import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.repository.PackageInstallerManager
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class InstallPackageUseCase(
    private val installer: PackageInstallerManager
) {
    val installProgress: StateFlow<InstallProgress> = installer.installProgress
    val lastResult: StateFlow<InstallResult?> = installer.lastResult

    suspend fun execute(
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return installer.installPackage(metadata, onProgress)
    }

    suspend fun executeMultiple(
        apkFiles: List<File>,
        obbFiles: List<ObbFile> = emptyList(),
        onProgress: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return installer.installMultipleApks(apkFiles, obbFiles, onProgress)
    }

    fun cancel() {
        installer.cancelInstall()
    }
}
