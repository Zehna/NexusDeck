package com.nexusdeck.app.core.model

sealed class GameState {
    data object NotInstalled : GameState()
    data class Downloading(val progress: Float) : GameState()
    data object Extracting : GameState()
    data class Installed(val romPath: String) : GameState()
}
