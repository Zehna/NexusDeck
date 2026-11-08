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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

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
    val version: String
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
        version = "v1.20.4"
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
        version = "v2.2n-3668"
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
        version = "latest"
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
        version = "1.9.3"
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
        version = "latest"
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
        version = "latest"
    )

    val armsx3Engine = EngineInfo(
        id = "armsx3",
        name = "ARMSX3",
        platform = GamePlatform.PS3,
        targetEmulator = TargetEmulator.ARMSX3,
        primaryPackageName = "com.armsx3.play",
        downloadUrl = "https://github.com/ARMSX3/ARMSX3/releases/latest/download/ARMSX3.apk",
        estimatedSizeLabel = "~65 MB",
        description = "Experimental PlayStation 3 runner core.",
        version = "latest"
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
        version = "1.17.0"
    )

    /**
     * Seluruh engine yang terdaftar di NexusDeck Registry.
     */
    val registeredEngines: List<EngineInfo> = listOf(
        ppssppEngine,
        netherSx2Engine,
        gameNativeEngine,
        melonDsEngine,
        lime3dsEngine,
        sudachiEngine,
        armsx3Engine,
        lemuroidEngine
    )

    // =========================================================================
    // Lookup & Matching Queries
    // =========================================================================

    fun getEngineById(id: String): EngineInfo? {
        return registeredEngines.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun getEngineForPlatform(platform: GamePlatform): EngineInfo {
        return when (platform) {
            GamePlatform.PSP -> ppssppEngine
            GamePlatform.PS2 -> netherSx2Engine
            GamePlatform.PC -> gameNativeEngine
            GamePlatform.NINTENDO -> lemuroidEngine
            GamePlatform.PS3 -> armsx3Engine
        }
    }

    fun getEngineForEmulator(targetEmulator: TargetEmulator): EngineInfo {
        return when (targetEmulator) {
            TargetEmulator.PPSSPP -> ppssppEngine
            TargetEmulator.ARMSX2 -> netherSx2Engine
            TargetEmulator.GameNative -> gameNativeEngine
            TargetEmulator.MelonDS -> melonDsEngine
            TargetEmulator.Lime3DS -> lime3dsEngine
            TargetEmulator.Sudachi -> sudachiEngine
            TargetEmulator.ARMSX3 -> armsx3Engine
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
                setRequestProperty("User-Agent", "NexusDeck/1.0 EngineDownloader")
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
            setRequestProperty("User-Agent", "NexusDeck/1.0 EngineDownloader")
        }
    }

    /**
     * Mengunduh file APK engine resmi langsung ke direktori cache/engines NexusDeck
     * lengkap dengan update progress flow secara asinkron.
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

        var conn: HttpURLConnection? = null
        try {
            conn = openConnectionWithRedirects(engineInfo.downloadUrl)
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                emit(EngineDownloadProgress.Failed("Server mengembalikan kode status HTTP $code"))
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
                    e.localizedMessage ?: "Gagal mengunduh APK engine ${engineInfo.name}."
                )
            )
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Membangun Intent instalasi paket Android menggunakan FileProvider resmi.
     */
    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } catch (_: Exception) {
            Uri.fromFile(apkFile)
        }

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
