package com.packageinstaller.app.ui.screens.browser

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packageinstaller.app.domain.model.InstallResult
import com.packageinstaller.app.domain.model.InstallStage
import com.packageinstaller.app.domain.model.SortOption
import com.packageinstaller.app.ui.components.BreadcrumbBar
import com.packageinstaller.app.ui.components.EmptyState
import com.packageinstaller.app.ui.components.FileListItem
import com.packageinstaller.app.ui.screens.inspector.PackageInspectorSheet
import com.packageinstaller.app.ui.screens.install.InstallProgressDialog
import com.packageinstaller.app.ui.screens.install.InstallResultDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    // Intercept back button to navigate up folders
    BackHandler(enabled = uiState.currentDirectory.parentFile?.canRead() == true) {
        viewModel.navigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "File Browser",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    // Multi-select toggle button
                    IconButton(onClick = { viewModel.toggleMultiSelectMode() }) {
                        Icon(
                            imageVector = if (uiState.isMultiSelectMode) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                            contentDescription = "Multi Select",
                            tint = if (uiState.isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Sort button
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
                                        fontWeight = if (uiState.sortBy == option) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.updateSortOption(option)
                                    showSortMenu = false
                                },
                                leadingIcon = if (uiState.sortBy == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }

                    // Show Hidden Directories Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Hidden",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = uiState.showHiddenDirectories,
                            onCheckedChange = { viewModel.toggleShowHiddenDirectories(it) }
                        )
                    }

                    IconButton(onClick = { viewModel.loadDirectory(uiState.currentDirectory) }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            // Multi-selection bottom action bar
            if (uiState.selectedFiles.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.selectedFiles.size} splits selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Clear")
                            }

                            Button(
                                onClick = { viewModel.inspectSelectedSplits() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect & Install")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Breadcrumbs Navigation Bar
            BreadcrumbBar(
                currentPath = uiState.currentDirectory,
                onNavigateTo = { viewModel.loadDirectory(it) }
            )

            // Search Bar inside current folder
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search files in current folder...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Filter Chips Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrowserFilterOption.values().forEach { option ->
                    FilterChip(
                        selected = uiState.filterOption == option,
                        onClick = { viewModel.updateFilterOption(option) },
                        label = { Text(option.displayName, fontSize = 12.sp) }
                    )
                }
            }

            // Multi-Select Helper bar (if multi-select mode is on)
            if (uiState.isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select split APK parts to install together",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                    )

                    TextButton(onClick = { viewModel.selectAllApksInCurrentFolder() }) {
                        Text("Select All APKs", fontSize = 12.sp)
                    }
                }
            }

            // Quick Action: Install this entire folder as Split Set
            val apkInDirCount = uiState.items.count { it.packageType != null }
            if (apkInDirCount > 1 && !uiState.isMultiSelectMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Split Package Detected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$apkInDirCount APK splits in folder",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        Button(
                            onClick = { viewModel.installFolderAsSplits(uiState.currentDirectory) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Install All Splits", fontSize = 12.sp)
                        }
                    }
                }
            }

            // File / Directory List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "No Items Found",
                    subtitle = if (uiState.searchQuery.isNotBlank()) "No files match '${uiState.searchQuery}'." else if (!uiState.showHiddenDirectories) "Folder is empty or contains hidden files. Enable 'Hidden' to see dot-directories." else "This folder is empty."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.filteredItems, key = { it.path }) { item ->
                        FileListItem(
                            item = item,
                            onClick = { viewModel.onItemClick(item) },
                            isSelectable = uiState.isMultiSelectMode,
                            isSelected = uiState.selectedFiles.contains(item.file),
                            onLongClick = {
                                if (!item.isDirectory) {
                                    viewModel.toggleSelectFile(item.file)
                                }
                            },
                            onToggleSelect = {
                                viewModel.toggleSelectFile(item.file)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Analyzing Loader Overlay
    if (uiState.isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(
                        text = "Analyzing package metadata...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        )
    }

    // Inspection Sheet (Analyzes and presents details before user taps "Install")
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
