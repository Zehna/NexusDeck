package com.nexusdeck.app.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nexusdeck.app.core.engine.EngineInfo
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import java.io.File

sealed class LauncherResult {
    data object Launched : LauncherResult()
    data class EmulatorNotFound(
        val emulatorName: String,
        val packageName: String,
        val downloadUrl: String,
        val engineInfo: EngineInfo? = null
    ) : LauncherResult()
    data class LaunchError(val message: String) : LauncherResult()
}

object GameLauncher {

    private const val TAG = "NexusDeckLauncher"

    /**
     * Menghasilkan URI yang aman untuk file ROM lokal menggunakan FileProvider.
     * Mengeliminasi FileUriExposedException pada Android modern (SDK 24+).
     */
    fun getSafeRomUri(context: Context, romPath: String): Uri {
        val file = File(romPath)
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    /**
     * Membangun Intent peluncuran dengan parameter URI FileProvider, flags perizinan baca,
     * serta extras spesifik untuk booting langsung ke game pada target emulator:
     * - PPSSPP: SHORTCUT_PATH & bootPath
     * - NetherSX2 / AetherSX2: bootPath & source
     * - Lime3DS: GamePath
     * - GameNative / Winlator: game_path & container_id
     * - melonDS / Sudachi: Intent ACTION_VIEW dengan URI ROM
     */
    fun buildLaunchIntent(
        context: Context,
        game: GameItem,
        romPath: String,
        engineConfig: EngineConfig,
        autoTuneEnabled: Boolean = true,
        targetPackage: String? = null
    ): Intent {
        val engine = EngineManager.getEngineForGame(game)
        val resolvedPackage = targetPackage ?: EngineManager.getInstalledPackage(context, engine) ?: engine.primaryPackageName
        val romUri = getSafeRomUri(context, romPath)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "*/*")
            setPackage(resolvedPackage)
            clipData = android.content.ClipData.newRawUri("ROM_URI", romUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Parameter booting universal & auto-tuning profile
            putExtra("rom_path", romPath)
            putExtra("game_id", game.id)
            putExtra("game_title", game.title)
            putExtra("extra_auto_tune_enabled", autoTuneEnabled)
            putExtra("extra_driver_name", engineConfig.driverName)
            putExtra("extra_target_resolution", engineConfig.targetResolution)
            putExtra("extra_renderer_backend", engineConfig.rendererBackend)
            putExtra("extra_dxvk_version", engineConfig.dxvkVersion)
            putExtra("extra_cpu_dynarec_preset", engineConfig.cpuDynarecPreset)

            // Extras spesifik per engine target
            when {
                resolvedPackage.contains("ppsspp") -> {
                    // Direct component launch ke PpssppActivity untuk melompati menu dan langsung memutar game
                    component = android.content.ComponentName(resolvedPackage, "org.ppsspp.ppsspp.PpssppActivity")
                    setDataAndType(romUri, "application/octet-stream")
                    putExtra("org.ppsspp.ppsspp.SHORTCUT_PATH", romPath)
                    putExtra("SHORTCUT_PATH", romPath)
                    putExtra("bootPath", romPath)
                }
                resolvedPackage.contains("aethersx2") || resolvedPackage.contains("armsx2") -> {
                    // Direct component launch ke EmulationActivity NetherSX2/AetherSX2 dengan bootPath absolut
                    val activityClass = if (resolvedPackage.contains("armsx2")) {
                        "come.nanodata.armsx2.EmulationActivity"
                    } else {
                        "xyz.aethersx2.android.EmulationActivity"
                    }
                    component = android.content.ComponentName(resolvedPackage, activityClass)
                    setDataAndType(romUri, "application/octet-stream")
                    putExtra("bootPath", romPath)
                    putExtra("source", "nexusdeck")
                }
                resolvedPackage.contains("lime3ds") || resolvedPackage.contains("citra") -> {
                    putExtra("GamePath", romPath)
                }
                resolvedPackage.contains("gamenative") || resolvedPackage.contains("winlator") -> {
                    putExtra("game_path", romPath)
                    putExtra("container_id", game.id)
                }
                resolvedPackage.contains("sudachi") -> {
                    putExtra("GamePath", romPath)
                }
                resolvedPackage.contains("melonDS") -> {
                    putExtra("rom_path", romPath)
                }
                resolvedPackage.contains("lemuroid") -> {
                    setDataAndType(romUri, "application/octet-stream")
                    putExtra("rom_path", romPath)
                    putExtra("bootPath", romPath)
                }
            }
        }

        return intent
    }

    /**
     * Memeriksa keberadaan engine dan mengeksekusi peluncuran game asli secara seamless.
     */
    fun launchGame(
        context: Context,
        game: GameItem,
        romPath: String,
        engineConfig: EngineConfig,
        autoTuneEnabled: Boolean = true
    ): LauncherResult {
        val engine = EngineManager.getEngineForGame(game)
        val installedPackage = EngineManager.getInstalledPackage(context, engine)

        Log.i(
            TAG,
            """
            ============================================================
            [NexusDeck] Preparing Native Launch for "${game.title}" (${game.platform.displayName})
            Resolved Engine : ${engine.name} (ID: ${engine.id})
            Target Package  : ${installedPackage ?: engine.primaryPackageName} (Installed: ${installedPackage != null})
            ROM Path        : $romPath
            Auto-Tune Active: $autoTuneEnabled
            -> GPU Driver   : ${engineConfig.driverName}
            -> Resolution   : ${engineConfig.targetResolution}
            -> Backend      : ${engineConfig.rendererBackend}
            -> DXVK Version : ${engineConfig.dxvkVersion}
            -> CPU Dynarec  : ${engineConfig.cpuDynarecPreset}
            ============================================================
            """.trimIndent()
        )

        if (installedPackage == null) {
            Log.w(TAG, "Target engine ${engine.name} not installed on device.")
            return LauncherResult.EmulatorNotFound(
                emulatorName = engine.name,
                packageName = engine.primaryPackageName,
                downloadUrl = engine.downloadUrl,
                engineInfo = engine
            )
        }

        return try {
            val launchIntent = buildLaunchIntent(
                context = context,
                game = game,
                romPath = romPath,
                engineConfig = engineConfig,
                autoTuneEnabled = autoTuneEnabled,
                targetPackage = installedPackage
            )

            try {
                context.startActivity(launchIntent)
                LauncherResult.Launched
            } catch (anfe: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Direct component launch failed, trying package launch intent: ${anfe.message}")
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage(installedPackage)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("ROM_URI", getSafeRomUri(context, romPath))
                    setDataAndType(getSafeRomUri(context, romPath), "application/octet-stream")
                    putExtra("rom_path", romPath)
                    putExtra("bootPath", romPath)
                    putExtra("SHORTCUT_PATH", romPath)
                    putExtra("game_id", game.id)
                }

                if (fallbackIntent != null) {
                    context.startActivity(fallbackIntent)
                    LauncherResult.Launched
                } else {
                    LauncherResult.LaunchError("Tidak dapat membuka ${engine.name}.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan game ${game.title}", e)
            LauncherResult.LaunchError(
                e.localizedMessage ?: "Gagal menjalankan ${game.title}."
            )
        }
    }
}
