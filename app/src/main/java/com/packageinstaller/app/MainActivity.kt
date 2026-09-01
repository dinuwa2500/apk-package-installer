package com.packageinstaller.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.packageinstaller.app.data.installer.PackageInstallStatusReceiver
import com.packageinstaller.app.domain.model.InstallResult
import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.ui.navigation.MainAppNavigation
import com.packageinstaller.app.ui.screens.inspector.PackageInspectorSheet
import com.packageinstaller.app.ui.theme.PackageInstallerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var incomingPackageMetadata by mutableStateOf<PackageMetadata?>(null)

    // User action confirmation launcher for PackageInstaller.Session
    private val confirmationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Result is propagated to broadcast receiver by Android OS
    }

    // Permission launcher for storage and notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestInitialPermissions()
        handleIncomingIntent(intent)
        observeInstallEvents()

        setContent {
            PackageInstallerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    MainAppNavigation(navController = navController)

                    // If app was opened with a file Intent (e.g. from a File Manager), show inspector sheet
                    incomingPackageMetadata?.let { metadata ->
                        PackageInspectorSheet(
                            metadata = metadata,
                            onDismiss = { incomingPackageMetadata = null },
                            onInstallClick = { pkg ->
                                incomingPackageMetadata = null
                                val app = application as PackageInstallerApplication
                                lifecycleScope.launch {
                                    app.installPackageUseCase.execute(pkg)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = intent.data

        if (uri != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_INSTALL_PACKAGE)) {
            val app = application as PackageInstallerApplication
            lifecycleScope.launch {
                val metadata = app.parsePackageUseCase.fromUri(uri)
                if (metadata != null) {
                    incomingPackageMetadata = metadata
                }
            }
        }
    }

    private fun requestInitialPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun observeInstallEvents() {
        lifecycleScope.launch {
            PackageInstallStatusReceiver.installEvents.collect { result ->
                if (result is InstallResult.UserActionRequired) {
                    confirmationLauncher.launch(result.confirmationIntent)
                }
            }
        }
    }
}
