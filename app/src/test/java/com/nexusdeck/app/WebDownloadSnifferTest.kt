package com.nexusdeck.app

import com.nexusdeck.app.core.downloader.SniffedDownload
import com.nexusdeck.app.core.downloader.WebAdBlocker
import com.nexusdeck.app.core.downloader.WebDownloadSniffer
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDownloadSnifferTest {

    @Test
    fun testGameDownloadDetection_gameExtensionsVsNormalWebPages() {
        // Game ROM extensions should be detected
        assertTrue(WebDownloadSniffer.isGameDownload("https://myrient.erista.me/files/Redump/PS2/Tekken5.iso"))
        assertTrue(WebDownloadSniffer.isGameDownload("https://cdromance.org/download/crisis_core.cso"))
        assertTrue(WebDownloadSniffer.isGameDownload("https://vimm.net/download/?mediaId=123", contentDisposition = "attachment; filename=\"Crash.chd\""))
        assertTrue(WebDownloadSniffer.isGameDownload("https://archive.org/download/nintendo/pokemon.gba"))
        assertTrue(WebDownloadSniffer.isGameDownload("https://nopaystation.com/download/game.pkg"))
        assertTrue(WebDownloadSniffer.isGameDownload("https://myrient.erista.me/files/No-Intro/GBA/Zelda.zip"))
        assertTrue(WebDownloadSniffer.isGameDownload("https://example.com/dump/bios.bin"))

        // Standard web assets & pages should NOT be intercepted
        assertFalse(WebDownloadSniffer.isGameDownload("https://cdromance.org/ps2-iso/tekken-5/"))
        assertFalse(WebDownloadSniffer.isGameDownload("https://vimm.net/vault/PS2"))
        assertFalse(WebDownloadSniffer.isGameDownload("https://myrient.erista.me/style.css"))
        assertFalse(WebDownloadSniffer.isGameDownload("https://cdromance.org/logo.png"))
        assertFalse(WebDownloadSniffer.isGameDownload("https://archive.org/details/software"))
    }

    @Test
    fun testFileNameExtraction_contentDispositionAndUrl() {
        // From URL path with URL-encoding
        val name1 = WebDownloadSniffer.extractFileName("https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%20Portable/Crisis%20Core%20-%20Final%20Fantasy%20VII.zip")
        assertEquals("Crisis Core - Final Fantasy VII.zip", name1)

        // From Content-Disposition header
        val name2 = WebDownloadSniffer.extractFileName(
            url = "https://vimm.net/download/?mediaId=999",
            contentDisposition = "attachment; filename=\"God of War - Ghost of Sparta.cso\""
        )
        assertEquals("God of War - Ghost of Sparta.cso", name2)

        // From UTF-8 encoded Content-Disposition
        val name3 = WebDownloadSniffer.extractFileName(
            url = "https://example.com/dl",
            contentDisposition = "attachment; filename*=UTF-8''Final%20Fantasy%20Tactics.iso"
        )
        assertEquals("Final Fantasy Tactics.iso", name3)
    }

    @Test
    fun testPlatformInference_accuracy() {
        // Direct extension mapping
        assertEquals(GamePlatform.PSP, WebDownloadSniffer.inferPlatform("https://example.com/dl", "tekken6.cso"))
        assertEquals(GamePlatform.NINTENDO, WebDownloadSniffer.inferPlatform("https://example.com/dl", "pokemon.gba"))
        assertEquals(GamePlatform.PS3, WebDownloadSniffer.inferPlatform("https://example.com/dl", "persona5.pkg"))

        // Keyword in filename
        assertEquals(GamePlatform.PS2, WebDownloadSniffer.inferPlatform("https://example.com/dl", "Silent_Hill_2_PlayStation_2.iso"))
        assertEquals(GamePlatform.PSP, WebDownloadSniffer.inferPlatform("https://example.com/dl", "Grand_Theft_Auto_Vice_City_Stories_PSP.iso"))
        assertEquals(GamePlatform.PC, WebDownloadSniffer.inferPlatform("https://example.com/dl", "Fallout_New_Vegas_PC_Windows.zip"))
        assertEquals(GamePlatform.NINTENDO, WebDownloadSniffer.inferPlatform("https://example.com/dl", "Mario_Kart_GameCube_Nintendo.iso"))
    }

    @Test
    fun testAdBlocker_blocksSuspiciousDomains() {
        assertTrue(WebAdBlocker.isAdUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(WebAdBlocker.isAdUrl("https://popads.net/serve.js"))
        assertTrue(WebAdBlocker.isAdUrl("https://adsterra.com/click?id=123"))
        assertTrue(WebAdBlocker.isAdUrl("https://bet365.com/landing"))
        assertTrue(WebAdBlocker.isAdUrl("https://propellerads.com/zone"))
        assertTrue(WebAdBlocker.isAdUrl("https://example.com/popunder.js"))

        // Trusted ROM site URLs should NEVER be blocked
        assertFalse(WebAdBlocker.isAdUrl("https://myrient.erista.me/files/No-Intro/"))
        assertFalse(WebAdBlocker.isAdUrl("https://cdromance.org/ps2-iso/"))
        assertFalse(WebAdBlocker.isAdUrl("https://vimm.net/vault/"))
        assertFalse(WebAdBlocker.isAdUrl("https://nopaystation.com/search"))
        assertFalse(WebAdBlocker.isAdUrl("https://archive.org/details/software"))
    }

    @Test
    fun testSniffer_createsValidSniffedDownloadAndGameItem() {
        val sniffed = WebDownloadSniffer.sniffDownload(
            url = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%20Portable/Crisis%20Core%20-%20Final%20Fantasy%20VII.zip",
            contentLength = 1048576000L // ~1000 MB
        )

        assertNotNull(sniffed)
        assertEquals("Crisis Core - Final Fantasy VII.zip", sniffed!!.fileName)
        assertEquals(GamePlatform.PSP, sniffed.platform)
        assertEquals(TargetEmulator.PPSSPP, sniffed.targetEmulator)
        assertEquals("1000 MB", sniffed.fileSizeLabel)

        val gameItem = sniffed.toGameItem()
        assertEquals("Crisis Core - Final Fantasy VII", gameItem.title)
        assertEquals(GamePlatform.PSP, gameItem.platform)
        assertEquals(TargetEmulator.PPSSPP, gameItem.targetEmulator)
        assertEquals("1000 MB", gameItem.fileSizeLabel)
        assertTrue(gameItem.isWebResult)
        assertTrue(gameItem.sourceRepositoryName.contains("myrient.erista.me"))
    }

    @Test
    fun testSniffer_ignoresNonGameUrl() {
        val nonGame = WebDownloadSniffer.sniffDownload(
            url = "https://cdromance.org/ps2-iso/index.html"
        )
        assertNull(nonGame)
    }
}
