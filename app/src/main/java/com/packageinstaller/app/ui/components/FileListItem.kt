package com.packageinstaller.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packageinstaller.app.domain.model.StorageItem
import com.packageinstaller.app.ui.theme.BadgeApkColor
import com.packageinstaller.app.ui.theme.BadgeApksColor
import com.packageinstaller.app.ui.theme.BadgeXapkColor
import com.packageinstaller.app.utils.FileUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: StorageItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectable: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null
) {
    val opacity = if (item.isHidden) 0.70f else 1.0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .alpha(opacity),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox in multi-select mode
            if (isSelectable && !item.isDirectory) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect?.invoke() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Icon
            val iconTint = when {
                item.isDirectory -> MaterialTheme.colorScheme.primary
                item.packageType != null -> when (item.packageType) {
                    com.packageinstaller.app.domain.model.PackageType.APK -> BadgeApkColor
                    com.packageinstaller.app.domain.model.PackageType.XAPK -> BadgeXapkColor
                    com.packageinstaller.app.domain.model.PackageType.APKS -> BadgeApksColor
                    else -> MaterialTheme.colorScheme.primary
                }
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isDirectory -> Icons.Default.Folder
                        item.packageType != null -> Icons.Default.Android
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (item.isHidden) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "HIDDEN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val detailText = if (item.isDirectory) {
                        "${item.childCount} items"
                    } else {
                        FileUtils.formatFileSize(item.sizeBytes)
                    }

                    val dateText = FileUtils.formatDate(item.lastModified)

                    Text(
                        text = if (dateText.isNotBlank()) "$detailText • $dateText" else detailText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.packageType != null) {
                Spacer(modifier = Modifier.width(8.dp))
                PackageTypeBadge(packageType = item.packageType)
            }

            if (item.isDirectory) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
