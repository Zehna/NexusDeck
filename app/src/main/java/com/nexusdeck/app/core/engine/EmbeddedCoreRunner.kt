package com.nexusdeck.app.core.engine

import com.nexusdeck.app.core.model.GameItem
import kotlinx.coroutines.flow.StateFlow

data class VirtualInputState(
    val analogX: Float = 0f,
    val analogY: Float = 0f,
    val buttonA: Boolean = false,
    val buttonB: Boolean = false,
    val buttonX: Boolean = false,
    val buttonY: Boolean = false,
    val buttonL1: Boolean = false,
    val buttonR1: Boolean = false
)

data class CoreTelemetry(
    val currentFps: Int = 60,
    val frameTimeMs: Float = 16.6f,
    val coreName: String = "NexusDeck Embedded Libretro Core",
    val activeRomPath: String = "",
    val isRunning: Boolean = false
)

interface EmbeddedCoreRunner {
    val telemetry: StateFlow<CoreTelemetry>

    fun loadRom(game: GameItem, romPath: String): Boolean
    fun sendInput(input: VirtualInputState)
    fun pause()
    fun resume()
    fun saveState(): ByteArray
    fun loadState(stateData: ByteArray): Boolean
    fun stop()
}
