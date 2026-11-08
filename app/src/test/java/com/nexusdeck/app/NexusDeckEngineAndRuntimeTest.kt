package com.nexusdeck.app

import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.engine.EmulatorJsEngine
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.core.engine.ExecutionType
import com.nexusdeck.app.core.engine.PlatformExecutionMatrix
import com.nexusdeck.app.core.model.EngineExecutionMode
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.ui.components.FALLBACK_BROWSER_BUTTON_LABEL
import com.nexusdeck.app.ui.components.formatFriendlyErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Suite Pengujian Otomatis untuk Dynamic Engine Resolver, Offline Runtime,
 * Binary ROM Header Validation, dan Resiliensi Error Dialog di NexusDeck.
 */
class NexusDeckEngineAndRuntimeTest {

    // =========================================================================
    // 1. Uji Resolusi Dynamic GitHub Release API
    // =========================================================================

    @Test
    fun testDynamicGitHubReleaseApi_parsesGameNativeApkAssetUrl() {
        // Simulasi respons JSON rilis nyata dari endpoint GitHub:
        // https://api.github.com/repos/utkarshdalal/GameNative/releases/latest
        val gameNativeReleaseJson = """
            {
              "tag_name": "v1.4.0",
              "name": "GameNative v1.4.0 Release",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "gamenative-v1.4.0-arm64-v8a.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 78241512,
                  "browser_download_url": "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0-arm64-v8a.apk"
                },
                {
                  "name": "source_code.tar.gz",
                  "content_type": "application/gzip",
                  "size": 1234567,
                  "browser_download_url": "https://github.com/utkarshdalal/GameNative/archive/refs/tags/v1.4.0.tar.gz"
                }
              ]
            }
        """.trimIndent()

        val parsedUrl = EngineManager.parseAssetApkUrlFromReleaseJson(gameNativeReleaseJson)
        assertNotNull("URL APK GameNative harus berhasil diekstrak", parsedUrl)
        assertEquals(
            "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0-arm64-v8a.apk",
            parsedUrl
        )
        assertFalse("URL tidak boleh mengarah ke URL hardcoded statis", parsedUrl!!.contains("GameNative.apk"))
        assertTrue("URL harus berakhiran .apk", parsedUrl.endsWith(".apk"))
    }

    @Test
    fun testDynamicGitHubReleaseApi_parsesPpssppOfficialReleaseApk() {
        // Simulasi respons JSON rilis PPSSPP resmi
        val ppssppReleaseJson = """
            {
              "tag_name": "v1.20.4",
              "name": "PPSSPP 1.20.4",
              "assets": [
                {
                  "name": "PPSSPP_v1.20.4_android.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 29817420,
                  "browser_download_url": "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP_v1.20.4_android.apk"
                },
                {
                  "name": "ppsspp-windows-x64.zip",
                  "content_type": "application/zip",
                  "size": 42100000,
                  "browser_download_url": "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/ppsspp-windows-x64.zip"
                }
              ]
            }
        """.trimIndent()

        val parsedUrl = EngineManager.parseAssetApkUrlFromReleaseJson(ppssppReleaseJson)
        assertNotNull(parsedUrl)
        assertEquals(
            "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP_v1.20.4_android.apk",
            parsedUrl
        )
    }

    @Test
    fun testDynamicGitHubReleaseApi_handlesEmptyOrCorruptAssetsGracefully() {
        val emptyAssetsJson = """{"tag_name": "v1.0.0", "assets": []}"""
        val parsedEmpty = EngineManager.parseAssetApkUrlFromReleaseJson(emptyAssetsJson)
        assertNull("Jika tidak ada APK di aset rilis, parser harus mengembalikan null", parsedEmpty)

        val invalidJson = "<html>502 Bad Gateway</html>"
        val parsedInvalid = EngineManager.parseAssetApkUrlFromReleaseJson(invalidJson)
        assertNull("Format non-JSON tidak boleh menyebabkan crash dan mengembalikan null", parsedInvalid)
    }

    // =========================================================================
    // 2. Uji Validasi Header Biner ROM vs Fake HTML Redirect Pages
    // =========================================================================

    @Test
    fun testRomBinaryValidation_rejectsHtmlRedirectResponses() {
        // Simulasi berbagai respons HTML dari server web / proteksi Cloudflare / 404 page
        val htmlPage1 = "<!DOCTYPE html><html lang='en'><head><title>Cloudflare DDoS</title></head><body>Redirecting...</body></html>"
        val htmlPage2 = "<html>\n<body>\n<h1>404 File Not Found</h1>\n</body>\n</html>"
        val xmlRedirect = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NoSuchKey</Code></Error>"

        val bytes1 = htmlPage1.toByteArray(StandardCharsets.UTF_8)
        val bytes2 = htmlPage2.toByteArray(StandardCharsets.UTF_8)
        val bytes3 = xmlRedirect.toByteArray(StandardCharsets.UTF_8)

        assertTrue(RealGameDownloader.isHtmlRedirectHeader(bytes1))
        assertTrue(RealGameDownloader.isHtmlRedirectHeader(bytes2))
        assertTrue(RealGameDownloader.isHtmlRedirectHeader(bytes3))

        assertFalse(RealGameDownloader.isRomBinaryValid(bytes1))
        assertFalse(RealGameDownloader.isRomBinaryValid(bytes2))
        assertFalse(RealGameDownloader.isRomBinaryValid(bytes3))
    }

    @Test
    fun testRomBinaryValidation_acceptsPureBinaryRomBytes() {
        // Simulasi header Game Boy Advance (GBA) asli: 4-byte ARM branch instruction diikuti logo Nintendo
        val gbaHeader = byteArrayOf(
            0x2E, 0x00, 0x00, 0xEA.toByte(), // ARM opcode
            0x24.toByte(), 0xFF.toByte(), 0xAE.toByte(), 0x51.toByte(), // Nintendo Logo bytes
            0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21.toByte()
        )
        assertFalse(RealGameDownloader.isHtmlRedirectHeader(gbaHeader))
        assertTrue(RealGameDownloader.isRomBinaryValid(gbaHeader))

        // Simulasi file ISO 9660 PS2 / PSP: CD001 Descriptor
        val isoHeader = ByteArray(512) { 0x00 }
        isoHeader[0] = 0x01
        isoHeader[1] = 'C'.code.toByte()
        isoHeader[2] = 'D'.code.toByte()
        isoHeader[3] = '0'.code.toByte()
        isoHeader[4] = '0'.code.toByte()
        isoHeader[5] = '1'.code.toByte()

        assertFalse(RealGameDownloader.isHtmlRedirectHeader(isoHeader))
        assertTrue(RealGameDownloader.isRomBinaryValid(isoHeader))
    }

    @Test
    fun testSaveAndValidateRom_rejectsFakeHtmlFileAndDeletesTarget() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "nexusdeck_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val fakeRomFile = File(tempDir, "fake_game.gba")

        val dummyGame = GameItem(
            id = "test_gba_fake",
            title = "Test GBA",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 2,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.Lemuroid
        )

        // 1. Simulasi unduhan yang menghasilkan HTML redirect
        val htmlContent = "<!DOCTYPE html><html><body>Access Denied - Cloudflare</body></html>".toByteArray(StandardCharsets.UTF_8)
        val result = RealGameDownloader.saveAndValidateRom(dummyGame, htmlContent, fakeRomFile)
        assertNull("Downloader harus menolak file HTML dan mengembalikan null", result)
        assertFalse("File ROM palsu (HTML) tidak boleh tersimpan di disk", fakeRomFile.exists())

        // 2. Simulasi unduhan ROM biner murni
        val validBinary = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x45, 0x4C, 0x46)
        val validFile = File(tempDir, "valid_game.gba")
        val validResult = RealGameDownloader.saveAndValidateRom(dummyGame, validBinary, validFile)
        assertNotNull("Downloader harus menerima file biner murni", validResult)
        assertTrue("File biner murni harus tersimpan di disk", validFile.exists())
        assertEquals(validBinary.size.toLong(), validFile.length())

        // Bersihkan
        validFile.delete()
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 3. Uji Matriks Platform & Offline Runtime (Zero Cloudflare CDN)
    // =========================================================================

    @Test
    fun testPspPlatform_strictlyNativeEngineHandoff_neverEntersInAppWasm() {
        val pspGame = GameItem(
            id = "psp_dbz_shin_budokai",
            title = "Dragon Ball Z: Shin Budokai 2",
            platform = GamePlatform.PSP,
            coverUrl = "",
            minRamGb = 3,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.PPSSPP
        )

        // Verifikasi bahwa PSP 100% adalah NATIVE_HANDOFF
        assertEquals(
            "Game PSP seperti Dragon Ball Z harus NATIVE_HANDOFF ke PPSSPP",
            EngineExecutionMode.NATIVE_HANDOFF,
            pspGame.executionMode
        )
        assertFalse(
            "Game PSP tidak boleh berstatus isEmbedded (WASM)",
            pspGame.executionMode.isEmbedded
        )

        // Verifikasi Platform Execution Matrix untuk PSP
        val pspRule = PlatformExecutionMatrix.getRule(GamePlatform.PSP)
        assertEquals(ExecutionType.NATIVE_ENGINE_NO_BIOS, pspRule.executionType)
        assertEquals("ppsspp", pspRule.defaultEngineId)
        assertFalse("PSP tidak boleh menggunakan embedded WASM", pspRule.isEmbeddedWasm)
    }

    @Test
    fun testRetroRuntime_usesLocalAssets_zeroCloudflareCdnReference() {
        val gbaGame = GameItem(
            id = "gba_zelda_minish",
            title = "The Legend of Zelda: The Minish Cap",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 2,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.Lemuroid
        )

        val htmlContent = EmulatorJsEngine.generateHtml(gbaGame, "/games/nintendo/zelda.gba")

        // 1. Verifikasi TIDAK ADA ketergantungan ke cdn.emulatorjs.org (akar masalah Cloudflare 'site owner')
        assertFalse(
            "HTML runtime tidak boleh memuat skrip dari cdn.emulatorjs.org",
            htmlContent.contains("cdn.emulatorjs.org")
        )

        // 2. Verifikasi memuat file runtime lokal dari assets
        assertTrue(
            "HTML runtime harus memuat file:///android_asset/retro/retro_engine.js",
            htmlContent.contains("file:///android_asset/retro/retro_engine.js")
        )
        assertEquals("file:///android_asset/retro/", EmulatorJsEngine.OFFLINE_ASSET_BASE_URL)

        // 3. Verifikasi core Nintendo terselesaikan dengan benar
        assertEquals("mgba", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "zelda.gba"))
        assertEquals("snes", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "mario_world.smc"))
        assertEquals("nes", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "contra.nes"))
    }

    // =========================================================================
    // 4. Uji Integritas Error Handling & Tombol Fallback Browser
    // =========================================================================

    @Test
    fun testErrorHandling_friendlyMessageAndFallbackBrowserButton() {
        // Uji tombol fallback label
        assertEquals("Unduh Manual dari Browser", FALLBACK_BROWSER_BUTTON_LABEL)

        // Uji pemformatan pesan error ramah pengguna
        val error404 = formatFriendlyErrorMessage("Server mengembalikan kode status HTTP 404")
        assertTrue("Error 404 harus menyarankan unduh manual dari browser", error404.contains("browser"))

        val errorTimeout = formatFriendlyErrorMessage("Connection timed out to github.com")
        assertTrue("Error timeout harus menyebutkan koneksi internet", errorTimeout.contains("Koneksi"))

        val genericError = formatFriendlyErrorMessage("Network unreachable")
        assertTrue(genericError.contains("Unduh Manual dari Browser") || genericError.contains("browser"))
    }
}
