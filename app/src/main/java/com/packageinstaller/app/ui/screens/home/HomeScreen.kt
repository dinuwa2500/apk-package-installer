package com.packageinstaller.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packageinstaller.app.domain.model.InstallResult
import com.packageinstaller.app.domain.model.InstallStage
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.ui.components.EmptyState
import com.packageinstaller.app.ui.components.PackageCard
import com.packageinstaller.app.ui.components.PermissionWarningBanner
import com.packageinstaller.app.ui.screens.inspector.PackageInspectorSheet
import com.packageinstaller.app.ui.screens.install.InstallProgressDialog
import com.packageinstaller.app.ui.screens.install.InstallResultDialog
import com.packageinstaller.app.ui.theme.BadgeApkColor
import com.packageinstaller.app.ui.theme.BadgeApksColor
import com.packageinstaller.app.ui.theme.BadgeSplitColor
import com.packageinstaller.app.ui.theme.BadgeXapkColor
import com.packageinstaller.app.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()

    // SAF Pickers
    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                viewModel.inspectUri(uris.first())
            } else {
                viewModel.inspectMultipleUris(uris)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Package Installer",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Warnings
            if (!uiState.hasStoragePermission) {
                item {
                    PermissionWarningBanner(
                        title = "Storage Permission Required",
                        description = "Grant All Files Access so Package Installer can locate and parse APK, XAPK, and APKS packages.",
                        actionText = "Grant Access",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            if (!uiState.hasInstallPermission) {
                item {
                    PermissionWarningBanner(
                        title = "Install Unknown Apps Permission",
                        description = "Package Installer requires permission to install applications on this device.",
                        actionText = "Allow",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Quick Actions Grid (4 Cards: Install APK, Install XAPK, Install APKS, Browse Files)
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        title = "Install APK",
                        subtitle = "Standard APK",
                        icon = Icons.Default.Android,
                        accentColor = BadgeApkColor,
                        modifier = Modifier.weight(1f),
                        onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) }
                    )
                    ActionCard(
                        title = "Install XAPK",
                        subtitle = "OBB + Splits",
                        icon = Icons.Default.Archive,
                        accentColor = BadgeXapkColor,
                        modifier = Modifier.weight(1f),
                        onClick = { apkPicker.launch(arrayOf("*/*")) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        title = "Install APKS",
                        subtitle = "Bundletool Archive",
                        icon = Icons.Default.Layers,
                        accentColor = BadgeApksColor,
                        modifier = Modifier.weight(1f),
                        onClick = { apkPicker.launch(arrayOf("*/*")) }
                    )
                    ActionCard(
                        title = "Browse Files",
                        subtitle = "Explorer & Splits",
                        icon = Icons.Default.FolderOpen,
                        accentColor = BadgeSplitColor,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToBrowser
                    )
                }
            }

            // Storage Overview
            item {
                StorageOverviewCard(
                    availableBytes = uiState.availableStorageBytes,
                    totalBytes = uiState.totalStorageBytes,
                    packagesFound = uiState.totalPackagesFound,
                    onScanClick = onNavigateToScanner
                )
            }

            // Recent Packages Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Packages",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onNavigateToScanner) {
                        Text("View All (${uiState.totalPackagesFound})")
                    }
                }
            }

            if (uiState.recentPackages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No Packages Discovered Yet",
                        subtitle = "Run the Storage Scanner to automatically find APK, XAPK, and APKS files across internal storage.",
                        actionText = "Scan Storage",
                        onActionClick = onNavigateToScanner
                    )
                }
            } else {
                items(uiState.recentPackages, key = { it.id }) { pkg ->
                    PackageCard(
                        metadata = pkg,
                        onInstallClick = { viewModel.installPackage(pkg) },
                        onDetailsClick = { viewModel.inspectPackage(pkg) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Inspection Sheet
    if (uiState.selectedPackageForInspection != null) {
        PackageInspectorSheet(
            metadata = uiState.selectedPackageForInspection!!,
            onDismiss = { viewModel.dismissInspector() },
            onInstallClick = { pkg -> viewModel.installPackage(pkg) }
        )
    }

    // Live Installation Progress Dialog
    if (installProgress.stage != InstallStage.PREPARING && installProgress.stage != InstallStage.COMPLETED && installProgress.stage != InstallStage.FAILED) {
        InstallProgressDialog(
            progress = installProgress,
            appName = "Package",
            onCancel = { viewModel.cancelInstall() }
        )
    }

    // Installation Result Dialog
    if (installResult != null && installResult !is InstallResult.UserActionRequired) {
        InstallResultDialog(
            result = installResult!!,
            onDismiss = { /* Reset handled by dialog */ },
            onOpenApp = { pkgName ->
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun StorageOverviewCard(
    availableBytes: Long,
    totalBytes: Long,
    packagesFound: Int,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Storage",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${FileUtils.formatFileSize(availableBytes)} free of ${FileUtils.formatFileSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                FilledTonalButton(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan ($packagesFound)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
