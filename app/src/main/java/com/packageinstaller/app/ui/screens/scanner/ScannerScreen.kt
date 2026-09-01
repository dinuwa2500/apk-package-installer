package com.packageinstaller.app.ui.screens.scanner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packageinstaller.app.domain.model.InstallResult
import com.packageinstaller.app.domain.model.InstallStage
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.domain.model.SortOption
import com.packageinstaller.app.ui.components.EmptyState
import com.packageinstaller.app.ui.components.PackageCard
import com.packageinstaller.app.ui.screens.inspector.PackageInspectorSheet
import com.packageinstaller.app.ui.screens.install.InstallProgressDialog
import com.packageinstaller.app.ui.screens.install.InstallResultDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage Scanner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.displayName,
                                        fontWeight = if (uiState.filter.sortBy == option) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.updateSortOption(option)
                                    showSortMenu = false
                                },
                                leadingIcon = if (uiState.filter.sortBy == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.scanStats.isScanning) {
                        viewModel.stopScan()
                    } else {
                        viewModel.startScan()
                    }
                },
                icon = {
                    if (uiState.scanStats.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Radar, contentDescription = null)
                    }
                },
                text = {
                    Text(if (uiState.scanStats.isScanning) "Scanning..." else "Scan Storage")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Field
            OutlinedTextField(
                value = uiState.filter.query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search by name, package or file...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.filter.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filter.typeFilter == null,
                    onClick = { viewModel.updateTypeFilter(null) },
                    label = { Text("All (${uiState.packages.size})") }
                )

                PackageType.values().forEach { type ->
                    val count = uiState.packages.count { it.packageType == type }
                    FilterChip(
                        selected = uiState.filter.typeFilter == type,
                        onClick = { viewModel.updateTypeFilter(if (uiState.filter.typeFilter == type) null else type) },
                        label = { Text("${type.displayName} ($count)") }
                    )
                }
            }

            // Scan Progress Banner (if scanning)
            if (uiState.scanStats.isScanning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scanning internal & hidden directories...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${uiState.scanStats.packagesFound} found",
                                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Visited: ${uiState.scanStats.totalDirectoriesScanned} dirs (${uiState.scanStats.hiddenDirectoriesScanned} hidden) • ${uiState.scanStats.totalFilesScanned} files",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        if (uiState.scanStats.currentPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.scanStats.currentPath,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Package List
            if (uiState.filteredPackages.isEmpty() && !uiState.scanStats.isScanning) {
                EmptyState(
                    icon = Icons.Default.ManageSearch,
                    title = "No Packages Found",
                    subtitle = if (uiState.filter.query.isNotBlank()) "No packages match your search filter." else "Tap 'Scan Storage' to scan device storage including hidden folders.",
                    actionText = if (uiState.filter.query.isNotBlank()) "Clear Filter" else "Start Scan",
                    onActionClick = {
                        if (uiState.filter.query.isNotBlank()) {
                            viewModel.updateSearchQuery("")
                            viewModel.updateTypeFilter(null)
                        } else {
                            viewModel.startScan()
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredPackages, key = { it.id }) { pkg ->
                        PackageCard(
                            metadata = pkg,
                            onInstallClick = { viewModel.installPackage(pkg) },
                            onDetailsClick = { viewModel.inspectPackage(pkg) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp)) // Space for FAB
                    }
                }
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
            onDismiss = { /* Handled */ },
            onOpenApp = { pkgName ->
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        )
    }
}
