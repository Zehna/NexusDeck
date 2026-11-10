package com.nexusdeck.app.core.library

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.nexusdeck.app.core.model.GamePlatform
import org.json.JSONObject

/**
 * Penyimpanan folder pustaka lokal (Fase 1): folder SAF yang dipilih
 * pengguna, masing-masing terikat ke satu sistem, plus pemetaan
 * game-lokal -> folder asalnya (untuk prune saat pindai ulang: game
 * yang filenya sudah tidak ada di folder yang berhasil dipindai akan
 * dihapus dari pustaka).
 *
 * Izin baca folder diambil sebagai persistable URI permission supaya
 * tetap berlaku setelah aplikasi ditutup.
 */
class LibraryFolderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Map folderUri (string) -> nama GamePlatform. */
    fun allFolders(): Map<String, GamePlatform> {
        val result = LinkedHashMap<String, GamePlatform>()
        try {
            val obj = JSONObject(prefs.getString(KEY_FOLDERS, "{}") ?: "{}")
            obj.keys().forEach { uri ->
                val platformName = obj.optString(uri)
                runCatching { GamePlatform.valueOf(platformName) }
                    .onSuccess { result[uri] = it }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun platformForFolder(folderUri: String): GamePlatform? = allFolders()[folderUri]

    fun addFolder(treeUri: Uri, platform: GamePlatform) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Izin persistable tidak tersedia (mis. provider tidak
            // mendukung) — pemindaian tetap dicoba; gagal berarti
            // hasil pindai kosong.
        }
        val folders = JSONObject(prefs.getString(KEY_FOLDERS, "{}") ?: "{}")
        folders.put(treeUri.toString(), platform.name)
        prefs.edit().putString(KEY_FOLDERS, folders.toString()).apply()
    }

    fun removeFolder(folderUri: String) {
        val folders = JSONObject(prefs.getString(KEY_FOLDERS, "{}") ?: "{}")
        folders.remove(folderUri)
        prefs.edit().putString(KEY_FOLDERS, folders.toString()).apply()
    }

    fun recordGameFolder(gameId: String, folderUri: String) {
        val map = JSONObject(prefs.getString(KEY_GAME_FOLDERS, "{}") ?: "{}")
        map.put(gameId, folderUri)
        prefs.edit().putString(KEY_GAME_FOLDERS, map.toString()).apply()
    }

    fun folderForGame(gameId: String): String? {
        return try {
            val map = JSONObject(prefs.getString(KEY_GAME_FOLDERS, "{}") ?: "{}")
            map.optString(gameId).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun gameIdsForFolder(folderUri: String): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val map = JSONObject(prefs.getString(KEY_GAME_FOLDERS, "{}") ?: "{}")
            map.keys().forEach { gameId ->
                if (map.optString(gameId) == folderUri) result.add(gameId)
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun forgetGame(gameId: String) {
        val map = JSONObject(prefs.getString(KEY_GAME_FOLDERS, "{}") ?: "{}")
        map.remove(gameId)
        prefs.edit().putString(KEY_GAME_FOLDERS, map.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "nexusdeck_library_folders_v1"
        private const val KEY_FOLDERS = "folders_json"
        private const val KEY_GAME_FOLDERS = "game_folders_json"
    }
}
