package com.nexusdeck.app.core.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/**
 * Metadata model untuk emulator target engine di NexusDeck.
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
    val githubRepo: String? = null
) {
    val allPackageNames: List<String>
        get() = listOf(primaryPackageName) + alternatePackageNames
}

/**
 * Status progres unduhan APK engine.
 */
sealed class EngineDownloadProgress {
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
 * Mengelola registry emulator resmi dari GitHub Releases, pengecekan instalasi package,
 * unduhan background APK, serta instalasi 1-tap menggunakan FileProvider.
 * Dilengkapi pencari aset dinamis menggunakan GitHub Releases API untuk mencegah error HTTP 404.
 */
object EngineManager {

    private const val TAG = "NexusEngineManager"

    // =========================================================================
    // Registry Data Engine Ringan Terbaik (Official GitHub Releases Direct APK)
    // =========================================================================

    val ppssppEngine = EngineInfo(
        id = "ppsspp",
        name = "PPSSPP",
        platform = GamePlatform.PSP,
        targetEmulator = TargetEmulator.PPSSPP,
        primaryPackageName = "org.ppsspp.ppsspp",
        downloadUrl = "https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/ppsspp.apk",
        estimatedSizeLabel = "~28 MB",
        description = "Emulator PSP resmi tercepat & paling kompatibel untuk Android.",
        version = "v1.20.4",
        githubRepo = "hrydgard/ppsspp"
    )

    val netherSx2Engine = EngineInfo(
        id = "nethersx2",
        name = "NetherSX2 Classic",
        platform = GamePlatform.PS2,
        targetEmulator = TargetEmulator.ARMSX2,
        primaryPackageName = "xyz.aethersx2.android",
        alternatePackageNames = listOf("come.nanodata.armsx2"),
        downloadUrl = "https://github.com/Trixarian/NetherSX2-classic/releases/download/v2.2n-3668/NetherSX2-v2.2n-3668.apk",
        estimatedSizeLabel = "~18 MB",
        description = "Build NetherSX2 Classic optimal tanpa iklan untuk PlayStation 2.",
        version = "v2.2n-3668",
        githubRepo = "Trixarian/NetherSX2-classic"
    )

    val gameNativeEngine = EngineInfo(
        id = "gamenative",
        name = "GameNative / Winlator",
        platform = GamePlatform.PC,
        targetEmulator = TargetEmulator.GameNative,
        primaryPackageName = "app.gamenative",
        alternatePackageNames = listOf("com.winlator"),
        downloadUrl = "https://github.com/utkarshdalal/GameNative/releases/latest/download/GameNative.apk",
        estimatedSizeLabel = "~75 MB",
        description = "Engine wrapper PC Windows x86/x64 dengan Turnip driver & DXVK.",
        version = "latest",
        githubRepo = "utkarshdalal/GameNative"
    )

    val melonDsEngine = EngineInfo(
        id = "melonds",
        name = "melonDS",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.MelonDS,
        primaryPackageName = "net.kuribo64.melonDS",
        downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/download/1.9.3/melonDS-1.9.3.apk",
        estimatedSizeLabel = "~12 MB",
        description = "Emulator open-source Nintendo DS presisi tinggi dengan JIT ARM.",
        version = "1.9.3",
        githubRepo = "rafaelvcaetano/melonDS-android"
    )

    val lime3dsEngine = EngineInfo(
        id = "lime3ds",
        name = "Lime3DS",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Lime3DS,
        primaryPackageName = "io.github.lime3ds",
        downloadUrl = "https://github.com/Lime3DS/Lime3DS/releases/latest/download/lime3ds-android.apk",
        estimatedSizeLabel = "~35 MB",
        description = "Penerus Citra terbaik untuk emulasi Nintendo 3DS di Android.",
        version = "latest",
        githubRepo = "Lime3DS/Lime3DS"
    )

    val sudachiEngine = EngineInfo(
        id = "sudachi",
        name = "Sudachi",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Sudachi,
        primaryPackageName = "org.sudachi.sudachi_emu",
        downloadUrl = "https://github.com/emuplace/sudachi.emuplace.app/releases/latest/download/sudachi.apk",
        estimatedSizeLabel = "~45 MB",
        description = "Emulator Nintendo Switch performa tinggi berbasis Yuzu.",
        version = "latest",
        githubRepo = "emuplace/sudachi.emuplace.app"
    )

    val aps3eEngine = EngineInfo(
        id = "aps3e",
        name = "APS3e (RPCS3 Android)",
        platform = GamePlatform.PS3,
        targetEmulator = TargetEmulator.ARMSX3,
        primaryPackageName = "com.tinyproot.aps3e",
        alternatePackageNames = listOf("com.armsx3.play", "org.aps3e", "org.rpcs3"),
        downloadUrl = "https://github.com/APS3e/APS3e/releases/latest/download/APS3e.apk",
        estimatedSizeLabel = "~26 MB",
        description = "Engine PlayStation 3 ultra-ringan (~26 MB) generasi baru berbasis RPCS3 & Vulkan.",
        version = "v1.2.0",
        githubRepo = "APS3e/APS3e"
    )

    val duckStationEngine = EngineInfo(
        id = "duckstation",
        name = "DuckStation",
        platform = GamePlatform.PS1,
        targetEmulator = TargetEmulator.DuckStation,
        primaryPackageName = "com.github.stenzek.duckstation",
        alternatePackageNames = listOf("org.duckstation.android"),
        downloadUrl = "https://github.com/stenzek/duckstation/releases/latest/download/duckstation-android.apk",
        estimatedSizeLabel = "~16 MB",
        description = "Emulator PlayStation 1 akurat performa tinggi dengan dukungan CHD & PGXP.",
        version = "latest",
        githubRepo = "stenzek/duckstation"
    )

    val armsx3Engine = EngineInfo(
        id = "armsx3",
        name = "ARMSX3",
        platform = GamePlatform.PS3,
        targetEmulator = TargetEmulator.ARMSX3,
        primaryPackageName = "com.armsx3.play",
        alternatePackageNames = listOf("org.aps3e"),
        downloadUrl = "https://github.com/ARMSX3/ARMSX3/releases/latest/download/ARMSX3.apk",
        estimatedSizeLabel = "~65 MB",
        description = "Experimental PlayStation 3 runner core.",
        version = "latest",
        githubRepo = "ARMSX3/ARMSX3"
    )

    val lemuroidEngine = EngineInfo(
        id = "lemuroid",
        name = "Lemuroid",
        platform = GamePlatform.NINTENDO,
        targetEmulator = TargetEmulator.Lemuroid,
        primaryPackageName = "com.swordfish.lemuroid",
        downloadUrl = "https://github.com/Swordfish90/Lemuroid/releases/download/1.17.0/lemuroid-app-free-dynamic-release.apk",
        estimatedSizeLabel = "~22 MB",
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
        lime3dsEngine,
        sudachiEngine,
        aps3eEngine,
        armsx3Engine,
        lemuroidEngine
    )

    // =========================================================================
    // Dynamic GitHub Releases API Resolver (Anti-404 Asset Finder)
    // =========================================================================

    /**
     * Parsing daftar assets dari JSON respons GitHub Releases:
     * https://api.github.com/repos/{owner}/{repo}/releases/latest
     *
     * Mengekstrak browser_download_url dari aset pertama yang berakhiran .apk
     * dengan prioritas arsitektur arm64 / v8a / universal / android.
     */
    fun parseAssetApkUrlFromReleaseJson(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            val assets = root.optJSONArray("assets") ?: return null
            var bestUrl: String? = null
            var fallbackUrl: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "").lowercase(Locale.ROOT)
                val downloadUrl = asset.optString("browser_download_url", "")

                if (name.endsWith(".apk") && downloadUrl.isNotBlank()) {
                    val isArm64OrUniversal = name.contains("arm64") ||
                            name.contains("v8a") ||
                            name.contains("universal") ||
                            name.contains("android")

                    if (isArm64OrUniversal && bestUrl == null) {
                        bestUrl = downloadUrl
                    }
                    if (fallbackUrl == null) {
                        fallbackUrl = downloadUrl
                    }
                }
            }
            bestUrl ?: fallbackUrl
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Menghubungi GitHub Releases API untuk mencari URL aset APK rilis terbaru secara dinamis.
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
     * 3. Jika API gagal (rate limit/offline), fallback ke URL rilis default.
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
            TargetEmulator.Lime3DS -> lime3dsEngine
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
                instanceFollowRedirects = true
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
        }
    }

    /**
     * Mengunduh file APK engine resmi langsung ke direktori cache/engines NexusDeck
     * lengkap dengan update progress flow secara asinkron.
     * Menggunakan resolusi URL dinamis via GitHub Releases API untuk mencegah HTTP 404.
     */
    fun downloadEngineApk(
        context: Context,
        engineInfo: EngineInfo
    ): Flow<EngineDownloadProgress> = flow {
        val enginesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "engines")
        if (!enginesDir.exists()) {
            enginesDir.mkdirs()
        }

        val apkFile = File(enginesDir, "${engineInfo.id}.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        val targetUrl = resolveDynamicApkUrl(engineInfo)

        var conn: HttpURLConnection? = null
        try {
            conn = openConnectionWithRedirects(targetUrl)
            conn.connect()
            var code = conn.responseCode

            // Jika targetUrl dinamis mengembalikan 404 dan berbeda dari default, coba default
            if (code == 404 && targetUrl != engineInfo.downloadUrl) {
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
