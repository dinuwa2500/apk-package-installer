package com.packageinstaller.app.data.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.packageinstaller.app.domain.model.InstallErrorCode
import com.packageinstaller.app.domain.model.InstallResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PackageInstallStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_STATUS = "com.packageinstaller.app.ACTION_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "com.packageinstaller.app.EXTRA_SESSION_ID"
        const val EXTRA_PACKAGE_NAME = "com.packageinstaller.app.EXTRA_PACKAGE_NAME"
        const val EXTRA_APP_NAME = "com.packageinstaller.app.EXTRA_APP_NAME"

        private val _installEvents = MutableSharedFlow<InstallResult>(extraBufferCapacity = 10)
        val installEvents: SharedFlow<InstallResult> = _installEvents.asSharedFlow()

        fun postEvent(result: InstallResult) {
            _installEvents.tryEmit(result)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    _installEvents.tryEmit(
                        InstallResult.UserActionRequired(
                            confirmationIntent = confirmIntent,
                            sessionId = sessionId
                        )
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                _installEvents.tryEmit(
                    InstallResult.Success(
                        packageName = packageName,
                        appName = appName,
                        versionName = ""
                    )
                )
            }
            else -> {
                val errorCode = InstallErrorCode.fromStatusAndMessage(status, message)
                _installEvents.tryEmit(
                    InstallResult.Failure(
                        errorCode = errorCode,
                        technicalMessage = message ?: "Status code: $status",
                        packageName = packageName,
                        appName = appName
                    )
                )
            }
        }
    }
}
