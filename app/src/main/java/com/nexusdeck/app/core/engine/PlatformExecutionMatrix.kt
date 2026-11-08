package com.nexusdeck.app.core.engine

import com.nexusdeck.app.core.model.GamePlatform

/**
 * Platform Execution Matrix - Matriks Lengkap Aturan Eksekusi Per Konsol di NexusDeck.
 * Mengklasifikasikan konsol ke dalam 4 jalur eksekusi yang jelas:
 * 1. Nintendo Retro: 100% In-App WASM Player (Tanpa APK luar, Tanpa BIOS).
 * 2. PSP: Engine PPSSPP (Tanpa BIOS, Direct Boot PpssppActivity).
 * 3. PC: Engine GameNative / Winlator (Tanpa BIOS, DXVK + Turnip).
 * 4. PlayStation 1 & 2 (PS1 & PS2): Engine NetherSX2 / DuckStation (WAJIB BIOS scph1001/scph39001).
 * 5. PlayStation 3 (PS3): Engine Ringan APS3e (~26 MB) / ARMSX3 (WAJIB FIRMWARE SONY PS3UPDAT.PUP).
 */
enum class ExecutionType(val displayName: String) {
    IN_APP_WASM("100% In-App (WebAssembly Player)"),
    NATIVE_ENGINE_NO_BIOS("Native Engine Handoff (Tanpa BIOS)"),
    NATIVE_ENGINE_REQUIRES_BIOS("Native Engine + Wajib BIOS"),
    NATIVE_ENGINE_REQUIRES_FIRMWARE("Native Engine + Wajib Sony Firmware")
}

data class PlatformExecutionRule(
    val platform: GamePlatform,
    val executionType: ExecutionType,
    val requiresBios: Boolean,
    val requiresFirmware: Boolean,
    val requiredFileLabel: String?,
    val defaultEngineId: String,
    val defaultEngineName: String,
    val description: String
) {
    val isEmbeddedWasm: Boolean get() = executionType == ExecutionType.IN_APP_WASM
}

object PlatformExecutionMatrix {

    val nintendoRule = PlatformExecutionRule(
        platform = GamePlatform.NINTENDO,
        executionType = ExecutionType.IN_APP_WASM,
        requiresBios = false,
        requiresFirmware = false,
        requiredFileLabel = null,
        defaultEngineId = "emulatorjs_wasm",
        defaultEngineName = "EmulatorJS WASM",
        description = "100% In-App Player via EmulatorJS WebAssembly tanpa unduh APK luar atau BIOS."
    )

    val pspRule = PlatformExecutionRule(
        platform = GamePlatform.PSP,
        executionType = ExecutionType.NATIVE_ENGINE_NO_BIOS,
        requiresBios = false,
        requiresFirmware = false,
        requiredFileLabel = null,
        defaultEngineId = "ppsspp",
        defaultEngineName = "PPSSPP",
        description = "Engine resmi PPSSPP dengan Intent Component langsung ke PpssppActivity."
    )

    val pcRule = PlatformExecutionRule(
        platform = GamePlatform.PC,
        executionType = ExecutionType.NATIVE_ENGINE_NO_BIOS,
        requiresBios = false,
        requiresFirmware = false,
        requiredFileLabel = null,
        defaultEngineId = "gamenative",
        defaultEngineName = "GameNative / Winlator",
        description = "Engine wrapper PC x86/x64 dengan Turnip GPU driver & profil DXVK."
    )

    val ps1Rule = PlatformExecutionRule(
        platform = GamePlatform.PS1,
        executionType = ExecutionType.NATIVE_ENGINE_REQUIRES_BIOS,
        requiresBios = true,
        requiresFirmware = false,
        requiredFileLabel = "scph1001.bin (PS1 BIOS)",
        defaultEngineId = "duckstation",
        defaultEngineName = "DuckStation",
        description = "Engine DuckStation dengan Direct Boot EmulationActivity dan verifikasi BIOS SCPH-1001 otomatis."
    )

    val ps2Rule = PlatformExecutionRule(
        platform = GamePlatform.PS2,
        executionType = ExecutionType.NATIVE_ENGINE_REQUIRES_BIOS,
        requiresBios = true,
        requiresFirmware = false,
        requiredFileLabel = "scph39001.bin (PS2 BIOS)",
        defaultEngineId = "nethersx2",
        defaultEngineName = "NetherSX2 Classic",
        description = "Engine NetherSX2 dengan Direct Boot EmulationActivity dan verifikasi BIOS otomatis."
    )

    val ps3Rule = PlatformExecutionRule(
        platform = GamePlatform.PS3,
        executionType = ExecutionType.NATIVE_ENGINE_REQUIRES_FIRMWARE,
        requiresBios = false,
        requiresFirmware = true,
        requiredFileLabel = "PS3UPDAT.PUP (Sony Firmware Resmi)",
        defaultEngineId = "aps3e",
        defaultEngineName = "APS3e (~26 MB)",
        description = "Engine ringan APS3e (~26 MB) dengan verifikasi Firmware Resmi Sony CDN."
    )

    private val rulesMap: Map<GamePlatform, PlatformExecutionRule> = mapOf(
        GamePlatform.PS1 to ps1Rule,
        GamePlatform.NINTENDO to nintendoRule,
        GamePlatform.PSP to pspRule,
        GamePlatform.PC to pcRule,
        GamePlatform.PS2 to ps2Rule,
        GamePlatform.PS3 to ps3Rule
    )

    fun getRule(platform: GamePlatform): PlatformExecutionRule {
        return rulesMap[platform] ?: nintendoRule
    }

    fun isEmbeddedWasm(platform: GamePlatform): Boolean {
        return getRule(platform).isEmbeddedWasm
    }

    fun requiresBios(platform: GamePlatform): Boolean {
        return getRule(platform).requiresBios
    }

    fun requiresFirmware(platform: GamePlatform): Boolean {
        return getRule(platform).requiresFirmware
    }
}
