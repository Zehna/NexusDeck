package com.nexusdeck.app

import com.nexusdeck.app.core.engine.EngineAssetPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tes unit murni untuk EngineAssetPicker (v1.2.0).
 *
 * File ini sengaja hanya menguji logika murni tanpa AndroidX/Context agar bisa
 * dijalankan lokal via kotlinc + JUnitCore (CI tetap menjalankan seluruh tes
 * termasuk NexusDeckComprehensiveStressTest).
 */
class EngineAssetPickerTest {

    @Test
    fun score_legacyXr_lowerThanPlainApk() {
        // Kasus nyata GameNative v1.4.0: picker lama memilih varian legacy-xr
        // 573 MB karena aset itu muncul pertama. Picker baru harus menolaknya.
        val legacyScore = EngineAssetPicker.scoreApkAssetName("gamenative-v1.4.0-legacy-xr.apk")
        val plainScore = EngineAssetPicker.scoreApkAssetName("gamenative-v1.4.0.apk")
        assertTrue("legacy-xr harus bernilai lebih rendah dari APK biasa", legacyScore < plainScore)
    }

    @Test
    fun score_vanilla_higherThanGooglePlay() {
        // Azahar 2126.2: varian vanilla untuk unduhan langsung, googleplay terikat toko.
        val vanillaScore = EngineAssetPicker.scoreApkAssetName("azahar-android-vanilla-2126.2.apk")
        val googlePlayScore = EngineAssetPicker.scoreApkAssetName("azahar-android-googleplay-2126.2.apk")
        assertTrue("vanilla harus bernilai lebih tinggi dari googleplay", vanillaScore > googlePlayScore)
    }

    @Test
    fun score_androidArm64_positive() {
        assertTrue(EngineAssetPicker.scoreApkAssetName("PPSSPP_v1.20.4_android.apk") > 0)
        assertTrue(EngineAssetPicker.scoreApkAssetName("gamenative-v1.4.0-arm64-v8a.apk") > 0)
        assertTrue(EngineAssetPicker.scoreApkAssetName("duckstation-android.apk") > 0)
    }

    @Test
    fun parse_picksPlainGameNativeOverLegacyXr() {
        // JSON meniru rilis nyata GameNative v1.4.0 (legacy-xr muncul pertama).
        val json = """
            {
              "tag_name": "v1.4.0",
              "assets": [
                {
                  "name": "gamenative-v1.4.0-legacy-xr.apk",
                  "browser_download_url": "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0-legacy-xr.apk"
                },
                {
                  "name": "gamenative-v1.4.0.apk",
                  "browser_download_url": "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0.apk"
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0.apk",
            EngineAssetPicker.parseAssetApkUrlFromReleaseJson(json)
        )
    }

    @Test
    fun parse_picksVanillaAzaharOverGooglePlay() {
        val json = """
            {
              "tag_name": "2126.2",
              "assets": [
                {
                  "name": "azahar-android-googleplay-2126.2.apk",
                  "browser_download_url": "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-googleplay-2126.2.apk"
                },
                {
                  "name": "azahar-android-vanilla-2126.2.apk",
                  "browser_download_url": "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk"
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk",
            EngineAssetPicker.parseAssetApkUrlFromReleaseJson(json)
        )
    }

    @Test
    fun parse_picksAndroidApkAmongMixedAssets() {
        // Sama seperti simulasi PPSSPP di NexusDeckEngineAndRuntimeTest.
        val json = """
            {
              "tag_name": "v1.20.4",
              "assets": [
                {
                  "name": "PPSSPP_v1.20.4_android.apk",
                  "browser_download_url": "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP_v1.20.4_android.apk"
                },
                {
                  "name": "ppsspp-windows-x64.zip",
                  "browser_download_url": "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/ppsspp-windows-x64.zip"
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/PPSSPP_v1.20.4_android.apk",
            EngineAssetPicker.parseAssetApkUrlFromReleaseJson(json)
        )
    }

    @Test
    fun parse_firstWinsOnTie() {
        val json = """
            {
              "tag_name": "2.2n",
              "assets": [
                {
                  "name": "NetherSX2-v2.2n-3668.apk",
                  "browser_download_url": "https://example.com/first.apk"
                },
                {
                  "name": "NetherSX2-v2.2n-3668-alt.apk",
                  "browser_download_url": "https://example.com/second.apk"
                }
              ]
            }
        """.trimIndent()
        assertEquals("https://example.com/first.apk", EngineAssetPicker.parseAssetApkUrlFromReleaseJson(json))
    }

    @Test
    fun parse_emptyAndInvalidJson_returnsNull() {
        assertNull(EngineAssetPicker.parseAssetApkUrlFromReleaseJson("""{"assets": []}"""))
        assertNull(EngineAssetPicker.parseAssetApkUrlFromReleaseJson("""{"assets": [{"name": "readme.txt", "browser_download_url": "https://example.com/readme.txt"}]}"""))
        assertNull(EngineAssetPicker.parseAssetApkUrlFromReleaseJson("bukan json"))
        assertNull(EngineAssetPicker.parseAssetApkUrlFromReleaseJson(""))
    }
}
