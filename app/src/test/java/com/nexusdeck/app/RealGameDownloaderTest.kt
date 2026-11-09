package com.nexusdeck.app

import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for the v1.1.7 PC-ZIP fix (RealGameDownloader.shouldExtractZip).
 *
 * Regression: Freedoom (PC, 42 MB zip dari GitHub) selalu gagal di v1.1.6
 * dengan "File ZIP tidak berisi ROM .zip yang valid" — padahal unduhannya
 * sukses. Penyebabnya: extractRomFromZip mencari entri ".zip" DI DALAM
 * zip, sementara zip Freedoom berisi .wad, bukan .zip. Untuk game PC,
 * ZIP-nya sendiri adalah paket game-nya dan tidak boleh diekstrak.
 * Semua tes di sini murni/offline.
 */
class RealGameDownloaderTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nexusdeck-1-1-7-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun game(platform: GamePlatform): GameItem = GameItem(
        id = "test_${platform.name.lowercase()}",
        title = "Test ${platform.displayName}",
        platform = platform,
        coverUrl = "",
        minRamGb = 2,
        recommendedSocKeywords = emptyList(),
        targetEmulator = TargetEmulator.GameNative
    )

    @Test
    fun `PC zip is the game package itself, never extracted`() {
        assertFalse(RealGameDownloader.shouldExtractZip(game(GamePlatform.PC)))
    }

    @Test
    fun `ROM platforms still extract zips`() {
        assertTrue(RealGameDownloader.shouldExtractZip(game(GamePlatform.NINTENDO)))
        assertTrue(RealGameDownloader.shouldExtractZip(game(GamePlatform.PS1)))
        assertTrue(RealGameDownloader.shouldExtractZip(game(GamePlatform.PSP)))
        assertTrue(RealGameDownloader.shouldExtractZip(game(GamePlatform.PS2)))
        assertTrue(RealGameDownloader.shouldExtractZip(game(GamePlatform.PS3)))
    }

    @Test
    fun `freedoom-style zip with only wad entries has no inner zip`() {
        // Bukti mode kegagalan v1.1.6: zip berisi freedoom1.wad /
        // freedoom2.wad (tanpa entri .zip) membuat extractRomFromZip
        // mengembalikan null — inilah yang memicu error palsu itu.
        val zipFile = File(tempDir, "freedoom-0.12.1.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("freedoom1.wad"))
            zip.write(ByteArray(1024) { 0x41 })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("freedoom2.wad"))
            zip.write(ByteArray(1024) { 0x42 })
            zip.closeEntry()
        }
        assertNull(
            RealGameDownloader.extractRomFromZip(
                zipFile, "zip", File(tempDir, "out"), 1L
            )
        )
    }
}
