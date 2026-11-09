package com.nexusdeck.app.core.engine

import org.json.JSONObject
import java.util.Locale

/**
 * Pemilih aset APK murni (tanpa dependensi Android) untuk GitHub Releases.
 *
 * Dipisah dari EngineManager agar logikanya bisa diuji unit secara lokal
 * tanpa AndroidX/FileProvider: EngineManager hanya mendelegasikan ke sini.
 *
 * Aturan skor dipelajari dari audit engine 2026-11-10:
 * - Naikkan: "android" (aset Android resmi), "arm64"/"v8a" (ARM 64-bit),
 *   "universal" (semua ABI), "vanilla" (varian langsung, bukan varian toko).
 * - Turunkan tajam: "legacy"/"xr" (varian khusus — GameNative pernah salah
 *   pilih varian legacy-xr 573 MB sebagai aset pertama), "debug",
 *   "symbols", "test", "source" (bukan APK rilis untuk user).
 * - Turunkan: "googleplay" (varian terikat Play Store; varian vanilla
 *   lebih cocok untuk unduhan langsung).
 */
object EngineAssetPicker {

    fun scoreApkAssetName(assetName: String): Int {
        val name = assetName.lowercase(Locale.ROOT)
        var score = 0
        if (name.contains("android")) score += 3
        if (name.contains("arm64") || name.contains("v8a")) score += 2
        if (name.contains("universal")) score += 1
        if (name.contains("vanilla")) score += 1
        if (name.contains("legacy")) score -= 10
        if (name.contains("xr")) score -= 10
        if (name.contains("googleplay")) score -= 5
        if (name.contains("debug")) score -= 10
        if (name.contains("symbols")) score -= 10
        if (name.contains("test")) score -= 10
        if (name.contains("source")) score -= 10
        return score
    }

    /**
     * Parsing daftar assets dari JSON respons GitHub Releases:
     * https://api.github.com/repos/{owner}/{repo}/releases/latest
     *
     * Mengekstrak browser_download_url dari aset .apk dengan skor tertinggi
     * menurut [scoreApkAssetName]; bila skor imbang, aset pertama menang.
     * Mengembalikan null bila tidak ada aset .apk atau JSON tidak valid.
     */
    fun parseAssetApkUrlFromReleaseJson(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            val assets = root.optJSONArray("assets") ?: return null
            var bestUrl: String? = null
            var bestScore = Int.MIN_VALUE

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")

                if (name.lowercase(Locale.ROOT).endsWith(".apk") && downloadUrl.isNotBlank()) {
                    val score = scoreApkAssetName(name)
                    if (score > bestScore) {
                        bestScore = score
                        bestUrl = downloadUrl
                    }
                }
            }
            bestUrl
        } catch (_: Exception) {
            null
        }
    }
}
