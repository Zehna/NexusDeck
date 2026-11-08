package com.nexusdeck.app.core.optimizer

import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.HardwareSpec

class AutoTuningEngine {

    fun generateOptimalConfig(spec: HardwareSpec, game: GameItem): EngineConfig {
        return Companion.generateOptimalConfig(spec, game)
    }

    companion object {
        private enum class SoCTier {
            FLAGSHIP,
            MIDRANGE,
            ENTRY
        }

        fun generateOptimalConfig(spec: HardwareSpec, game: GameItem): EngineConfig {
            val isAdreno = spec.gpuModel.contains("adreno", ignoreCase = true)
            val socTier = classifySocTier(spec)

            // 1. Logika Driver GPU
            val driverName = if (isAdreno) {
                "Mesa Turnip v24.2.0 (Custom Adreno Driver)"
            } else {
                "System Vulkan (Stock)"
            }

            // 2. Logika Resolusi berdasarkan Tier SoC & Platform
            val targetResolution = when (game.platform) {
                GamePlatform.PSP -> when (socTier) {
                    SoCTier.FLAGSHIP -> "1080p (4x PSP Scaling)"
                    SoCTier.MIDRANGE -> "1080p (3x PSP Scaling)"
                    SoCTier.ENTRY -> "720p (2x PSP Scaling)"
                }
                else -> when (socTier) {
                    SoCTier.FLAGSHIP -> "1080p (2x Scaling)"
                    SoCTier.MIDRANGE -> "720p (1.5x Scaling)"
                    SoCTier.ENTRY -> "Native / 1x"
                }
            }

            // 3. Logika Renderer Backend
            val rendererBackend = if (
                spec.vulkanVersion.contains("1.3") ||
                spec.vulkanVersion.contains("1.2") ||
                isAdreno ||
                game.platform != GamePlatform.PS2
            ) {
                "Vulkan"
            } else {
                "OpenGL"
            }

            // 4. Logika DXVK (Khusus Game PC) & CPU Dynarec JIT Preset
            val dxvkVersion = when (game.platform) {
                GamePlatform.PC -> {
                    if (socTier == SoCTier.FLAGSHIP || socTier == SoCTier.MIDRANGE) {
                        "DXVK 2.3.1 (Async)"
                    } else {
                        "DXVK 1.10.3"
                    }
                }
                else -> "N/A (Native Console Pipeline)"
            }

            val cpuDynarecPreset = if (
                (socTier == SoCTier.FLAGSHIP || socTier == SoCTier.MIDRANGE) &&
                spec.totalRamGb >= game.minRamGb
            ) {
                "Performance (Aggressive JIT)"
            } else {
                "Safe (Compatibility)"
            }

            return EngineConfig(
                driverName = driverName,
                targetResolution = targetResolution,
                rendererBackend = rendererBackend,
                dxvkVersion = dxvkVersion,
                cpuDynarecPreset = cpuDynarecPreset
            )
        }

        fun generateStockConfig(game: GameItem): EngineConfig {
            return EngineConfig(
                driverName = "System Default Driver",
                targetResolution = "Native / 1x",
                rendererBackend = "OpenGL",
                dxvkVersion = if (game.platform == GamePlatform.PC) "DXVK 1.10.3" else "N/A (Native Console Pipeline)",
                cpuDynarecPreset = "Safe (Compatibility)"
            )
        }

        private fun classifySocTier(spec: HardwareSpec): SoCTier {
            val combined = "${spec.socModel} ${spec.gpuModel}".lowercase()

            val flagshipIndicators = listOf(
                "8 gen 2", "8 gen 3", "8 gen 4", "8 elite",
                "sm8550", "sm8650", "sm8750",
                "adreno 740", "adreno (tm) 740",
                "adreno 750", "adreno (tm) 750",
                "adreno 830", "adreno (tm) 830",
                "dimensity 9", "mt698", "mt699"
            )
            if (flagshipIndicators.any { combined.contains(it) }) {
                return SoCTier.FLAGSHIP
            }

            val midrangeIndicators = listOf(
                "snapdragon 7", "sm7",
                "870", "888", "8 gen 1", "8+ gen 1", "sm8250", "sm8350", "sm8450", "sm8475",
                "adreno 6", "adreno (tm) 6", "adreno 710", "adreno 720", "adreno 730",
                "dimensity 8", "mt689",
                "tensor", "exynos 2", "ranchu", "goldfish"
            )
            if (midrangeIndicators.any { combined.contains(it) } || spec.totalRamGb >= 8) {
                return SoCTier.MIDRANGE
            }

            return SoCTier.ENTRY
        }
    }
}
