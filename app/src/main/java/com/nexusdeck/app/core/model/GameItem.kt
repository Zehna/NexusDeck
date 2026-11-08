package com.nexusdeck.app.core.model

enum class GamePlatform(
    val displayName: String,
    val filterLabel: String,
    val defaultExtension: String
) {
    PSP("PSP", "PSP", "iso"),
    NINTENDO("Nintendo", "Nintendo / Retro", "gba"),
    PS2("PS2", "PlayStation 2", "iso"),
    PC("PC", "PC Games", "zip"),
    PS3("PS3", "PlayStation 3", "iso")
}

enum class TargetEmulator(val displayName: String) {
    PPSSPP("PPSSPP Core"),
    Lemuroid("Lemuroid Core"),
    NexusRetroCore("Lemuroid Core"),
    GameNative("GameNative"),
    ARMSX2("ARMSX2 / NetherSX2"),
    ARMSX3("ARMSX3"),
    MelonDS("melonDS"),
    Lime3DS("Lime3DS"),
    Sudachi("Sudachi")
}

enum class EngineExecutionMode(
    val label: String,
    val isEmbedded: Boolean,
    val description: String
) {
    IN_APP_PLAYABLE(
        label = "In-App Playable",
        isEmbedded = true,
        description = "Dimainkan langsung di dalam NexusDeck dengan embedded core & touch joystick."
    ),
    NATIVE_HANDOFF(
        label = "Native Engine Handoff",
        isEmbedded = false,
        description = "NexusDeck menginjeksi driver Mesa Turnip kustom & menjalankan engine target."
    )
}

data class GameItem(
    val id: String,
    val title: String,
    val platform: GamePlatform,
    val coverUrl: String,
    val minRamGb: Int,
    val recommendedSocKeywords: List<String>,
    val targetEmulator: TargetEmulator,
    val fileSizeLabel: String = "1.2 GB",
    val downloadUrl: String = "",
    val isWebResult: Boolean = false,
    val sourceRepositoryName: String = "Nexus Archive"
) {
    val executionMode: EngineExecutionMode
        get() = when (platform) {
            GamePlatform.NINTENDO -> EngineExecutionMode.IN_APP_PLAYABLE
            GamePlatform.PSP, GamePlatform.PS2, GamePlatform.PC, GamePlatform.PS3 -> EngineExecutionMode.NATIVE_HANDOFF
        }
}
