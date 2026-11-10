package com.nexusdeck.app.core.manager

import android.content.Context
import android.content.SharedPreferences
import com.nexusdeck.app.core.catalog.health.CatalogHealthStore
import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.data.MockGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AutoPlayEvent(
    val game: GameItem,
    val romPath: String,
    val eventId: Long = System.currentTimeMillis()
)

class GameInstallManager(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val healthStore: CatalogHealthStore? = null
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

    private val _autoPlayTrigger = MutableStateFlow<AutoPlayEvent?>(null)
    val autoPlayTrigger: StateFlow<AutoPlayEvent?> = _autoPlayTrigger.asStateFlow()

    fun consumeAutoPlayTrigger() {
        _autoPlayTrigger.value = null
    }

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
                        isWebResult = obj.optBoolean("isWebResult", true),
                        sourceRepositoryName = obj.optString("sourceRepositoryName", "Nexus Archive")
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
     * Installs a game for real: collects RealGameDownloader.downloadGameFile()
     * and surfaces its actual progress. A game is only marked Installed when
     * the flow emits RealDownloadProgress.Completed with a validated file.
     *
     * v1.1.5 deletes the old fake flow (28 steps x 40 ms of invented
     * progress, an 800 ms fake "extraction", then a fabricated
     * NEXUSDECK_ROM_CONTAINER text file passed off as a ROM). If the game
     * was already downloaded and validated, it is marked Installed
     * immediately without re-downloading.
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

        // Already downloaded and validated on disk? Skip the download.
        realDownloader.getVerifiedLocalGameFile(game)?.let { existingPath ->
            persistInstalledPath(game.id, existingPath)
            updateGameState(game.id, GameState.Installed(romPath = existingPath))
            _autoPlayTrigger.value = AutoPlayEvent(game = game, romPath = existingPath)
            return
        }

        activeInstallJobs[game.id]?.cancel()
        activeInstallJobs[game.id] = coroutineScope.launch(Dispatchers.Main) {
            // 1. BIOS / Firmware Otomatis sesuai kebutuhan konsol
            // (PS1, PS2, PS3) — sebelum mengunduh gamenya.
            val requiredBios = com.nexusdeck.app.core.bios.BiosManager.getRequiredEntryForPlatform(game.platform)
            if (requiredBios != null && !com.nexusdeck.app.core.bios.BiosManager.isBiosOrFirmwareReady(appContext, game.platform)) {
                updateGameState(game.id, GameState.Downloading(progress = 0.02f))
                com.nexusdeck.app.core.bios.BiosManager.ensureBiosReady(appContext, game.platform)
            }

            // 2. Unduhan nyata via DownloadManager — progresnya adalah
            // progres unduhan yang sebenarnya, bukan animasi.
            try {
                realDownloader.downloadGameFile(
                    game,
                    game.downloadUrl,
                    // v1.1.9 — hasil pre-flight gerbang unduhan dicatat ke
                    // snapshot kesehatan katalog (lihat CatalogHealthStore):
                    // 404/HTML = satu kegagalan keras, lolos = ALIVE.
                    onLinkCheck = { result ->
                        healthStore?.recordLinkCheck(game.id, result)
                    }
                ).collect { progress ->
                    when (progress) {
                        is com.nexusdeck.app.core.downloader.RealDownloadProgress.InProgress -> {
                            updateGameState(game.id, GameState.Downloading(progress = progress.progress))
                        }
                        is com.nexusdeck.app.core.downloader.RealDownloadProgress.Completed -> {
                            val finalRomPath = progress.localFilePath
                            persistInstalledPath(game.id, finalRomPath)
                            updateGameState(game.id, GameState.Installed(romPath = finalRomPath))

                            // 3. Auto-Play Pipeline Trigger: otomatis luncurkan / boot game
                            _autoPlayTrigger.value = AutoPlayEvent(game = game, romPath = finalRomPath)
                            activeInstallJobs.remove(game.id)
                        }
                        is com.nexusdeck.app.core.downloader.RealDownloadProgress.Failed -> {
                            updateGameState(game.id, GameState.Failed(message = progress.reason))
                            activeInstallJobs.remove(game.id)
                        }
                    }
                }
            } catch (e: Exception) {
                updateGameState(
                    game.id,
                    GameState.Failed(message = e.localizedMessage ?: "Unduhan gagal.")
                )
                activeInstallJobs.remove(game.id)
            }
        }
    }

    /**
     * Mendaftarkan file game lokal (hasil pindai SAF, Fase 1) ke pustaka:
     * metadata game disimpan dan statusnya langsung Installed dengan
     * content URI sebagai romPath — tidak ada unduhan. Hanya menerima
     * ID game lokal ("local_...") supaya game katalog tidak tertimpa.
     */
    fun registerLocalFiles(items: List<Pair<GameItem, String>>) {
        items.forEach { (game, contentUri) ->
            if (!game.id.startsWith("local_")) return@forEach
            rememberGameMetadata(game)
            persistInstalledPath(game.id, contentUri)
            updateGameState(game.id, GameState.Installed(romPath = contentUri))
        }
    }

    /**
     * Menghapus satu game lokal dari pustaka (dipakai prune saat pindai
     * ulang: filenya sudah tidak ada di folder yang berhasil dipindai).
     * Hanya untuk ID game lokal ("local_...").
     */
    fun removeLocalGame(gameId: String) {
        if (!gameId.startsWith("local_")) return
        _gameStates.update { current -> current - gameId }
        _savedLibraryGames.update { current ->
            val updated = LinkedHashMap(current)
            updated.remove(gameId)
            persistWebGamesCatalog(updated.values.toList())
            updated
        }
        val paths = JSONObject(prefs.getString(KEY_INSTALLED_PATHS, "{}") ?: "{}")
        paths.remove(gameId)
        prefs.edit().putString(KEY_INSTALLED_PATHS, paths.toString()).apply()
        if (gameId in _favoriteIds.value) {
            val updatedFavorites = _favoriteIds.value - gameId
            _favoriteIds.value = updatedFavorites
            prefs.edit().putStringSet(KEY_FAVORITE_IDS, updatedFavorites).apply()
        }
        if (_lastPlayedGameId.value == gameId) {
            _lastPlayedGameId.value = null
            prefs.edit().remove(KEY_LAST_PLAYED).apply()
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
                put("sourceRepositoryName", item.sourceRepositoryName)
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
