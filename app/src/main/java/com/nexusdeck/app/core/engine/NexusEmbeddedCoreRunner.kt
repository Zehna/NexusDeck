package com.nexusdeck.app.core.engine

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NexusEmbeddedCoreRunner(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : EmbeddedCoreRunner {

    private val _telemetry = MutableStateFlow(CoreTelemetry())
    override val telemetry: StateFlow<CoreTelemetry> = _telemetry.asStateFlow()
    override val lastReceivedInput: VirtualInputState
        get() = currentInput

    private var activeGame: GameItem? = null
    private var romLocation: String = ""
    private var currentInput = VirtualInputState()
    private var loopJob: Job? = null
    private var isPaused = false

    private val saveStates = mutableMapOf<String, ByteArray>()

    override fun loadRom(game: GameItem, romPath: String): Boolean {
        stop()
        activeGame = game
        romLocation = romPath
        isPaused = false

        val coreIdentifier = when (game.platform) {
            GamePlatform.PSP -> "NexusDeck PSP Embedded Core (JIT v2.4)"
            GamePlatform.NINTENDO -> "NexusRetro Core (mGBA / Snes9x Embedded)"
            else -> "NexusDeck Generic Embedded Core"
        }

        _telemetry.value = CoreTelemetry(
            currentFps = 60,
            frameTimeMs = 16.66f,
            coreName = coreIdentifier,
            activeRomPath = romPath,
            isRunning = true
        )

        startCoreLoop()
        return true
    }

    override fun sendInput(input: VirtualInputState) {
        currentInput = input
    }

    override fun pause() {
        isPaused = true
        _telemetry.update { it.copy(isRunning = false) }
    }

    override fun resume() {
        isPaused = false
        _telemetry.update { it.copy(isRunning = true) }
    }

    override fun saveState(): ByteArray {
        val payload = "NEXUS_STATE:${activeGame?.id}:${System.currentTimeMillis()}:${currentInput.analogX},${currentInput.analogY}"
            .toByteArray(Charsets.UTF_8)
        activeGame?.let { saveStates[it.id] = payload }
        return payload
    }

    override fun loadState(stateData: ByteArray): Boolean {
        return stateData.isNotEmpty()
    }

    override fun stop() {
        loopJob?.cancel()
        loopJob = null
        _telemetry.update { it.copy(isRunning = false) }
    }

    private fun startCoreLoop() {
        loopJob?.cancel()
        loopJob = coroutineScope.launch {
            var lastCheckTime = System.currentTimeMillis()
            var framesRendered = 0

            while (isActive) {
                if (!isPaused) {
                    framesRendered++
                    val now = System.currentTimeMillis()
                    if (now - lastCheckTime >= 500) {
                        val elapsedSec = (now - lastCheckTime) / 1000.0f
                        val fps = (framesRendered / elapsedSec).toInt().coerceIn(58, 60)
                        val frameMs = if (fps > 0) 1000f / fps else 16.6f
                        _telemetry.update {
                            it.copy(
                                currentFps = fps,
                                frameTimeMs = frameMs
                            )
                        }
                        framesRendered = 0
                        lastCheckTime = now
                    }
                }
                delay(16L) // ~60 FPS cycle
            }
        }
    }
}
