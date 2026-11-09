package com.nexusdeck.app

import com.nexusdeck.app.core.downloader.LinkVerifier
import com.nexusdeck.app.core.downloader.LinkVerifier.LinkVerdict
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

    // ---------- LinkVerifier.evaluateHeadersDetailed (v1.1.9) ----------
    // Tabel putusan §5: hanya bukti keras (404/410, HTML terkonfirmasi,
    // file terlalu kecil) yang jadi Dead; semua yang lain yang bukan
    // Alive jadi Unknown — satu 503 tidak boleh menyembunyikan game.

    @Test
    fun testDetailed_404_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 404,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("http-404", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_410_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 410,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("http-410", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_503_isUnknownWithRetryAfter() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 503,
            contentType = "text/html",
            contentLength = 1024L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = 120L
        )
        assertTrue(verdict is LinkVerdict.Unknown)
        val unknown = verdict as LinkVerdict.Unknown
        assertEquals("http-503", unknown.reason)
        assertEquals(120L, unknown.retryAfterSeconds)
    }

    @Test
    fun testDetailed_429_isUnknown() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 429,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = 30L
        )
        assertTrue(verdict is LinkVerdict.Unknown)
    }

    @Test
    fun testDetailed_401_and_403_areUnknown() {
        for (status in listOf(401, 403)) {
            val verdict = LinkVerifier.evaluateHeadersDetailed(
                url = "https://example.com/files/game.chd",
                httpStatus = status,
                contentType = null,
                contentLength = -1L,
                minBytes = 1024L * 1024L,
                htmlConfirmed = false,
                retryAfterSeconds = null
            )
            assertTrue("status $status harus Unknown", verdict is LinkVerdict.Unknown)
        }
    }

    @Test
    fun testDetailed_405_isUnknownHeadUnsupported() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 405,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Unknown)
        assertEquals("head-tidak-didukung", (verdict as LinkVerdict.Unknown).reason)
    }

    @Test
    fun testDetailed_htmlConfirmed_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.zip",
            httpStatus = 200,
            contentType = "text/html; charset=utf-8",
            contentLength = 42L * 1024L,
            minBytes = 256L * 1024L,
            htmlConfirmed = true,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("html-landing", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_htmlUnconfirmed_isUnknown() {
        // HEAD saja bilang text/html, tapi probe belum mengonfirmasi —
        // bisa jadi redirector unduhan; jangan langsung Dead.
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.zip",
            httpStatus = 200,
            contentType = "text/html; charset=utf-8",
            contentLength = 42L * 1024L,
            minBytes = 256L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Unknown)
        assertEquals("html-perlu-konfirmasi", (verdict as LinkVerdict.Unknown).reason)
    }

    @Test
    fun testDetailed_validBinary_isAlive() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 24_121_671L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Alive)
        val alive = verdict as LinkVerdict.Alive
        assertEquals(200, alive.httpStatus)
        assertEquals(24_121_671L, alive.contentLength)
        assertEquals("https://example.com/files/game.chd", alive.finalUrl)
    }

    @Test
    fun testDetailed_partial206_isAlive() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 206,
            contentType = "application/octet-stream",
            contentLength = 512L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        // 206 adalah jawaban Range probe; Content-Length-nya hanya
        // 512 byte probe, jadi aturan minimum tidak berlaku di sini —
        // verdict tetap Alive karena header-nya valid.
        assertTrue(verdict is LinkVerdict.Alive)
    }

    @Test
    fun testDetailed_tooSmallFile_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 1024L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("file-terlalu-kecil", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_blankUrl_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "",
            httpStatus = -1,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("url-kosong", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_directoryUrl_isDead() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/",
            httpStatus = 200,
            contentType = "text/html",
            contentLength = 2048L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("direktori", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testDetailed_unreachable_isUnknown() {
        val verdict = LinkVerifier.evaluateHeadersDetailed(
            url = "https://example.com/files/game.chd",
            httpStatus = -1,
            contentType = null,
            contentLength = -1L,
            minBytes = 1024L * 1024L,
            htmlConfirmed = false,
            retryAfterSeconds = null
        )
        assertTrue(verdict is LinkVerdict.Unknown)
        assertEquals("tidak-terjangkau", (verdict as LinkVerdict.Unknown).reason)
    }

    @Test
    fun testEvaluateHeaders_tekkenReplacementLink_isOk() {
        // v1.1.8: item ps1-chd-collection dihapus; pengganti dari
        // ps1-rip-chd-ck terverifikasi (HTTP 200, application/octet-stream,
        // 24.121.671 byte, magic CHD "MComprHD").
        val result = LinkVerifier.evaluateHeaders(
            url = "https://archive.org/download/ps1-rip-chd-ck/Tekken%203.chd",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 24121671L,
            minBytes = 1024L * 1024L
        )
        assertTrue(result.ok)
    }

    @Test
    fun testEvaluateHeaders_castlevaniaReplacementLink_isOk() {
        // v1.1.8: pengganti Castlevania dari ps1-rip-chd-ck terverifikasi
        // (HTTP 200, application/octet-stream, 57.488.209 byte, magic CHD).
        val result = LinkVerifier.evaluateHeaders(
            url = "https://archive.org/download/ps1-rip-chd-ck/Castlevania%20-%20Symphony%20Of%20The%20Night.chd",
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 57488209L,
            minBytes = 1024L * 1024L
        )
        assertTrue(result.ok)
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
