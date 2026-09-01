package com.packageinstaller.app.data.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.packageinstaller.app.data.obb.ObbManagerImpl
import com.packageinstaller.app.domain.model.*
import com.packageinstaller.app.domain.repository.PackageInstallerManager
import com.packageinstaller.app.utils.FileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile

class PackageInstallerManagerImpl(
    private val context: Context,
    private val obbManager: ObbManagerImpl = ObbManagerImpl(context)
) : PackageInstallerManager {

    private val _installProgress = MutableStateFlow(InstallProgress())
    override val installProgress: StateFlow<InstallProgress> = _installProgress.asStateFlow()

    private val _lastResult = MutableStateFlow<InstallResult?>(null)
    override val lastResult: StateFlow<InstallResult?> = _lastResult.asStateFlow()

    private var currentSessionId: Int = -1
    private var installJob: Job? = null

    init {
        // Collect results from broadcast receiver
        CoroutineScope(Dispatchers.Main).launch {
            PackageInstallStatusReceiver.installEvents.collect { result ->
                _lastResult.value = result
                when (result) {
                    is InstallResult.Success -> {
                        _installProgress.value = InstallProgress(
                            stage = InstallStage.COMPLETED,
                            progressFraction = 1f,
                            statusDetail = "Installed ${result.appName} successfully!"
                        )
                    }
                    is InstallResult.Failure -> {
                        _installProgress.value = InstallProgress(
                            stage = InstallStage.FAILED,
                            progressFraction = 0f,
                            statusDetail = result.errorCode.userMessage
                        )
                    }
                    is InstallResult.UserActionRequired -> {
                        _installProgress.value = InstallProgress(
                            stage = InstallStage.WAITING_CONFIRMATION,
                            progressFraction = 0.9f,
                            statusDetail = "Awaiting user confirmation..."
                        )
                    }
                }
            }
        }
    }

    override suspend fun installPackage(
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        _lastResult.value = null
        val packageFile = File(metadata.filePath)

        if (!packageFile.exists()) {
            val failure = InstallResult.Failure(
                errorCode = InstallErrorCode.INSTALL_FAILED_INVALID_APK,
                technicalMessage = "File not found: ${metadata.filePath}",
                packageName = metadata.packageName,
                appName = metadata.appName
            )
            _lastResult.value = failure
            return@withContext failure
        }

        // Check Unknown Sources Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val failure = InstallResult.Failure(
                    errorCode = InstallErrorCode.INSTALL_FAILED_UNKNOWN_SOURCES_DISABLED,
                    technicalMessage = "REQUEST_INSTALL_PACKAGES permission not granted",
                    packageName = metadata.packageName,
                    appName = metadata.appName
                )
                _lastResult.value = failure
                return@withContext failure
            }
        }

        updateProgress(
            InstallStage.VALIDATING,
            0.05f,
            "Validating ${metadata.appName}...",
            onProgress
        )

        try {
            when (metadata.packageType) {
                PackageType.APK -> {
                    installApkDirect(packageFile, metadata, onProgress)
                }
                PackageType.XAPK -> {
                    installXapk(packageFile, metadata, onProgress)
                }
                PackageType.APKS -> {
                    installApks(packageFile, metadata, onProgress)
                }
                PackageType.SPLIT_SET -> {
                    val apkFiles = metadata.splits.mapNotNull { it.file }
                    installMultipleApks(apkFiles, emptyList(), onProgress)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val failure = InstallResult.Failure(
                errorCode = InstallErrorCode.fromStatusAndMessage(-1, e.message),
                technicalMessage = e.localizedMessage ?: e.toString(),
                packageName = metadata.packageName,
                appName = metadata.appName
            )
            _lastResult.value = failure
            failure
        }
    }

    private suspend fun installApkDirect(
        apkFile: File,
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        return createAndCommitSession(
            packageName = metadata.packageName,
            appName = metadata.appName,
            apkStreams = listOf("base.apk" to FileInputStream(apkFile)),
            totalSize = apkFile.length(),
            onProgress = onProgress
        )
    }

    private suspend fun installXapk(
        xapkFile: File,
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        // Step 1: Deploy OBB expansions if present
        if (metadata.obbFiles.isNotEmpty()) {
            updateProgress(
                InstallStage.DEPLOYING_OBB,
                0.15f,
                "Deploying expansion files to Android/obb...",
                onProgress
            )
            val obbResult = obbManager.deployObbFiles(xapkFile, metadata.obbFiles) { fraction, obbName ->
                updateProgress(
                    InstallStage.DEPLOYING_OBB,
                    0.15f + (fraction * 0.25f),
                    "Deploying OBB: $obbName",
                    onProgress
                )
            }
            if (obbResult.isFailure) {
                val err = obbResult.exceptionOrNull()
                return InstallResult.Failure(
                    errorCode = InstallErrorCode.INSTALL_FAILED_OBB_PERMISSION_DENIED,
                    technicalMessage = err?.message ?: "OBB deployment failed",
                    packageName = metadata.packageName,
                    appName = metadata.appName
                )
            }
        }

        // Step 2: Extract and stream APKs into PackageInstaller Session
        updateProgress(
            InstallStage.EXTRACTING,
            0.40f,
            "Extracting APK split components...",
            onProgress
        )

        val tempApkFiles = mutableListOf<File>()
        val tempDir = File(context.cacheDir, "xapk_staging_${System.currentTimeMillis()}").also { it.mkdirs() }

        try {
            var totalApkSize = 0L
            ZipFile(xapkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".apk", ignoreCase = true)) {
                        val cleanName = entry.name.substringAfterLast('/')
                        val tempFile = File(tempDir, cleanName)
                        zip.getInputStream(entry).use { inStream ->
                            tempFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        tempApkFiles.add(tempFile)
                        totalApkSize += tempFile.length()
                    }
                }
            }

            if (tempApkFiles.isEmpty()) {
                return InstallResult.Failure(
                    errorCode = InstallErrorCode.INSTALL_FAILED_INVALID_APK,
                    technicalMessage = "No APK files found inside XAPK archive",
                    packageName = metadata.packageName,
                    appName = metadata.appName
                )
            }

            val apkStreams = tempApkFiles.map { it.name to FileInputStream(it) }
            val result = createAndCommitSession(
                packageName = metadata.packageName,
                appName = metadata.appName,
                apkStreams = apkStreams,
                totalSize = totalApkSize,
                startProgress = 0.50f,
                onProgress = onProgress
            )

            return result
        } finally {
            FileUtils.clearDirectory(tempDir)
            tempDir.delete()
        }
    }

    private suspend fun installApks(
        apksFile: File,
        metadata: PackageMetadata,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        updateProgress(
            InstallStage.EXTRACTING,
            0.20f,
            "Selecting compatible splits from APKS bundle...",
            onProgress
        )

        val tempDir = File(context.cacheDir, "apks_staging_${System.currentTimeMillis()}").also { it.mkdirs() }
        val tempApkFiles = mutableListOf<File>()

        try {
            var totalApkSize = 0L
            val requiredSplitNames = metadata.splits
                .filter { it.isRequiredForDevice }
                .map { it.name }
                .toSet()

            ZipFile(apksFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".apk", ignoreCase = true)) {
                        val isRequired = requiredSplitNames.isEmpty() || entry.name in requiredSplitNames
                        if (isRequired) {
                            val cleanName = entry.name.replace('/', '_').replace('\\', '_')
                            val tempFile = File(tempDir, cleanName)
                            zip.getInputStream(entry).use { inStream ->
                                tempFile.outputStream().use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            tempApkFiles.add(tempFile)
                            totalApkSize += tempFile.length()
                        }
                    }
                }
            }

            if (tempApkFiles.isEmpty()) {
                return InstallResult.Failure(
                    errorCode = InstallErrorCode.INSTALL_FAILED_NO_MATCHING_ABIS,
                    technicalMessage = "No matching APK splits found for device architecture",
                    packageName = metadata.packageName,
                    appName = metadata.appName
                )
            }

            val apkStreams = tempApkFiles.map { it.name to FileInputStream(it) }
            return createAndCommitSession(
                packageName = metadata.packageName,
                appName = metadata.appName,
                apkStreams = apkStreams,
                totalSize = totalApkSize,
                startProgress = 0.40f,
                onProgress = onProgress
            )
        } finally {
            FileUtils.clearDirectory(tempDir)
            tempDir.delete()
        }
    }

    override suspend fun installMultipleApks(
        apkFiles: List<File>,
        obbFiles: List<ObbFile>,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        if (apkFiles.isEmpty()) {
            return@withContext InstallResult.Failure(
                errorCode = InstallErrorCode.INSTALL_FAILED_INVALID_APK,
                technicalMessage = "No APK files provided for split installation"
            )
        }

        val totalSize = apkFiles.sumOf { it.length() }
        val apkStreams = apkFiles.map { it.name to FileInputStream(it) }

        createAndCommitSession(
            packageName = "",
            appName = "Split Package",
            apkStreams = apkStreams,
            totalSize = totalSize,
            onProgress = onProgress
        )
    }

    private suspend fun createAndCommitSession(
        packageName: String,
        appName: String,
        apkStreams: List<Pair<String, InputStream>>,
        totalSize: Long,
        startProgress: Float = 0.1f,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (packageName.isNotBlank()) {
            params.setAppPackageName(packageName)
        }
        if (totalSize > 0) {
            params.setSize(totalSize)
        }

        var session: PackageInstaller.Session? = null
        try {
            updateProgress(
                InstallStage.CREATING_SESSION,
                startProgress,
                "Opening installer session...",
                onProgress
            )

            val sessionId = packageInstaller.createSession(params)
            currentSessionId = sessionId
            val activeSession = packageInstaller.openSession(sessionId)
            session = activeSession

            val totalFiles = apkStreams.size
            var overallBytesWritten = 0L

            for ((index, pair) in apkStreams.withIndex()) {
                val (name, inStream) = pair
                val cleanName = name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

                updateProgress(
                    InstallStage.WRITING_SPLITS,
                    startProgress + ((index.toFloat() / totalFiles) * (0.85f - startProgress)),
                    "Writing split $cleanName (${index + 1}/$totalFiles)...",
                    onProgress
                )

                inStream.use { input ->
                    activeSession.openWrite(cleanName, 0, -1).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            overallBytesWritten += bytesRead
                        }
                        activeSession.fsync(output)
                    }
                }
            }

            updateProgress(
                InstallStage.COMMITTING_SESSION,
                0.88f,
                "Committing session to Android package manager...",
                onProgress
            )

            // Prepare callback intent
            val callbackIntent = Intent(context, PackageInstallStatusReceiver::class.java).apply {
                action = PackageInstallStatusReceiver.ACTION_INSTALL_STATUS
                putExtra(PackageInstallStatusReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(PackageInstallStatusReceiver.EXTRA_PACKAGE_NAME, packageName)
                putExtra(PackageInstallStatusReceiver.EXTRA_APP_NAME, appName)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                flags
            )

            activeSession.commit(pendingIntent.intentSender)
            activeSession.close()
            session = null

            updateProgress(
                InstallStage.WAITING_CONFIRMATION,
                0.92f,
                "Awaiting system confirmation...",
                onProgress
            )

            // Return pending user action state (will be updated via broadcast receiver)
            InstallResult.UserActionRequired(
                confirmationIntent = Intent(),
                sessionId = sessionId
            )
        } catch (e: Exception) {
            session?.abandon()
            e.printStackTrace()
            val failure = InstallResult.Failure(
                errorCode = InstallErrorCode.fromStatusAndMessage(-1, e.message),
                technicalMessage = e.localizedMessage ?: e.toString(),
                packageName = packageName,
                appName = appName
            )
            _lastResult.value = failure
            failure
        }
    }

    private fun updateProgress(
        stage: InstallStage,
        fraction: Float,
        detail: String,
        onProgress: (InstallProgress) -> Unit
    ) {
        val progress = InstallProgress(
            stage = stage,
            progressFraction = fraction.coerceIn(0f, 1f),
            statusDetail = detail
        )
        _installProgress.value = progress
        onProgress(progress)
    }

    override fun cancelInstall() {
        if (currentSessionId != -1) {
            try {
                context.packageManager.packageInstaller.abandonSession(currentSessionId)
            } catch (e: Exception) {
                // ignore
            }
            currentSessionId = -1
        }
        installJob?.cancel()
        _installProgress.value = InstallProgress(
            stage = InstallStage.FAILED,
            progressFraction = 0f,
            statusDetail = "Installation cancelled"
        )
    }
}
