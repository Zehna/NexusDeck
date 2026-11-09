package com.nexusdeck.app.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nexusdeck.app.core.bios.BiosManager
import com.nexusdeck.app.core.engine.EngineInfo
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.core.engine.PlatformExecutionMatrix
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import java.io.File

sealed class LauncherResult {
    data object Launched : LauncherResult()
    data class LaunchedWithNotice(val message: String) : LauncherResult()
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
     * JANGAN gunakan Uri.fromFile() di sini: intent yang membawa file:// URI
     * akan melempar FileUriExposedException di Android 24+. Jika FileProvider
     * gagal, lempar IllegalStateException agar launchGame() bisa mengubahnya
     * menjadi LauncherResult.LaunchError, bukan URI file mentah.
     */
    fun getSafeRomUri(context: Context, romPath: String): Uri {
        val file = File(romPath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Membangun Intent peluncuran dengan parameter URI FileProvider, flags perizinan baca,
     * serta extras spesifik untuk booting langsung ke game pada target emulator:
     * - PPSSPP: SHORTCUT_PATH & bootPath
     * - NetherSX2 / AetherSX2: bootPath & source
     * - Lime3DS / Azahar: GamePath
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
                resolvedPackage.contains("aethersx2") || resolvedPackage.contains("armsx2") || resolvedPackage.contains("nethersx2") -> {
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
                    val biosFile = BiosManager.getTargetFileForEntry(context, BiosManager.ps2Bios)
                    if (biosFile.exists()) {
                        putExtra("bios_path", biosFile.absolutePath)
                        putExtra("biosPath", biosFile.absolutePath)
                    }
                }
                resolvedPackage.contains("duckstation") -> {
                    // DuckStation (PS1): Direct Boot ke EmulationActivity dengan verifikasi BIOS scph1001.bin
                    val activityClass = if (resolvedPackage == "com.github.stenzek.duckstation") {
                        "com.github.stenzek.duckstation.EmulationActivity"
                    } else {
                        "org.duckstation.android.EmulationActivity"
                    }
                    component = android.content.ComponentName(resolvedPackage, activityClass)
                    setDataAndType(romUri, "application/octet-stream")
                    putExtra("bootPath", romPath)
                    putExtra("isoPath", romPath)
                    val biosFile = BiosManager.getTargetFileForEntry(context, BiosManager.ps1Bios)
                    if (biosFile.exists()) {
                        putExtra("bios_path", biosFile.absolutePath)
                        putExtra("biosPath", biosFile.absolutePath)
                    }
                }
                resolvedPackage.contains("aps3e") || resolvedPackage.contains("armsx3") || resolvedPackage.contains("tinyproot") || resolvedPackage.contains("rpcs3") -> {
                    // APS3e / ARMSX3 (PS3): Direct Boot dengan parameter path game folder dan firmware terpasang
                    val activityClass = when {
                        resolvedPackage.contains("tinyproot") -> "com.tinyproot.aps3e.MainActivity"
                        resolvedPackage.contains("armsx3") -> "com.armsx3.play.MainActivity"
                        else -> "org.aps3e.MainActivity"
                    }
                    component = android.content.ComponentName(resolvedPackage, activityClass)
                    setDataAndType(romUri, "application/octet-stream")
                    putExtra("bootPath", romPath)
                    putExtra("game_path", romPath)
                    putExtra("game_dir", romPath)
                    val fwFile = BiosManager.getTargetFileForEntry(context, BiosManager.ps3Firmware)
                    if (fwFile.exists()) {
                        putExtra("firmware_path", fwFile.absolutePath)
                        putExtra("firmwarePath", fwFile.absolutePath)
                    }
                }
                // Azahar adalah penerus Lime3DS (package org.azahar_emu.azahar);
                // keluarga Citra memakai extra "GamePath" yang sama.
                resolvedPackage.contains("lime3ds") || resolvedPackage.contains("citra") ||
                    resolvedPackage.contains("azahar") -> {
                    putExtra("GamePath", romPath)
                }
                resolvedPackage.contains("gamenative") || resolvedPackage.contains("winlator") -> {
                    component = android.content.ComponentName(resolvedPackage, "app.gamenative.MainActivity")
                    putExtra("game_path", romPath)
                    putExtra("container_id", game.id)
                    putExtra("extra_driver_name", engineConfig.driverName)
                    putExtra("extra_dxvk_version", engineConfig.dxvkVersion)
                    putExtra("extra_renderer_backend", engineConfig.rendererBackend)
                }
                resolvedPackage.contains("sudachi") -> {
                    putExtra("GamePath", romPath)
                }
                resolvedPackage.contains("melonDS") -> {
                    putExtra("rom_path", romPath)
                }
                resolvedPackage.contains("lemuroid") -> {
                    // Lemuroid has NO documented direct-ROM-boot Intent and no
                    // launchable component for a ROM path. Do not invent one:
                    // launchGame() handles Lemuroid separately via a plain
                    // package launch intent (fallback only). This branch is
                    // intentionally a no-op so a generic ACTION_VIEW is never
                    // sent to Lemuroid with a ROM it cannot open.
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

        // Freedoom guard: Freedoom is a .zip containing .wad files, not a PC
        // game GameNative/Winlator can run — a .wad needs a Doom engine
        // (Delta Touch / GZDoom). There is no GOG routing anywhere in the
        // app; do not launch GameNative for it.
        if (game.id == "pc_freedoom" || game.downloadUrl.endsWith("freedoom-0.12.1.zip")) {
            return LauncherResult.LaunchError(
                "Freedoom membutuhkan Doom engine (Delta Touch / GZDoom) — " +
                    "GameNative tidak bisa menjalankan file .wad. Ekstrak " +
                    "freedoom1.wad dari freedoom-0.12.1.zip ke " +
                    "/storage/emulated/0/NexusDeck/games/pc/freedoom/, lalu buka " +
                    "dengan Delta Touch / GZDoom."
            )
        }

        val rule = PlatformExecutionMatrix.getRule(game.platform)
        val biosReady = BiosManager.isBiosOrFirmwareReady(context, game.platform)

        Log.i(
            TAG,
            """
            ============================================================
            [NexusDeck] Preparing Native Launch for "${game.title}" (${game.platform.displayName})
            Execution Rule  : ${rule.executionType.displayName}
            Required Asset  : ${rule.requiredFileLabel ?: "None"} (Ready: $biosReady)
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

        // PSP direct boot requires the ROM file to actually exist at the
        // absolute path handed to PPSSPP via SHORTCUT_PATH; PPSSPP reads it
        // directly from /storage/emulated/0/NexusDeck/games/psp/.
        if (engine.id == "ppsspp" || game.platform == com.nexusdeck.app.core.model.GamePlatform.PSP) {
            if (!File(romPath).exists()) {
                return LauncherResult.LaunchError(
                    "File ROM PSP tidak ditemukan: $romPath. Unduh ulang game ini terlebih dahulu."
                )
            }
        }

        // Lemuroid fallback only: Lemuroid has no documented direct-ROM-boot
        // Intent, so never send it a ROM ACTION_VIEW. Just open the app and
        // tell the user to point its library folder (once, via SAF) at the
        // shared Nintendo games dir.
        if (engine.id == "lemuroid") {
            val packageIntent = context.packageManager.getLaunchIntentForPackage(installedPackage)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            return if (packageIntent != null) {
                context.startActivity(packageIntent)
                LauncherResult.LaunchedWithNotice(
                    "Lemuroid dibuka. Arahkan folder library Lemuroid (sekali saja, " +
                        "via Storage Access Framework) ke " +
                        "/storage/emulated/0/NexusDeck/games/nintendo/ agar game " +
                        "Nintendo kamu muncul di sana."
                )
            } else {
                LauncherResult.LaunchError("Tidak dapat membuka Lemuroid.")
            }
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
                Log.w(TAG, "Direct component launch failed, trying ACTION_VIEW fallback: ${anfe.message}")
                // Fallback 1: plain ACTION_VIEW with the FileProvider URI and
                // SHORTCUT_PATH, but WITHOUT an explicit component.
                val viewFallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(getSafeRomUri(context, romPath), "application/octet-stream")
                    setPackage(installedPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("ROM_URI", getSafeRomUri(context, romPath))
                    putExtra("rom_path", romPath)
                    putExtra("bootPath", romPath)
                    putExtra("SHORTCUT_PATH", romPath)
                    putExtra("game_id", game.id)
                }
                try {
                    context.startActivity(viewFallback)
                    LauncherResult.Launched
                } catch (_: android.content.ActivityNotFoundException) {
                    // Fallback 2: plain package launch intent (app's own
                    // launcher activity, no ROM attached).
                    Log.w(TAG, "ACTION_VIEW fallback failed, trying package launch intent.")
                    val fallbackIntent = context.packageManager.getLaunchIntentForPackage(installedPackage)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (fallbackIntent != null) {
                        context.startActivity(fallbackIntent)
                        LauncherResult.Launched
                    } else {
                        LauncherResult.LaunchError("Tidak dapat membuka ${engine.name}.")
                    }
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
