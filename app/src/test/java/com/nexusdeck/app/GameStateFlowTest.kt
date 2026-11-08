package com.nexusdeck.app

import com.nexusdeck.app.core.engine.NexusEmbeddedCoreRunner
import com.nexusdeck.app.core.engine.VirtualInputState
import com.nexusdeck.app.core.model.EngineExecutionMode
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.TargetEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateFlowTest {

    @Test
    fun testGameStateTransitions() {
        val stateFlow = MutableStateFlow<GameState>(GameState.NotInstalled)
        assertEquals(GameState.NotInstalled, stateFlow.value)

        // 1. Downloading
        stateFlow.value = GameState.Downloading(progress = 0.45f)
        assertTrue(stateFlow.value is GameState.Downloading)
        assertEquals(0.45f, (stateFlow.value as GameState.Downloading).progress, 0.001f)

        // 2. Extracting
        stateFlow.value = GameState.Extracting
        assertEquals(GameState.Extracting, stateFlow.value)

        // 3. Installed
        val expectedPath = "/games/psp/psp_tekken_6.iso"
        stateFlow.value = GameState.Installed(romPath = expectedPath)
        assertTrue(stateFlow.value is GameState.Installed)
        assertEquals(expectedPath, (stateFlow.value as GameState.Installed).romPath)
    }

    @Test
    fun testDualEngineExecutionModeCategorization() {
        val pspGame = GameItem(
            id = "psp_gow",
            title = "God of War",
            platform = GamePlatform.PSP,
            coverUrl = "",
            minRamGb = 3,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.PPSSPP
        )

        val nintendoGame = GameItem(
            id = "gba_pokemon",
            title = "Pokemon Emerald",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 2,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.NexusRetroCore
        )

        val ps2Game = GameItem(
            id = "ps2_gow2",
            title = "God of War II",
            platform = GamePlatform.PS2,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.ARMSX2
        )

        val pcGame = GameItem(
            id = "pc_fallout",
            title = "Fallout",
            platform = GamePlatform.PC,
            coverUrl = "",
            minRamGb = 6,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.GameNative
        )

        // Path A: Embedded Core for PSP & Nintendo
        assertEquals(EngineExecutionMode.IN_APP_PLAYABLE, pspGame.executionMode)
        assertEquals(EngineExecutionMode.IN_APP_PLAYABLE, nintendoGame.executionMode)
        assertTrue(pspGame.executionMode.isEmbedded)

        // Path B: Heavy Native Handoff for PS2, PS3, and PC
        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, ps2Game.executionMode)
        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, pcGame.executionMode)
        assertFalse(ps2Game.executionMode.isEmbedded)
    }

    @Test
    fun testNexusEmbeddedCoreRunner_lifecycleAndStateBuffer() = runBlocking {
        val runner = NexusEmbeddedCoreRunner()

        val sampleGame = GameItem(
            id = "psp_tekken_6",
            title = "Tekken 6",
            platform = GamePlatform.PSP,
            coverUrl = "",
            minRamGb = 3,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.PPSSPP
        )

        // 1. Load ROM
        val loaded = runner.loadRom(sampleGame, "/storage/emulated/0/games/psp/tekken6.iso")
        assertTrue(loaded)
        assertTrue(runner.telemetry.value.isRunning)
        assertTrue(runner.telemetry.value.coreName.contains("PSP Embedded Core"))

        // 2. Send input
        runner.sendInput(
            VirtualInputState(
                analogX = 0.75f,
                analogY = -0.25f,
                buttonA = true
            )
        )

        // 3. Save State
        val stateBytes = runner.saveState()
        assertTrue("State data should not be empty", stateBytes.isNotEmpty())

        // 4. Load State
        val stateLoaded = runner.loadState(stateBytes)
        assertTrue(stateLoaded)

        // 5. Pause & Stop
        runner.pause()
        assertFalse(runner.telemetry.value.isRunning)

        runner.resume()
        assertTrue(runner.telemetry.value.isRunning)

        runner.stop()
        assertFalse(runner.telemetry.value.isRunning)
    }
}
