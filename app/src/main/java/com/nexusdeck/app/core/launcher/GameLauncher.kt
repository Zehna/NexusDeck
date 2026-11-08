package com.nexusdeck.app.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.TargetEmulator

sealed class LauncherResult {
    data object Launched : LauncherResult()
    data class EmulatorNotFound(
        val emulatorName: String,
        val packageName: String,
        val downloadUrl: String
    ) : LauncherResult()
    data class LaunchError(val message: String) : LauncherResult()
}

object GameLauncher {

    private const val TAG = "NexusDeckLauncher"

    private data class EmulatorConfig(
        val emulatorName: String,
        val packageName: String,
        val downloadUrl: String
    )

    private fun getEmulatorConfig(targetEmulator: TargetEmulator): EmulatorConfig {
        return when (targetEmulator) {
            TargetEmulator.PPSSPP -> EmulatorConfig(
                emulatorName = "PPSSPP",
                packageName = "org.ppsspp.ppsspp",
                downloadUrl = "https://www.ppsspp.org/download/"
            )
            TargetEmulator.NexusRetroCore -> EmulatorConfig(
                emulatorName = "RetroArch",
                packageName = "com.retroarch",
                downloadUrl = "https://www.retroarch.com/?page=platforms"
            )
            TargetEmulator.ARMSX2 -> EmulatorConfig(
                emulatorName = "ARMSX2",
                packageName = "come.nanodata.armsx2",
                downloadUrl = "https://github.com/ARMSX2/ARMSX2/releases"
            )
            TargetEmulator.ARMSX3 -> EmulatorConfig(
                emulatorName = "ARMSX3",
                packageName = "com.armsx3.play",
                downloadUrl = "https://github.com/ARMSX3/ARMSX3/releases"
            )
            TargetEmulator.GameNative -> EmulatorConfig(
                emulatorName = "GameNative",
                packageName = "app.gamenative",
                downloadUrl = "https://gamenative.app/download"
            )
        }
    }

    fun launchGame(
        context: Context,
        game: GameItem,
        romPath: String,
        engineConfig: EngineConfig,
        autoTuneEnabled: Boolean = true
    ): LauncherResult {
        val config = getEmulatorConfig(game.targetEmulator)

        Log.i(
            TAG,
            """
            ============================================================
            [NexusDeck] Preparing Launch for "${game.title}" (${game.platform.displayName})
            Target Emulator : ${config.emulatorName} (${config.packageName})
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

        if (!isPackageInstalled(context, config.packageName)) {
            Log.w(TAG, "Target emulator ${config.packageName} not installed on device.")
            return LauncherResult.EmulatorNotFound(
                emulatorName = config.emulatorName,
                packageName = config.packageName,
                downloadUrl = config.downloadUrl
            )
        }

        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("file://$romPath"), "*/*")
                setPackage(config.packageName)
                applyLaunchExtras(game, romPath, engineConfig, autoTuneEnabled)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                LauncherResult.Launched
            } else {
                val fallbackIntent =
                    context.packageManager.getLaunchIntentForPackage(config.packageName)?.apply {
                        applyLaunchExtras(game, romPath, engineConfig, autoTuneEnabled)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                if (fallbackIntent != null) {
                    context.startActivity(fallbackIntent)
                    LauncherResult.Launched
                } else {
                    LauncherResult.LaunchError(
                        "Tidak dapat membuka ${config.emulatorName}."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${game.title}", e)
            LauncherResult.LaunchError(
                e.localizedMessage ?: "Gagal menjalankan ${game.title}."
            )
        }
    }

    private fun Intent.applyLaunchExtras(
        game: GameItem,
        romPath: String,
        engineConfig: EngineConfig,
        autoTuneEnabled: Boolean
    ) {
        putExtra("rom_path", romPath)
        putExtra("game_id", game.id)
        putExtra("game_title", game.title)
        putExtra("extra_auto_tune_enabled", autoTuneEnabled)
        putExtra("extra_driver_name", engineConfig.driverName)
        putExtra("extra_target_resolution", engineConfig.targetResolution)
        putExtra("extra_renderer_backend", engineConfig.rendererBackend)
        putExtra("extra_dxvk_version", engineConfig.dxvkVersion)
        putExtra("extra_cpu_dynarec_preset", engineConfig.cpuDynarecPreset)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
