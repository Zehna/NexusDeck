package com.nexusdeck.app.core.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.nexusdeck.app.core.downloader.RealDownloadProgress
import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GameInstallManager(
    context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val realDownloader = RealGameDownloader(appContext)

    private val _gameStates = MutableStateFlow<Map<String, GameState>>(loadPersistedStates())
    val gameStates: StateFlow<Map<String, GameState>> = _gameStates.asStateFlow()

    private val activeInstallJobs = mutableMapOf<String, Job>()

    private fun loadPersistedStates(): Map<String, GameState> {
        val persistedMap = mutableMapOf<String, GameState>()
        prefs.all.forEach { (gameId, value) ->
            val romPath = value as? String
            if (!romPath.isNullOrBlank()) {
                persistedMap[gameId] = GameState.Installed(romPath)
            }
        }
        return persistedMap
    }

    fun getStateForGame(gameId: String): GameState {
        return _gameStates.value[gameId] ?: GameState.NotInstalled
    }

    /**
     * Menginstal game. Jika [realPackageUrl] disediakan, gunakan [RealGameDownloader]
     * berbasis android.app.DownloadManager; jika gagal (mis. offline) atau tidak disediakan,
     * jalankan simulasi progresif yang mulus.
     */
    fun installGame(
        game: GameItem,
        realPackageUrl: String? = null
    ) {
        val currentState = getStateForGame(game.id)
        if (currentState is GameState.Downloading ||
            currentState is GameState.Extracting ||
            currentState is GameState.Installed
        ) {
            return
        }

        activeInstallJobs[game.id]?.cancel()
        activeInstallJobs[game.id] = coroutineScope.launch(Dispatchers.Main) {
            var downloadedPath: String? = null

            if (!realPackageUrl.isNullOrBlank()) {
                realDownloader.downloadGameFile(game, realPackageUrl).collect { status ->
                    when (status) {
                        is RealDownloadProgress.InProgress -> {
                            updateGameState(
                                game.id,
                                GameState.Downloading(progress = status.progress)
                            )
                        }
                        is RealDownloadProgress.Completed -> {
                            downloadedPath = status.localFilePath
                        }
                        is RealDownloadProgress.Failed -> {
                            downloadedPath = null
                        }
                    }
                }
            }

            // Fallback simulasi unduhan progresif jika URL tidak diisi atau perangkat sedang offline
            if (downloadedPath == null) {
                val totalSteps = 25
                for (step in 1..totalSteps) {
                    val progress = step.toFloat() / totalSteps.toFloat()
                    updateGameState(game.id, GameState.Downloading(progress = progress))
                    delay(90L)
                }
                downloadedPath = buildLocalRomPath(game)
            }

            // Transisi ke status Extracting (uncompress / optimasi aset) selama 2 detik
            updateGameState(game.id, GameState.Extracting)
            delay(2000L)

            // Selesai -> Status Installed & simpan secara lokal di SharedPreferences
            val finalRomPath = downloadedPath ?: buildLocalRomPath(game)
            prefs.edit().putString(game.id, finalRomPath).apply()
            updateGameState(game.id, GameState.Installed(romPath = finalRomPath))
            activeInstallJobs.remove(game.id)
        }
    }

    private fun updateGameState(gameId: String, state: GameState) {
        _gameStates.update { currentMap ->
            currentMap.toMutableMap().apply {
                put(gameId, state)
            }
        }
    }

    private fun buildLocalRomPath(game: GameItem): String {
        val extension = when (game.platform) {
            GamePlatform.PC -> "exe"
            GamePlatform.PS2 -> "iso"
            GamePlatform.PS3 -> "iso"
        }
        val folder = game.platform.name.lowercase()
        val baseDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return if (baseDir != null) {
            File(baseDir, "roms/$folder/${game.id}.$extension").absolutePath
        } else {
            "/storage/emulated/0/NexusDeck/roms/$folder/${game.id}.$extension"
        }
    }

    companion object {
        private const val PREFS_NAME = "nexusdeck_installed_games"
    }
}
