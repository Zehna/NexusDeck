package com.nexusdeck.app.core.manager

import android.content.Context
import android.content.SharedPreferences
import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.data.MockGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

    private val _savedLibraryGames = MutableStateFlow<Map<String, GameItem>>(loadSavedLibraryGames())
    val savedLibraryGames: StateFlow<Map<String, GameItem>> = _savedLibraryGames.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(loadFavoriteIds())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _lastPlayedGameId = MutableStateFlow<String?>(prefs.getString(KEY_LAST_PLAYED, null))
    val lastPlayedGameId: StateFlow<String?> = _lastPlayedGameId.asStateFlow()

    private val activeInstallJobs = mutableMapOf<String, Job>()

    private fun loadPersistedStates(): Map<String, GameState> {
        val persistedMap = mutableMapOf<String, GameState>()
        val rawJson = prefs.getString(KEY_INSTALLED_PATHS, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(rawJson)
                obj.keys().forEach { gameId ->
                    val path = obj.optString(gameId)
                    if (path.isNotBlank()) {
                        persistedMap[gameId] = GameState.Installed(path)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return persistedMap
    }

    private fun loadSavedLibraryGames(): Map<String, GameItem> {
        val result = LinkedHashMap<String, GameItem>()
        MockGames.extendedCloudRepository.forEach { result[it.id] = it }

        val rawJson = prefs.getString(KEY_SAVED_WEB_GAMES, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val platformName = obj.optString("platform", "PSP")
                    val platform = runCatching { GamePlatform.valueOf(platformName) }
                        .getOrDefault(GamePlatform.PSP)
                    val emulatorName = obj.optString("targetEmulator", "PPSSPP")
                    val targetEmulator = runCatching { TargetEmulator.valueOf(emulatorName) }
                        .getOrDefault(TargetEmulator.PPSSPP)

                    val item = GameItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        platform = platform,
                        coverUrl = obj.optString("coverUrl"),
                        minRamGb = obj.optInt("minRamGb", 4),
                        recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
                        targetEmulator = targetEmulator,
                        fileSizeLabel = obj.optString("fileSizeLabel", "1.2 GB"),
                        downloadUrl = obj.optString("downloadUrl", ""),
                        isWebResult = obj.optBoolean("isWebResult", true)
                    )
                    if (item.id.isNotBlank()) {
                        result[item.id] = item
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun loadFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun toggleFavorite(game: GameItem) {
        rememberGameMetadata(game)
        _favoriteIds.update { current ->
            val updated = current.toMutableSet()
            if (updated.contains(game.id)) {
                updated.remove(game.id)
            } else {
                updated.add(game.id)
            }
            prefs.edit().putStringSet(KEY_FAVORITE_IDS, updated).apply()
            updated
        }
    }

    fun markLastPlayed(game: GameItem) {
        rememberGameMetadata(game)
        _lastPlayedGameId.value = game.id
        prefs.edit().putString(KEY_LAST_PLAYED, game.id).apply()
    }

    fun getStateForGame(gameId: String): GameState {
        return _gameStates.value[gameId] ?: GameState.NotInstalled
    }

    /**
     * Mengunduh game langsung ke folder /games/[platform]/ dan menyimpannya secara permanen
     * ke Library / Downloads agar siap dimainkan kapan saja.
     */
    fun installGame(game: GameItem) {
        val currentState = getStateForGame(game.id)
        if (currentState is GameState.Downloading ||
            currentState is GameState.Extracting ||
            currentState is GameState.Installed
        ) {
            return
        }

        rememberGameMetadata(game)

        activeInstallJobs[game.id]?.cancel()
        activeInstallJobs[game.id] = coroutineScope.launch(Dispatchers.Main) {
            // Alur unduhan progresif ke /games/[platform]/
            val totalSteps = 28
            for (step in 1..totalSteps) {
                val progress = step.toFloat() / totalSteps.toFloat()
                updateGameState(game.id, GameState.Downloading(progress = progress))
                delay(75L)
            }

            // Ekstraksi & verifikasi kontainer ROM di /games/[platform]/
            updateGameState(game.id, GameState.Extracting)
            delay(1400L)

            val finalRomPath = realDownloader.writeVerifiedLocalGameFile(game)
            persistInstalledPath(game.id, finalRomPath)
            updateGameState(game.id, GameState.Installed(romPath = finalRomPath))
            activeInstallJobs.remove(game.id)
        }
    }

    private fun rememberGameMetadata(game: GameItem) {
        _savedLibraryGames.update { current ->
            val updated = LinkedHashMap(current)
            updated[game.id] = game
            persistWebGamesCatalog(updated.values.toList())
            updated
        }
    }

    private fun persistInstalledPath(gameId: String, romPath: String) {
        val currentPaths = JSONObject(prefs.getString(KEY_INSTALLED_PATHS, "{}") ?: "{}")
        currentPaths.put(gameId, romPath)
        prefs.edit().putString(KEY_INSTALLED_PATHS, currentPaths.toString()).apply()
    }

    private fun persistWebGamesCatalog(games: List<GameItem>) {
        val arr = JSONArray()
        games.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("platform", item.platform.name)
                put("coverUrl", item.coverUrl)
                put("minRamGb", item.minRamGb)
                put("targetEmulator", item.targetEmulator.name)
                put("fileSizeLabel", item.fileSizeLabel)
                put("downloadUrl", item.downloadUrl)
                put("isWebResult", item.isWebResult)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_WEB_GAMES, arr.toString()).apply()
    }

    private fun updateGameState(gameId: String, state: GameState) {
        _gameStates.update { currentMap ->
            currentMap.toMutableMap().apply {
                put(gameId, state)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "nexusdeck_library_v2"
        private const val KEY_INSTALLED_PATHS = "installed_paths_json"
        private const val KEY_SAVED_WEB_GAMES = "saved_web_games_json"
        private const val KEY_FAVORITE_IDS = "favorite_game_ids"
        private const val KEY_LAST_PLAYED = "last_played_game_id"
    }
}
