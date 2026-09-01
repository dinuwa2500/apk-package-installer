package com.packageinstaller.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packageinstaller.app.domain.model.PackageType
import com.packageinstaller.app.ui.theme.*

@Composable
fun PackageTypeBadge(
    packageType: PackageType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (packageType) {
        PackageType.APK -> BadgeApkColor.copy(alpha = 0.15f) to BadgeApkColor
        PackageType.XAPK -> BadgeXapkColor.copy(alpha = 0.15f) to BadgeXapkColor
        PackageType.APKS -> BadgeApksColor.copy(alpha = 0.15f) to BadgeApksColor
        PackageType.SPLIT_SET -> BadgeSplitColor.copy(alpha = 0.15f) to BadgeSplitColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = packageType.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CompatibilityBadge(
    isCompatible: Boolean,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = if (isCompatible) {
        Triple(SuccessGreen.copy(alpha = 0.15f), SuccessGreen, "Compatible")
    } else {
        Triple(ErrorLight.copy(alpha = 0.15f), ErrorLight, "Incompatible")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
