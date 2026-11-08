package com.nexusdeck.app.core.model

enum class GamePlatform(val displayName: String, val filterLabel: String) {
    PSP("PSP", "PSP"),
    PS2("PS2", "PlayStation 2"),
    PC("PC", "PC Games"),
    PS3("PS3", "PlayStation 3")
}

enum class TargetEmulator(val displayName: String) {
    PPSSPP("PPSSPP"),
    GameNative("GameNative"),
    ARMSX2("ARMSX2"),
    ARMSX3("ARMSX3")
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
    val isWebResult: Boolean = false
)
