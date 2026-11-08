package com.nexusdeck.app.core.profiler

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.HardwareSpec
import java.io.File
import kotlin.math.ceil

class HardwareProfiler(private val context: Context) {

    fun detectHardwareSpec(): HardwareSpec {
        val totalRamGb = readTotalRamGb()
        val socModel = readSocModel()
        val gpuModel = readGpuModel()
        val vulkanVersion = readVulkanVersion()

        return HardwareSpec(
            socModel = socModel,
            gpuModel = gpuModel,
            totalRamGb = totalRamGb,
            vulkanVersion = vulkanVersion
        )
    }

    private fun readTotalRamGb(): Int {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val totalBytes = memoryInfo.totalMem
            if (totalBytes > 0L) {
                val gb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                ceil(gb).toInt().coerceAtLeast(1)
            } else {
                4
            }
        } catch (_: Exception) {
            4
        }
    }

    private fun readSocModel(): String {
        val candidates = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL
            if (!socModel.isNullOrBlank() && !socModel.equals(Build.UNKNOWN, ignoreCase = true)) {
                candidates.add(socModel.trim())
            }
        }

        val cpuInfoSoc = readCpuInfoHardware()
        if (!cpuInfoSoc.isNullOrBlank()) {
            candidates.add(cpuInfoSoc.trim())
        }

        val hardware = Build.HARDWARE
        if (!hardware.isNullOrBlank() && !hardware.equals(Build.UNKNOWN, ignoreCase = true)) {
            candidates.add(hardware.trim())
        }

        return candidates.firstOrNull { it.isNotBlank() } ?: "Unknown SoC"
    }

    private fun readCpuInfoHardware(): String? {
        return try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (!cpuInfoFile.exists() || !cpuInfoFile.canRead()) return null

            var hardwareLine: String? = null
            var modelNameLine: String? = null

            cpuInfoFile.forEachLine { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    if (value.isNotBlank()) {
                        when (key) {
                            "hardware" -> if (hardwareLine == null) hardwareLine = value
                            "model name", "processor" -> if (modelNameLine == null) modelNameLine = value
                        }
                    }
                }
            }
            hardwareLine ?: modelNameLine
        } catch (_: Exception) {
            null
        }
    }

    private fun readGpuModel(): String {
        return try {
            val existingRenderer = GLES20.glGetString(GLES20.GL_RENDERER)
            if (!existingRenderer.isNullOrBlank()) {
                return existingRenderer.trim()
            }

            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "Unknown GPU"

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return "Unknown GPU"
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: run {
                EGL14.eglTerminate(display)
                return "Unknown GPU"
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val eglContext = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                contextAttribs,
                0
            )

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.trim() ?: "Unknown GPU"

            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            renderer
        } catch (_: Exception) {
            "Unknown GPU"
        }
    }

    private fun readVulkanVersion(): String {
        return try {
            val features = context.packageManager.systemAvailableFeatures
            val vulkanFeature = features.firstOrNull {
                it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
            }
            val rawVersion = vulkanFeature?.version ?: 0
            if (rawVersion > 0) {
                val major = rawVersion shr 22
                val minor = (rawVersion shr 12) and 0x3FF
                "Vulkan $major.$minor"
            } else {
                "Vulkan 1.1"
            }
        } catch (_: Exception) {
            "Vulkan 1.1"
        }
    }

    fun evaluateCompatibility(spec: HardwareSpec, game: GameItem): CompatibilityTier {
        return Companion.evaluateCompatibility(spec, game)
    }

    companion object {
        fun evaluateCompatibility(spec: HardwareSpec, game: GameItem): CompatibilityTier {
            // 1. Jika RAM di bawah batas minimum game -> UNSUPPORTED
            if (spec.totalRamGb < game.minRamGb) {
                return CompatibilityTier.UNSUPPORTED
            }

            // 2. Normalisasi teks SoC & GPU untuk pencocokan kata kunci
            val normalizedHardwareSignature = buildNormalizedSignature(spec)

            val matchesRecommendedSoc = game.recommendedSocKeywords.any { keyword ->
                val normalizedKeyword = keyword.lowercase()
                    .replace("_", "")
                    .replace("-", "")
                    .replace(" ", "")
                normalizedHardwareSignature.contains(normalizedKeyword)
            }

            // 3. Jika RAM mencukupi dan SoC masuk daftar rekomendasi (atau RAM jauh melampaui syarat + GPU kelas atas)
            if (matchesRecommendedSoc) {
                return CompatibilityTier.VERIFIED_60FPS
            }

            // 4. Jika RAM memenuhi syarat minimum tetapi SoC di bawah rekomendasi -> PLAYABLE_TWEAKED
            return CompatibilityTier.PLAYABLE_TWEAKED
        }

        private fun buildNormalizedSignature(spec: HardwareSpec): String {
            val raw = "${spec.socModel} ${spec.gpuModel}".lowercase()
            val aliases = buildList {
                add(raw)
                // Alias kode model Qualcomm SM8xxx / Adreno 7xx-8xx ke snapdragon_8
                if (raw.contains("sm8") || raw.contains("adreno 7") || raw.contains("adreno (tm) 7") ||
                    raw.contains("adreno 8") || raw.contains("adreno (tm) 8") || raw.contains("qcom")
                ) {
                    add("snapdragon_8")
                }
                // Alias kode model Qualcomm SM7xxx / Adreno 6xx ke snapdragon_7
                if (raw.contains("sm7") || raw.contains("adreno 6") || raw.contains("adreno (tm) 6")) {
                    add("snapdragon_7")
                }
                // Alias MediaTek Dimensity 8xxx / 9xxx
                if (raw.contains("mt689") || raw.contains("dimensity 8")) {
                    add("dimensity_8")
                }
                if (raw.contains("mt698") || raw.contains("mt699") || raw.contains("dimensity 9")) {
                    add("dimensity_9")
                    add("dimensity_8")
                }
                // Alias Google Tensor / Exynos / Emulator x86_64
                if (raw.contains("tensor") || raw.contains("exynos 2") || raw.contains("ranchu") || raw.contains("goldfish")) {
                    add("snapdragon_7")
                    add("dimensity_8")
                }
            }
            return aliases.joinToString(" ")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
        }
    }
}
