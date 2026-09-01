package com.packageinstaller.app.compat

import com.packageinstaller.app.domain.usecase.GetDeviceCompatUseCase
import org.junit.Assert.*
import org.junit.Test

class DeviceCompatibilityTest {

    private val compatUseCase = GetDeviceCompatUseCase()

    @Test
    fun testMinSdkCheck() {
        val result = compatUseCase.checkCompatibility(
            minSdk = 21,
            targetSdk = 34,
            packageAbis = emptyList(),
            currentSdk = 34,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        // Check structure
        assertEquals(21, result.minSdk)
        assertEquals(34, result.targetSdk)
        assertTrue(result.minSdkMet)
        assertTrue(result.isCompatible)
    }

    @Test
    fun testAbiMatching() {
        val result = compatUseCase.checkCompatibility(
            minSdk = 21,
            targetSdk = 34,
            packageAbis = listOf("arm64-v8a", "armeabi-v7a"),
            currentSdk = 34,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNotNull(result)
        assertTrue(result.isCompatible)
    }
}
