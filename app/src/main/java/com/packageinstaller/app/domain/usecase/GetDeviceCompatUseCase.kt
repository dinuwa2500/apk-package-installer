package com.packageinstaller.app.domain.usecase

import android.os.Build
import com.packageinstaller.app.domain.model.DeviceCompatibility

class GetDeviceCompatUseCase {

    fun checkCompatibility(
        minSdk: Int,
        targetSdk: Int,
        packageAbis: List<String>
    ): DeviceCompatibility {
        val currentSdk = Build.VERSION.SDK_INT
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val issues = mutableListOf<String>()

        val minSdkMet = currentSdk >= minSdk
        if (!minSdkMet) {
            issues.add("Requires Android SDK $minSdk (Device is SDK $currentSdk)")
        }

        var abiCompatible = true
        if (packageAbis.isNotEmpty()) {
            val hasMatchingAbi = packageAbis.any { pkgAbi ->
                supportedAbis.any { devAbi ->
                    devAbi.equals(pkgAbi, ignoreCase = true) ||
                    (devAbi.startsWith("arm64") && pkgAbi.startsWith("armeabi"))
                }
            }
            if (!hasMatchingAbi) {
                abiCompatible = false
                issues.add("Incompatible CPU architecture. Package supports [${packageAbis.joinToString()}], device supports [${supportedAbis.joinToString()}]")
            }
        }

        return DeviceCompatibility(
            isCompatible = minSdkMet && abiCompatible,
            issues = issues,
            supportedAbis = supportedAbis,
            packageAbis = packageAbis,
            minSdkMet = minSdkMet,
            minSdk = minSdk,
            targetSdk = targetSdk,
            currentSdk = currentSdk
        )
    }
}
