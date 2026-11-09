package com.nexusdeck.app.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nexusdeck.app.core.downloader.LinkVerifier
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Cara sebuah engine diperoleh di perangkat user.
 *
 * - GITHUB_APK: file APK diunduh langsung dari GitHub Releases (URL sudah
 *   terverifikasi hidup pada 2026-11-10; lihat komentar tiap registry).
 * - PLAY_STORE: proyek tidak lagi menerbitkan APK Android di GitHub — distribusi
 *   resminya lewat Google Play Store. Aplikasi membuka halaman Play Store,
 *   bukan mengunduh file mati.
 * - UNAVAILABLE: tidak ada sumber unduhan yang terverifikasi saat ini. Engine
 *   tetap terdaftar agar dialog bisa menjelaskan alasannya dengan jujur,
 *   bukan menawarkan unduhan yang pasti gagal.
 */
enum class EngineDownloadKind {
    GITHUB_APK,
    PLAY_STORE,
    UNAVAILABLE
}

/**
 * Hasil resolusi jalur pemasangan satu engine.
 */
sealed interface EngineDownloadTarget {
    /** Unduh file APK langsung dari URL ini. */
    data class Apk(val url: String) : EngineDownloadTarget

    /** Pasang lewat Google Play Store untuk package ini. */
    data class PlayStore(val packageName: String) : EngineDownloadTarget

    /** Tidak ada jalur pemasangan yang terverifikasi saat ini. */
    data class Unavailable(val reason: String) : EngineDownloadTarget
}

/**
 * Metadata model untuk emulator target engine di NexusDeck.
 *
 * Catatan v1.2.0: [downloadUrl] selalu menunjuk ke tujuan yang masih hidup —
 * URL APK langsung untuk GITHUB_APK, atau URL halaman Play Store untuk
 * PLAY_STORE. Untuk UNAVAILABLE, [downloadUrl] kosong dan alasannya ada di
 * [unavailableReason]. Jangan pernah mengisi URL yang belum diverifikasi
 * hidup: dialog dan downloader menganggap URL ini benar adanya.
 */
data class EngineInfo(
    val id: String,
    val name: String,
    val platform: GamePlatform,
    val targetEmulator: TargetEmulator,
    val primaryPackageName: String,
    val alternatePackageNames: List<String> = emptyList(),
    val downloadUrl: String,
    val estimatedSizeLabel: String,
    val description: String,
    val version: String,
    val githubRepo: String? = null,
    val downloadKind: EngineDownloadKind = EngineDownloadKind.GITHUB_APK,
    val unavailableReason: String? = null
) {
    val allPackageNames: List<String>
        get() = listOf(primaryPackageName) + alternatePackageNames

    /** URL halaman Play Store untuk engine ini (hanya bermakna bila kind PLAY_STORE). */
    val playStoreWebUrl: String
        get() = "https://play.google.com/store/apps/details?id=$primaryPackageName"
}

/**
 * Status progres unduhan APK engine.
 */
sealed class EngineDownloadProgress {
    /** Masih menyelesaikan URL dan memeriksa ketersediaan file (belum ada byte). */
    object Resolving : EngineDownloadProgress()

    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : EngineDownloadProgress()

    data class Completed(
        val apkFile: File
    ) : EngineDownloadProgress()

    data class Failed(
        val error: String
    ) : EngineDownloadProgress()
}

/**
 * Modul Pengelola Engine Otomatis (Unified In-App Engine Manager)
 * Mengelola registry emulator resmi, pengecekan instalasi package,
 * unduhan background APK, serta instalasi 1-tap menggunakan FileProvider.
 *
 * v1.2.0 — registry yang menyeleksi sendiri (prinsip yang sama seperti katalog
 * game v1.1.9): setiap URL unduhan diverifikasi hidup pada 2026-11-10 sebelum
 * ditulis ke registry. Hasil audit 2026-11-10: 9 dari 10 URL lama mati (404).
 * - DuckStation & PPSSPP: GitHub Releases tidak lagi berisi APK Android sama
 *   sekali (rilis terbaru hanya AppImage/Windows/Mac) — jalur resmi keduanya
 *   adalah Google Play Store (listing terverifikasi hidup 2026-11-10).
 * - NetherSX2: tag rilis lama salah (v2.2n-3668); tag benar "2.2n", aset
 *   NetherSX2-v2.2n-3668.apk (19.544.036 byte, HEAD 200 terverifikasi).
 * - GameNative: aset berganti nama; rilis v1.4.0 berisi gamenative-v1.4.0.apk
 *   (571.251.660 byte, HEAD 200 terverifikasi) — BUKAN varian legacy-xr.
 * - melonDS: rilis 2.0.1 berisi app-gitHub-prod-release.apk (21.061.910 byte,
 *   HEAD 200 terverifikasi).
 * - Lime3DS: proyek berganti nama menjadi Azahar (azahar-emu/azahar); rilis
 *   stabil 2126.2 berisi azahar-android-vanilla-2126.2.apk (50.187.175 byte,
 *   HEAD 200 terverifikasi, package org.azahar_emu.azahar dicek via aapt).
 * - Sudachi: repositori sudah tidak ada (404) — ditandai UNAVAILABLE.
 * - APS3e & ARMSX3: repositori lama 404; keduanya hidup di Play Store
 *   (aenu.aps3e dan com.armsx3.play, keduanya HEAD 200 terverifikasi).
 * - Lemuroid: satu-satunya URL lama yang masih hidup (200, 11.985.230 byte).
 *
 * Resolver dinamis (GitHub Releases API) dipertahankan sebagai lapisan
 * pembaruan, tapi fallback-nya kini URL yang sudah terverifikasi — bukan URL
 * mati. API GitHub tanpa token dibatasi 60 permintaan/jam per IP, sehingga
 * resolver tidak boleh menjadi satu-satunya jalan: bila API gagal, fallback
 * terverifikasi yang dipakai.
 */
object EngineManager {

    private const val TAG = "NexusEngineManager"

    /** Ukuran minimum file APK engine yang dianggap valid (1 MiB). */
    const val MIN_ENGINE_APK_BYTES: Long = 1L * 1024L * 1024L

    // =========================================================================
    // Registry Data Engine (URL terverifikasi hidup 2026-11-10)
    // =========================================================================

    val ppssppEngine = EngineInfo(
        id = "ppsspp",
        name = "PPSSPP",
        platform = GamePlatform.PSP,
        targetEmulator = TargetEmulator.PPSSPP,
        primaryPackageName = "org.ppsspp.ppsspp",
        // GitHub v1.20.4 TIDAK berisi aset APK (cek 2026-11-10). Distribusi resmi: Play Store.
        downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp",
        estimatedSizeLabel = "Play Store",
        description = "Emulator PSP resmi tercepat & paling kompatibel untuk Android.",
        version = "latest",
        githubRepo = "hrydgard/ppsspp",
        downloadKind = EngineDownloadKind.PLAY_STORE
    )

    val netherSx2Engine = EngineInfo(
        id = "nethersx2",
        name = "NetherSX2 Classic",
        platform = GamePlatform.PS2,
        targetEmulator = TargetEmulator.ARMSX2,
        primaryPackageName = "xyz.aethersx2.android",
        alternatePackageNames = listOf("come.nanodata.armsx2"),
        // Tag lama "v2.2n-3668" salah; tag benar "2.2n". Aset 19.544.036 byte, HEAD 200 (2026-11-10).
        downloadUrl = "https://github.com/Trixarian/NetherSX2-classic/releases/download/2.2n/NetherSX2-v2.2n-3668.apk",
        estimatedSizeLabel = "~19 MB",
        description = "Build NetherSX2 Classic optimal tanpa iklan untuk PlayStation 2.",
        version = "2.2n-3668",
        githubRepo = "Trixarian/NetherSX2-classic"
    )

    val gameNativeEngine = EngineInfo(
        id = "gamenative",
        name = "GameNative / Winlator",
        platform = GamePlatform.PC,
        targetEmulator = TargetEmulator.GameNative,
        primaryPackageName = "app.gamenative",
        alternatePackageNames = listOf("com.winlator"),
        // Aset lama "GameNative.apk" tidak ada; rilis v1.4.0 berisi gamenative-v1.4.0.apk
        // (571.251.660 byte, HEAD 200). Unduhan besar — dialog menampilkan ukuran jujur.
        downloadUrl = "https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0.apk",
        estimatedSizeLabel = "~571 MB",
        description = "Engine wrapper PC Windows x86/x64 dengan Turnip driver & DXVK.",
        version = "v1.4.0",
        githubRepo = "utkarshdalal/GameNative"
    )

    val melonDsEngine = EngineInfo(
        id = "melonds",
        name = "melonDS",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.MelonDS,
        primaryPackageName = "net.kuribo64.melonDS",
        // Rilis 1.9.3 mati; rilis 2.0.1 berisi app-gitHub-prod-release.apk (21.061.910 byte, HEAD 200).
        downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/download/2.0.1/app-gitHub-prod-release.apk",
        estimatedSizeLabel = "~21 MB",
        description = "Emulator open-source Nintendo DS presisi tinggi dengan JIT ARM.",
        version = "2.0.1",
        githubRepo = "rafaelvcaetano/melonDS-android"
    )

    val azaharEngine = EngineInfo(
        id = "azahar",
        name = "Azahar",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Lime3DS,
        primaryPackageName = "org.azahar_emu.azahar",
        // Lime3DS berganti nama menjadi Azahar (azahar-emu/azahar). Rilis stabil 2126.2
        // berisi azahar-android-vanilla-2126.2.apk (50.187.175 byte, HEAD 200;
        // package org.azahar_emu.azahar terverifikasi via aapt, 2026-11-10).
        // Pakai varian vanilla (bukan googleplay) agar bisa dipasang langsung.
        downloadUrl = "https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk",
        estimatedSizeLabel = "~48 MB",
        description = "Penerus Citra/Lime3DS untuk emulasi Nintendo 3DS di Android.",
        version = "2126.2",
        githubRepo = "azahar-emu/azahar"
    )

    val sudachiEngine = EngineInfo(
        id = "sudachi",
        name = "Sudachi",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Sudachi,
        primaryPackageName = "org.sudachi.sudachi_emu",
        // Repositori emuplace/sudachi.emuplace.app sudah tidak ada (404, cek 2026-11-10).
        // Tidak ada engine Switch pengganti yang terverifikasi; katalog saat ini pun
        // tidak berisi game Sudachi. Jangan tawarkan unduhan untuk engine ini.
        downloadUrl = "",
        estimatedSizeLabel = "Tidak tersedia",
        description = "Emulator Nintendo Switch berbasis Yuzu (proyek sudah tidak tersedia).",
        version = "latest",
        githubRepo = "emuplace/sudachi.emuplace.app",
        downloadKind = EngineDownloadKind.UNAVAILABLE,
        unavailableReason = "Repositori Sudachi sudah tidak ada di GitHub, jadi tidak ada file APK " +
            "yang bisa diunduh. Belum ada engine Nintendo Switch pengganti yang terverifikasi."
    )

    val aps3eEngine = EngineInfo(
        id = "aps3e",
        name = "APS3e (RPCS3 Android)",
        platform = GamePlatform.PS3,
        targetEmulator = TargetEmulator.ARMSX3,
        primaryPackageName = "aenu.aps3e",
        alternatePackageNames = listOf("com.armsx3.play", "com.tinyproot.aps3e", "org.aps3e", "org.rpcs3"),
        // Repo lama APS3e/APS3e 404; proyek kini di aenu1/aps3e dan terdaftar di Play Store
        // (listing aenu.aps3e terverifikasi hidup 2026-11-10). PS3 tetap butuh firmware PS3UPDAT.PUP.
        downloadUrl = "https://play.google.com/store/apps/details?id=aenu.aps3e",
        estimatedSizeLabel = "Play Store",
        description = "Engine PlayStation 3 berbasis RPCS3 & Vulkan (wajib firmware PS3UPDAT.PUP).",
        version = "latest",
        githubRepo = "aenu1/aps3e",
        downloadKind = EngineDownloadKind.PLAY_STORE
    )

    val duckStationEngine = EngineInfo(
        id = "duckstation",
        name = "DuckStation",
        platform = GamePlatform.PS1,
        targetEmulator = TargetEmulator.DuckStation,
        primaryPackageName = "com.github.stenzek.duckstation",
        alternatePackageNames = listOf("org.duckstation.android"),
        // Rilis GitHub stenzek/duckstation TIDAK berisi APK Android sama sekali
        // (cek 2026-11-10: hanya AppImage/Windows/Mac). Distribusi resmi: Play Store
        // (listing terverifikasi hidup, rating 4,6, update 24 Okt 2026).
        downloadUrl = "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation",
        estimatedSizeLabel = "Play Store",
        description = "Emulator PlayStation 1 akurat performa tinggi dengan dukungan CHD & PGXP.",
        version = "latest",
        githubRepo = "stenzek/duckstation",
        downloadKind = EngineDownloadKind.PLAY_STORE
    )

    val armsx3Engine = EngineInfo(
        id = "armsx3",
        name = "ARMSX3",
        platform = GamePlatform.PS3,
        targetEmulator = TargetEmulator.ARMSX3,
        primaryPackageName = "com.armsx3.play",
        alternatePackageNames = listOf("org.aps3e"),
        // Repo lama ARMSX3/ARMSX3 404; proyek kini di ARMSX2/ArmSX3 dan terdaftar di
        // Play Store (listing com.armsx3.play terverifikasi hidup 2026-11-10).
        downloadUrl = "https://play.google.com/store/apps/details?id=com.armsx3.play",
        estimatedSizeLabel = "Play Store",
        description = "PlayStation 3 runner berbasis RPCS3 untuk ARM64 (wajib firmware PS3UPDAT.PUP).",
        version = "latest",
        githubRepo = "ARMSX2/ArmSX3",
        downloadKind = EngineDownloadKind.PLAY_STORE
    )

    val lemuroidEngine = EngineInfo(
        id = "lemuroid",
        name = "Lemuroid",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Lemuroid,
        primaryPackageName = "com.swordfish.lemuroid",
        // Satu-satunya URL lama yang masih hidup: HEAD 200, 11.985.230 byte (2026-11-10).
        downloadUrl = "https://github.com/Swordfish90/Lemuroid/releases/download/1.17.0/lemuroid-app-free-dynamic-release.apk",
        estimatedSizeLabel = "~12 MB",
        description = "Emulator retro zero-setup all-in-one (GBA, GBC, NES, SNES, NDS, N64) dengan core bawaan mGBA, Snes9x, MelonDS & Mupen64 tanpa unduh core manual.",
        version = "1.17.0",
        githubRepo = "Swordfish90/Lemuroid"
    )

    /**
     * Seluruh engine yang terdaftar di NexusDeck Registry.
     */
    val registeredEngines: List<EngineInfo> = listOf(
        ppssppEngine,
        netherSx2Engine,
        duckStationEngine,
        gameNativeEngine,
        melonDsEngine,
        azaharEngine,
        sudachiEngine,
        aps3eEngine,
        armsx3Engine,
        lemuroidEngine
    )

    // =========================================================================
    // Dynamic GitHub Releases API Resolver (Anti-404 Asset Finder)
    // =========================================================================

    /**
     * Skor satu nama aset APK untuk pemilihan otomatis. Logika murni tinggal di
     * [EngineAssetPicker] agar bisa diuji unit secara lokal; fungsi ini hanya
     * mendelegasikan (dipakai juga oleh tes lama).
     */
    fun scoreApkAssetName(assetName: String): Int {
        return EngineAssetPicker.scoreApkAssetName(assetName)
    }

    /**
     * Parsing daftar assets dari JSON respons GitHub Releases:
     * https://api.github.com/repos/{owner}/{repo}/releases/latest
     *
     * Logika murni tinggal di [EngineAssetPicker]; fungsi ini hanya
     * mendelegasikan (dipakai juga oleh tes lama).
     */
    fun parseAssetApkUrlFromReleaseJson(jsonString: String): String? {
        return EngineAssetPicker.parseAssetApkUrlFromReleaseJson(jsonString)
    }

    /**
     * Menghubungi GitHub Releases API untuk mencari URL aset APK rilis terbaru secara dinamis.
     * Mengembalikan null bila API gagal (rate limit 60/jam, offline, atau rilis tanpa APK).
     */
    fun fetchLatestReleaseAssetUrl(repo: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val conn = (URI(apiUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "NexusDeck/1.1.3 Handheld Engine")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseAssetApkUrlFromReleaseJson(jsonText)
            } else {
                conn.disconnect()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Menyelesaikan URL unduhan APK engine secara dinamis:
     * 1. Mencari aset APK terbaru via GitHub Releases API jika githubRepo tersedia.
     * 2. Jika API mengembalikan URL valid, gunakan URL tersebut.
     * 3. Jika API gagal (rate limit/offline), fallback ke URL rilis default —
     *    yang sejak v1.2.0 sudah terverifikasi hidup, bukan URL mati.
     * Hanya untuk engine GITHUB_APK; engine PLAY_STORE/UNAVAILABLE tidak
     * memakai jalur ini (lihat [resolveEngineDownload]).
     */
    fun resolveDynamicApkUrl(engineInfo: EngineInfo): String {
        val repo = engineInfo.githubRepo
        if (!repo.isNullOrBlank()) {
            val dynamicUrl = fetchLatestReleaseAssetUrl(repo)
            if (!dynamicUrl.isNullOrBlank() && dynamicUrl.startsWith("http")) {
                Log.i(TAG, "Resolusi dinamis aset APK untuk ${engineInfo.name}: $dynamicUrl")
                return dynamicUrl
            }
        }
        return engineInfo.downloadUrl
    }

    /**
     * Menyelesaikan jalur pemasangan satu engine — inilah "seleksi otomatis":
     * - UNAVAILABLE: tidak ada jalur; kembalikan alasan jujur.
     * - PLAY_STORE: pasang lewat Play Store (package utama engine).
     * - GITHUB_APK: unduh APK dari URL hasil resolusi dinamis (fallback
     *   terverifikasi bila API gagal).
     */
    fun resolveEngineDownload(engineInfo: EngineInfo): EngineDownloadTarget {
        return when (engineInfo.downloadKind) {
            EngineDownloadKind.UNAVAILABLE -> EngineDownloadTarget.Unavailable(
                engineInfo.unavailableReason
                    ?: "Engine ${engineInfo.name} belum tersedia untuk diunduh saat ini."
            )
            EngineDownloadKind.PLAY_STORE -> EngineDownloadTarget.PlayStore(
                engineInfo.primaryPackageName
            )
            EngineDownloadKind.GITHUB_APK -> EngineDownloadTarget.Apk(
                resolveDynamicApkUrl(engineInfo)
            )
        }
    }

    /**
     * URL halaman Play Store untuk satu package (dipakai sebagai fallback bila
     * aplikasi Play Store tidak terpasang di perangkat).
     */
    fun playStoreWebUrl(packageName: String): String {
        return "https://play.google.com/store/apps/details?id=$packageName"
    }

    /**
     * Membuka halaman Play Store untuk satu engine: coba aplikasi Play Store
     * (market://) dulu, jatuh ke halaman web bila gagal. Mengembalikan true
     * bila ada intent yang berhasil diluncurkan.
     */
    fun openPlayStore(context: Context, engineInfo: EngineInfo): Boolean {
        val packageName = engineInfo.primaryPackageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(playStoreWebUrl(packageName))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // =========================================================================
    // Lookup & Matching Queries
    // =========================================================================

    fun getEngineById(id: String): EngineInfo? {
        return registeredEngines.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun getEngineForPlatform(platform: GamePlatform): EngineInfo {
        return when (platform) {
            GamePlatform.PS1 -> duckStationEngine
            GamePlatform.PSP -> ppssppEngine
            GamePlatform.PS2 -> netherSx2Engine
            GamePlatform.PC -> gameNativeEngine
            GamePlatform.NINTENDO -> lemuroidEngine
            GamePlatform.PS3 -> aps3eEngine
        }
    }

    fun getEngineForEmulator(targetEmulator: TargetEmulator): EngineInfo {
        return when (targetEmulator) {
            TargetEmulator.DuckStation -> duckStationEngine
            TargetEmulator.PPSSPP -> ppssppEngine
            TargetEmulator.ARMSX2 -> netherSx2Engine
            TargetEmulator.GameNative -> gameNativeEngine
            TargetEmulator.MelonDS -> melonDsEngine
            TargetEmulator.Lime3DS -> azaharEngine
            TargetEmulator.Sudachi -> sudachiEngine
            TargetEmulator.ARMSX3 -> aps3eEngine
            TargetEmulator.Lemuroid, TargetEmulator.NexusRetroCore -> lemuroidEngine
        }
    }

    fun getEngineForGame(game: GameItem): EngineInfo {
        return runCatching { getEngineForEmulator(game.targetEmulator) }
            .getOrElse { getEngineForPlatform(game.platform) }
    }

    // =========================================================================
    // Package Availability Checks
    // =========================================================================

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isEngineInstalled(context: Context, engineInfo: EngineInfo): Boolean {
        return engineInfo.allPackageNames.any { isPackageInstalled(context, it) }
    }

    fun isEngineInstalled(context: Context, platform: GamePlatform): Boolean {
        val engine = getEngineForPlatform(platform)
        return isEngineInstalled(context, engine)
    }

    fun isEngineInstalledForGame(context: Context, game: GameItem): Boolean {
        val engine = getEngineForGame(game)
        return isEngineInstalled(context, engine)
    }

    fun getInstalledPackage(context: Context, engineInfo: EngineInfo): String? {
        return engineInfo.allPackageNames.firstOrNull { isPackageInstalled(context, it) }
    }

    // =========================================================================
    // APK Download Stream & 1-Tap Installer
    // =========================================================================

    /**
     * Membuka koneksi HTTP dengan penanganan redirect berantai (301, 302, 307)
     * yang lazim terjadi pada tautan GitHub Releases ke CDN objects.githubusercontent.com.
     * instanceFollowRedirects sengaja dimatikan agar loop manual di sini yang
     * mengendalikan redirect (bila dibiarkan true, responseCode sudah final dan
     * loop ini tidak pernah berjalan).
     */
    fun openConnectionWithRedirects(initialUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var urlStr = initialUrl
        var redirects = 0
        while (redirects < maxRedirects) {
            val uri = URI(urlStr)
            val conn = (uri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "NexusDeck/1.1.3 EngineDownloader")
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                urlStr = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    uri.resolve(location).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
        return (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "NexusDeck/1.1.3 EngineDownloader")
            instanceFollowRedirects = false
        }
    }

    /**
     * Mengunduh file APK engine resmi langsung ke direktori cache/engines NexusDeck
     * lengkap dengan update progress flow secara asinkron.
     *
     * Alur v1.2.0 (perbaikan atas dialog 0% yang diam di video user):
     * 1. Emit Resolving — UI menampilkan "Memeriksa ketersediaan engine…"
     *    (bukan 0,0 MB yang diam) selama resolusi URL + pre-flight berjalan.
     * 2. Selesaikan jalur pemasangan: PLAY_STORE/UNAVAILABLE langsung gagal
     *    dengan pesan yang jelas (dialog seharusnya tidak memanggil fungsi ini
     *    untuk kedua jenis itu; ini pengaman).
     * 3. Pre-flight dengan LinkVerifier: tolak tautan mati/HTML SEBELUM satu
     *    byte pun diunduh, dengan alasan yang spesifik — bukan 404 samar
     *    setelah menunggu.
     * 4. Unduh dengan progres nyata; bila GET mengembalikan 404 padahal
     *    pre-flight lolos dan URL dinamis berbeda dari default, coba sekali
     *    lagi dengan URL default terverifikasi.
     */
    fun downloadEngineApk(
        context: Context,
        engineInfo: EngineInfo
    ): Flow<EngineDownloadProgress> = flow {
        emit(EngineDownloadProgress.Resolving)

        val target = resolveEngineDownload(engineInfo)
        val targetUrl = when (target) {
            is EngineDownloadTarget.Unavailable -> {
                emit(EngineDownloadProgress.Failed(target.reason))
                return@flow
            }
            is EngineDownloadTarget.PlayStore -> {
                emit(
                    EngineDownloadProgress.Failed(
                        "Engine ${engineInfo.name} dipasang lewat Google Play Store, " +
                            "bukan unduhan APK langsung. Gunakan tombol Play Store."
                    )
                )
                return@flow
            }
            is EngineDownloadTarget.Apk -> target.url
        }

        // Pre-flight: jangan mulai mengunduh tautan yang sudah terbukti mati.
        val linkCheck = LinkVerifier.verify(targetUrl, MIN_ENGINE_APK_BYTES)
        if (!linkCheck.ok) {
            emit(EngineDownloadProgress.Failed(linkCheck.reason))
            return@flow
        }
        val verifiedUrl = linkCheck.finalUrl.ifBlank { targetUrl }

        val enginesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "engines")
        if (!enginesDir.exists()) {
            enginesDir.mkdirs()
        }

        val apkFile = File(enginesDir, "${engineInfo.id}.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = openConnectionWithRedirects(verifiedUrl)
            conn.connect()
            var code = conn.responseCode

            // Jika URL terverifikasi mengembalikan 404 saat GET dan berbeda dari
            // default, coba default terverifikasi sekali.
            if (code == 404 && verifiedUrl != engineInfo.downloadUrl) {
                conn.disconnect()
                conn = openConnectionWithRedirects(engineInfo.downloadUrl)
                conn.connect()
                code = conn.responseCode
            }

            if (code !in 200..299) {
                emit(EngineDownloadProgress.Failed("Server mengembalikan kode status HTTP $code. Gunakan opsi 'Unduh Manual dari Browser'."))
                return@flow
            }

            val totalBytes = conn.contentLengthLong.let { if (it > 0L) it else 30_000_000L }
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastEmitTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime > 120L) {
                            lastEmitTime = now
                            val progress =
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            emit(
                                EngineDownloadProgress.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }
            }

            emit(EngineDownloadProgress.Completed(apkFile = apkFile))
        } catch (e: Exception) {
            emit(
                EngineDownloadProgress.Failed(
                    "Gagal mengunduh APK engine ${engineInfo.name}: ${e.localizedMessage ?: "Koneksi terputus"}. Gunakan opsi 'Unduh Manual dari Browser'."
                )
            )
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Membangun Intent instalasi paket Android menggunakan FileProvider resmi.
     * JANGAN gunakan Uri.fromFile() sebagai fallback: intent yang membawa
     * file:// URI melempar FileUriExposedException di Android 24+. Jika
     * FileProvider gagal, lempar IllegalStateException dan biarkan pemanggil
     * menampilkannya sebagai pesan error.
     */
    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Menjalankan proses download APK di latar belakang dan otomatis membuka prompt
     * instalasi Android dalam 1 ketukan setelah selesai.
     */
    fun downloadAndInstallEngine(
        context: Context,
        engineInfo: EngineInfo,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        onProgress: (EngineDownloadProgress) -> Unit = {}
    ): Job {
        return scope.launch {
            downloadEngineApk(context, engineInfo).collect { progress ->
                onProgress(progress)
                if (progress is EngineDownloadProgress.Completed) {
                    try {
                        val installIntent = buildInstallIntent(context, progress.apkFile)
                        context.startActivity(installIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal meluncurkan intent installer APK", e)
                    }
                }
            }
        }
    }
}
