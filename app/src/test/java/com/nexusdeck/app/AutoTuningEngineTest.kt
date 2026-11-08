package com.nexusdeck.app

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.core.optimizer.AutoTuningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTuningEngineTest {

    private val pcGame = GameItem(
        id = "pc_fallout_nv",
        title = "Fallout: New Vegas",
        platform = GamePlatform.PC,
        coverUrl = "https://example.com/cover.jpg",
        minRamGb = 6,
        recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
        targetEmulator = TargetEmulator.GameNative
    )

    private val pspGame = GameItem(
        id = "psp_tekken_6",
        title = "Tekken 6",
        platform = GamePlatform.PSP,
        coverUrl = "https://example.com/cover.jpg",
        minRamGb = 3,
        recommendedSocKeywords = listOf("snapdragon_8", "dimensity_8"),
        targetEmulator = TargetEmulator.PPSSPP
    )

    @Test
    fun testAdrenoGpu_automaticallySelectsMesaTurnipDriver() {
        val adrenoSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 2",
            gpuModel = "Adreno (TM) 740",
            totalRamGb = 12,
            vulkanVersion = "Vulkan 1.3"
        )

        val config = AutoTuningEngine.generateOptimalConfig(adrenoSpec, pcGame)

        assertTrue(
            "Driver should be Mesa Turnip for Adreno GPU, but was: ${config.driverName}",
            config.driverName.contains("Mesa Turnip")
        )
    }

    @Test
    fun testMaliGpu_fallsBackToSystemVulkan() {
        val maliSpec = HardwareSpec(
            socModel = "Dimensity 9200",
            gpuModel = "Mali-G715 Immortalis",
            totalRamGb = 12,
            vulkanVersion = "Vulkan 1.3"
        )

        val config = AutoTuningEngine.generateOptimalConfig(maliSpec, pcGame)

        assertEquals("System Vulkan (Stock)", config.driverName)
    }

    @Test
    fun testFlagshipSoC_setsTargetResolutionHigh() {
        val flagshipSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 3",
            gpuModel = "Adreno 750",
            totalRamGb = 16,
            vulkanVersion = "Vulkan 1.3"
        )

        val config = AutoTuningEngine.generateOptimalConfig(flagshipSpec, pspGame)
        assertEquals("1080p (4x PSP Scaling)", config.targetResolution)
    }

    @Test
    fun testPcGame_configuresDxvkAsyncShaderPipeline() {
        val flagshipSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 2",
            gpuModel = "Adreno 740",
            totalRamGb = 12,
            vulkanVersion = "Vulkan 1.3"
        )

        val config = AutoTuningEngine.generateOptimalConfig(flagshipSpec, pcGame)

        assertEquals("DXVK 2.3.1 (Async)", config.dxvkVersion)
        assertEquals("Performance (Aggressive JIT)", config.cpuDynarecPreset)
        assertEquals("Vulkan", config.rendererBackend)
    }

    @Test
    fun testStockConfig_returnsSafeDefaults() {
        val stockConfig = AutoTuningEngine.generateStockConfig(pcGame)

        assertEquals("System Default Driver", stockConfig.driverName)
        assertEquals("Native / 1x", stockConfig.targetResolution)
        assertEquals("OpenGL", stockConfig.rendererBackend)
        assertEquals("Safe (Compatibility)", stockConfig.cpuDynarecPreset)
    }
}
