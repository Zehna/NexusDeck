package com.nexusdeck.app.core.library

import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryRulesTest {

    @Test
    fun `extensionOf extracts lowercase extension`() {
        assertEquals("chd", GameLibraryRules.extensionOf("Tekken 3.chd"))
        assertEquals("bin", GameLibraryRules.extensionOf("game.BIN"))
        assertEquals("", GameLibraryRules.extensionOf("noextension"))
        assertEquals("", GameLibraryRules.extensionOf("trailing."))
    }

    @Test
    fun `allow-list per platform follows ES-DE`() {
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PS1, "chd"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PS1, "cue"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PS2, "cso"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PSP, "iso"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.NINTENDO, "gba"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.NINTENDO, "nds"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.NINTENDO, "3ds"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PS3, "pkg"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PC, "zip"))
        // Arsip tidak dipindai untuk konsol (bukan format game langsung).
        assertFalse(GameLibraryRules.isSupportedExtension(GamePlatform.PS1, "zip"))
        assertFalse(GameLibraryRules.isSupportedExtension(GamePlatform.PSP, "7z"))
        // Ekstensi sistem lain ditolak.
        assertFalse(GameLibraryRules.isSupportedExtension(GamePlatform.PS1, "gba"))
        assertTrue(GameLibraryRules.isSupportedExtension(GamePlatform.PSP, "chd"))
    }

    @Test
    fun `emulatorFor maps system and Nintendo subfamily`() {
        assertEquals(TargetEmulator.DuckStation, GameLibraryRules.emulatorFor(GamePlatform.PS1, "chd"))
        assertEquals(TargetEmulator.PPSSPP, GameLibraryRules.emulatorFor(GamePlatform.PSP, "iso"))
        assertEquals(TargetEmulator.ARMSX2, GameLibraryRules.emulatorFor(GamePlatform.PS2, "iso"))
        assertEquals(TargetEmulator.ARMSX3, GameLibraryRules.emulatorFor(GamePlatform.PS3, "iso"))
        assertEquals(TargetEmulator.GameNative, GameLibraryRules.emulatorFor(GamePlatform.PC, "zip"))
        assertEquals(TargetEmulator.Lemuroid, GameLibraryRules.emulatorFor(GamePlatform.NINTENDO, "gba"))
        assertEquals(TargetEmulator.MelonDS, GameLibraryRules.emulatorFor(GamePlatform.NINTENDO, "nds"))
        assertEquals(TargetEmulator.Lime3DS, GameLibraryRules.emulatorFor(GamePlatform.NINTENDO, "3ds"))
        assertEquals(TargetEmulator.Lime3DS, GameLibraryRules.emulatorFor(GamePlatform.NINTENDO, "cci"))
    }

    @Test
    fun `cleanDisplayName strips tags and separators`() {
        assertEquals("Tekken 3", GameLibraryRules.cleanDisplayName("Tekken 3 (USA).chd"))
        assertEquals(
            "Legend of Zelda The Minish Cap",
            GameLibraryRules.cleanDisplayName("Legend_of_Zelda_-_The_Minish_Cap_(USA).zip")
        )
        assertEquals(
            "Castlevania Symphony of the Night",
            GameLibraryRules.cleanDisplayName("Castlevania - Symphony of the Night [PSX].bin")
        )
        assertEquals("God of War", GameLibraryRules.cleanDisplayName("God.of.War.iso"))
    }

    @Test
    fun `localGameId is deterministic and prefixed`() {
        val first = GameLibraryRules.localGameId(GamePlatform.PS1, "Tekken 3")
        val second = GameLibraryRules.localGameId(GamePlatform.PS1, "Tekken 3")
        assertEquals(first, second)
        assertEquals("local_ps1_tekken_3", first)
        assertNotEquals(
            first,
            GameLibraryRules.localGameId(GamePlatform.PSP, "Tekken 3")
        )
    }

    @Test
    fun `toGameItem builds a local library entry`() {
        val file = ScannedGameFile(
            contentUri = "content://com.android.externalstorage.documents/tree/primary%3ARoms%2FPS1/document/primary%3ARoms%2FPS1%2FTekken%203.chd",
            fileName = "Tekken 3 (USA).chd",
            displayName = "Tekken 3",
            platform = GamePlatform.PS1,
            targetEmulator = TargetEmulator.DuckStation,
            sizeBytes = 1_500_000_000L
        )
        val item = GameLibraryRules.toGameItem(file)
        assertEquals("local_ps1_tekken_3", item.id)
        assertEquals("Tekken 3", item.title)
        assertEquals(GamePlatform.PS1, item.platform)
        assertEquals(TargetEmulator.DuckStation, item.targetEmulator)
        assertEquals("1.40 GB", item.fileSizeLabel)
        assertFalse(item.isWebResult)
        assertEquals("Pustaka Lokal", item.sourceRepositoryName)
        assertEquals("", item.downloadUrl)
    }

    @Test
    fun `humanSizeLabel formats bytes`() {
        assertEquals("—", GameLibraryRules.humanSizeLabel(0))
        assertEquals("512 KB", GameLibraryRules.humanSizeLabel(512 * 1024L))
        assertEquals("1.5 MB", GameLibraryRules.humanSizeLabel(1_572_864L))
        assertEquals("1.40 GB", GameLibraryRules.humanSizeLabel(1_500_000_000L))
    }
}
