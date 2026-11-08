package com.nexusdeck.app

import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.core.profiler.HardwareProfiler
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareProfilerTest {

    private val heavyGame = GameItem(
        id = "ps3_rdr",
        title = "Red Dead Redemption",
        platform = GamePlatform.PS3,
        coverUrl = "https://example.com/cover.jpg",
        minRamGb = 8,
        recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
        targetEmulator = TargetEmulator.ARMSX3
    )

    private val pspGame = GameItem(
        id = "psp_tekken_6",
        title = "Tekken 6",
        platform = GamePlatform.PSP,
        coverUrl = "https://example.com/cover.jpg",
        minRamGb = 3,
        recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
        targetEmulator = TargetEmulator.PPSSPP
    )

    @Test
    fun testRamBelowMinimum_returnsUnsupported() {
        val lowRamSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 2",
            gpuModel = "Adreno 740",
            totalRamGb = 4, // Below 8GB requirement
            vulkanVersion = "Vulkan 1.3"
        )

        val tier = HardwareProfiler.evaluateCompatibility(lowRamSpec, heavyGame)
        assertEquals(CompatibilityTier.UNSUPPORTED, tier)
    }

    @Test
    fun testFlagshipSnapdragonWithAdreno_returnsVerified60Fps() {
        val flagshipSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 2",
            gpuModel = "Adreno (TM) 740",
            totalRamGb = 12,
            vulkanVersion = "Vulkan 1.3"
        )

        val tier = HardwareProfiler.evaluateCompatibility(flagshipSpec, heavyGame)
        assertEquals(CompatibilityTier.VERIFIED_60FPS, tier)
    }

    @Test
    fun testMidrangeChipsetAboveMinRam_returnsPlayableTweaked() {
        // Spec with sufficient RAM (8GB) but older Helio / entry GPU not in recommended list for PS3
        val entrySpec = HardwareSpec(
            socModel = "MediaTek Helio G85",
            gpuModel = "Mali-G52 MC2",
            totalRamGb = 8,
            vulkanVersion = "Vulkan 1.1"
        )

        val tier = HardwareProfiler.evaluateCompatibility(entrySpec, heavyGame)
        assertEquals(CompatibilityTier.PLAYABLE_TWEAKED, tier)
    }

    @Test
    fun testPspGameWithDimensityOrAdreno_returnsVerified60Fps() {
        val midSpec = HardwareSpec(
            socModel = "Dimensity 8100",
            gpuModel = "Mali-G610",
            totalRamGb = 8,
            vulkanVersion = "Vulkan 1.3"
        )

        val tier = HardwareProfiler.evaluateCompatibility(midSpec, pspGame)
        assertEquals(CompatibilityTier.VERIFIED_60FPS, tier)
    }

    @Test
    fun testQualcommCodeNameSm8550_recognizedAsSnapdragon8() {
        val oemCodeNameSpec = HardwareSpec(
            socModel = "SM8550", // Qualcomm internal part number for Snapdragon 8 Gen 2
            gpuModel = "Adreno 740",
            totalRamGb = 16,
            vulkanVersion = "Vulkan 1.3"
        )

        val tier = HardwareProfiler.evaluateCompatibility(oemCodeNameSpec, heavyGame)
        assertEquals(CompatibilityTier.VERIFIED_60FPS, tier)
    }
}
