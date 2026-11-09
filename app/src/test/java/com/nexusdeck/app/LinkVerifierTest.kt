package com.nexusdeck.app

import com.nexusdeck.app.core.downloader.LinkVerifier
import com.nexusdeck.app.core.downloader.RealGameDownloader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for v1.1.6 automatic link verification (LinkVerifier) and ZIP
 * ROM extraction (RealGameDownloader). All tests here are pure/offline:
 * evaluateHeaders takes headers as values (no network), and the ZIP tests
 * operate on files created in a temp dir. The live network half of
 * LinkVerifier.verify() (HEAD + 512-byte probe) is exercised by CI against
 * the real network, not here.
 */
class LinkVerifierTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nexusdeck-linkverifier-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ---------- LinkVerifier.evaluateHeaders ----------

    @Test
    fun testEvaluateHeaders_validBinaryLink_isOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/game.gba",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 8L * 1024L * 1024L,
            minBytes = 256L * 1024L
        )
        assertTrue(result.ok)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun testEvaluateHeaders_zipContentType_isOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/game.zip",
            httpStatus = 200,
            contentType = "application/zip",
            contentLength = 16L * 1024L * 1024L,
            minBytes = 256L * 1024L
        )
        assertTrue(result.ok)
    }

    @Test
    fun testEvaluateHeaders_deadLink503_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://archive.org/download/ps1-chd-collection/castlevania_sotn.chd",
            httpStatus = 503,
            contentType = "text/html",
            contentLength = 1024L,
            minBytes = 1024L * 1024L
        )
        assertFalse(result.ok)
        assertTrue(result.reason.contains("503"))
    }

    @Test
    fun testEvaluateHeaders_htmlPage_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/game.gba",
            httpStatus = 200,
            contentType = "text/html; charset=utf-8",
            contentLength = 42L * 1024L,
            minBytes = 256L * 1024L
        )
        assertFalse(result.ok)
        assertTrue(result.reason.contains("halaman web"))
    }

    @Test
    fun testEvaluateHeaders_directoryUrl_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://myrient.erista.me/files/No-Intro/Nintendo%20-%20Game%20Boy%20Advance/",
            httpStatus = 200,
            contentType = "text/html",
            contentLength = 12L * 1024L,
            minBytes = 256L * 1024L
        )
        assertFalse(result.ok)
        assertTrue(result.reason.contains("direktori"))
    }

    @Test
    fun testEvaluateHeaders_tooSmall_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/game.iso",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 100L,
            minBytes = 1024L * 1024L
        )
        assertFalse(result.ok)
    }

    @Test
    fun testEvaluateHeaders_notFound_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/missing.gba",
            httpStatus = 404,
            contentType = "text/html",
            contentLength = 512L,
            minBytes = 256L * 1024L
        )
        assertFalse(result.ok)
        assertTrue(result.reason.contains("404"))
    }

    @Test
    fun testEvaluateHeaders_blankUrl_isNotOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "",
            httpStatus = -1,
            contentType = null,
            contentLength = -1L,
            minBytes = 256L * 1024L
        )
        assertFalse(result.ok)
    }

    @Test
    fun testEvaluateHeaders_unknownTypeWithSufficientSize_isOk() {
        val result = LinkVerifier.evaluateHeaders(
            url = "https://example.com/files/game.gba",
            httpStatus = 200,
            contentType = null,
            contentLength = 8L * 1024L * 1024L,
            minBytes = 256L * 1024L
        )
        assertTrue(result.ok)
    }

    // ---------- RealGameDownloader no-progress limit ----------

    @Test
    fun testNoProgressTickLimit_isThirtySeconds() {
        // 200 ticks x 150 ms polling = 30 s. v1.1.5 used 10-12 ticks (~1.5 s).
        assertEquals(200, RealGameDownloader.NO_PROGRESS_TICK_LIMIT)
    }

    // ---------- ZIP detection & extraction ----------

    private fun writeZip(zipFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun testIsZipFile_detectsZipMagic() {
        val zip = File(tempDir, "game.zip")
        writeZip(zip, mapOf("game.gba" to ByteArray(1024) { 0x01 }))
        assertTrue(RealGameDownloader.isZipFile(zip))

        val text = File(tempDir, "page.html")
        text.writeText("<!DOCTYPE html><html></html>")
        assertFalse(RealGameDownloader.isZipFile(text))
    }

    @Test
    fun testExtractRomFromZip_extractsMatchingRom() {
        val romBytes = ByteArray(300 * 1024) { 0x42 }
        val zip = File(tempDir, "mario.zip")
        writeZip(
            zip,
            mapOf(
                "readme.txt" to "hello".toByteArray(),
                "Super Mario Advance 4.gba" to romBytes
            )
        )
        val destDir = File(tempDir, "out").apply { mkdirs() }
        val extracted = RealGameDownloader.extractRomFromZip(
            zipFile = zip,
            wantedExtension = "gba",
            destDir = destDir,
            minBytes = 256L * 1024L
        )
        assertNotNull(extracted)
        assertEquals(300L * 1024L, extracted!!.length())
        assertTrue(extracted.readBytes().contentEquals(romBytes))
    }

    @Test
    fun testExtractRomFromZip_noMatchingEntry_returnsNull() {
        val zip = File(tempDir, "empty.zip")
        writeZip(zip, mapOf("readme.txt" to "hello".toByteArray()))
        val destDir = File(tempDir, "out").apply { mkdirs() }
        assertNull(
            RealGameDownloader.extractRomFromZip(
                zipFile = zip,
                wantedExtension = "gba",
                destDir = destDir,
                minBytes = 256L * 1024L
            )
        )
    }

    @Test
    fun testExtractRomFromZip_pathTraversalEntry_staysInsideDestDir() {
        // Output filename is fixed by the extractor (never derived from the
        // entry name), so a "../evil.gba" entry cannot escape destDir.
        val romBytes = ByteArray(300 * 1024) { 0x07 }
        val zip = File(tempDir, "evil.zip")
        writeZip(zip, mapOf("../evil.gba" to romBytes))
        val destDir = File(tempDir, "out").apply { mkdirs() }
        val extracted = RealGameDownloader.extractRomFromZip(
            zipFile = zip,
            wantedExtension = "gba",
            destDir = destDir,
            minBytes = 256L * 1024L
        )
        assertNotNull(extracted)
        assertTrue(extracted!!.canonicalPath.startsWith(destDir.canonicalPath))
        assertFalse(File(tempDir, "evil.gba").exists())
    }

    @Test
    fun testExtractRomFromZip_entryBelowMinimum_isRejected() {
        val zip = File(tempDir, "tiny.zip")
        writeZip(zip, mapOf("game.gba" to ByteArray(1024) { 0x01 }))
        val destDir = File(tempDir, "out").apply { mkdirs() }
        assertNull(
            RealGameDownloader.extractRomFromZip(
                zipFile = zip,
                wantedExtension = "gba",
                destDir = destDir,
                minBytes = 256L * 1024L
            )
        )
    }
}
