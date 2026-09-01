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
            packageAbis = emptyList()
        )
        // Check structure
        assertEquals(21, result.minSdk)
        assertEquals(34, result.targetSdk)
    }

    @Test
    fun testAbiMatching() {
        val result = compatUseCase.checkCompatibility(
            minSdk = 21,
            targetSdk = 34,
            packageAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertNotNull(result)
    }
}
