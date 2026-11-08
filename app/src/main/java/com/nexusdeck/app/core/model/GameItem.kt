package com.nexusdeck.app.core.model

enum class GamePlatform(val displayName: String) {
    PC("PC"),
    PS2("PS2"),
    PS3("PS3")
}

enum class TargetEmulator(val displayName: String) {
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
    val targetEmulator: TargetEmulator
)
