package com.nexusdeck.app.core.catalog.health

import android.content.Context
import android.content.SharedPreferences
import com.nexusdeck.app.core.downloader.LinkCheckResult
import com.nexusdeck.app.core.downloader.LinkVerifier.LinkVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * CatalogHealthStore — cache tahan lama untuk status kesehatan tautan
 * katalog (v1.1.9 — katalog yang menyeleksi sendiri).
 *
 * Penyimpanan: satu string JSON berversi di SharedPreferences (pola yang
 * sama dengan yang dipakai GameInstallManager untuk state instalasi), plus
 * [StateFlow] di memori supaya grid bereaksi langsung setiap kali satu
 * putusan baru mendarat. Format JSON-nya sama persis dengan yang ditulis
 * skrip CI (`tools/check-catalog.py`) ke `assets/catalog-health.json`,
 * sehingga snapshot CI bisa dimuat sebagai seed saat pertama kali jalan.
 *
 * Yang TIDAK dilakukan store ini: menghapus entri. Entri DEAD tetap
 * tersimpan (dengan `lastAliveAt` dan `consecutiveFailures`) sampai manusia
 * menghapus game itu dari katalog.
 */
class CatalogHealthStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(loadInitial())
    val snapshot: StateFlow<CatalogHealthSnapshot?> = _snapshot.asStateFlow()

    private val _showUnavailable = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_UNAVAILABLE, false)
    )
    val showUnavailable: StateFlow<Boolean> = _showUnavailable.asStateFlow()

    fun setShowUnavailable(value: Boolean) {
        _showUnavailable.value = value
        prefs.edit().putBoolean(KEY_SHOW_UNAVAILABLE, value).apply()
    }

    fun healthFor(gameId: String): LinkHealth? = _snapshot.value?.entries?.get(gameId)

    fun isVisible(gameId: String, showUnavailable: Boolean): Boolean {
        if (showUnavailable) return true
        val health = healthFor(gameId) ?: return true
        return health.status != LinkStatus.DEAD
    }

    /**
     * Mencatat satu putusan baru untuk satu game. Aturan penggabungan
     * (hysteresis) ada di [mergeHealth]: satu kegagalan keras tidak pernah
     * langsung menyembunyikan game.
     */
    fun recordVerdict(
        gameId: String,
        verdict: LinkVerdict,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val current = _snapshot.value
            ?: CatalogHealthSnapshot(generatedBy = "device")
        val merged = mergeHealth(current.entries[gameId], gameId, verdict, nowMs)
        save(
            current.copy(
                generatedAtEpochMs = nowMs,
                entries = current.entries + (gameId to merged)
            )
        )
    }

    /**
     * Mencatat hasil pre-flight gerbang unduhan ([LinkCheckResult]) ke dalam
     * snapshot. Dipanggil dari [com.nexusdeck.app.core.manager.GameInstallManager]
     * setiap kali unduhan lolos atau ditolak oleh LinkVerifier — jadi satu
     * kali unduhan yang gagal 404 ikut terhitung sebagai satu kegagalan
     * keras, dan unduhan yang berhasil membuktikan tautan itu hidup.
     */
    fun recordLinkCheck(
        gameId: String,
        result: LinkCheckResult,
        nowMs: Long = System.currentTimeMillis()
    ) {
        recordVerdict(gameId, result.toVerdict(), nowMs)
    }

    /**
     * Memuat snapshot bawaan dari assets bila belum ada cache sama sekali.
     * Snapshot ini ditulis skrip CI harian (`tools/check-catalog.py`).
     * Gagal memuat (tidak ada file / format rusak) bukan error — katalog
     * mulai dari NOT_CHECKED dan pemeriksa perangkat yang mengambil alih.
     */
    fun loadBundledSnapshotIfEmpty() {
        if (_snapshot.value != null) return
        try {
            val json = appContext.assets.open(ASSET_PATH)
                .bufferedReader().use { it.readText() }
            val parsed = snapshotFromJson(json)
            if (parsed != null) save(parsed)
        } catch (_: Exception) {
            // Tidak ada snapshot bawaan — bukan error.
        }
    }

    private fun loadInitial(): CatalogHealthSnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            snapshotFromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun save(snapshot: CatalogHealthSnapshot) {
        _snapshot.value = snapshot
        prefs.edit().putString(KEY_SNAPSHOT, snapshotToJson(snapshot)).apply()
    }

    companion object {
        const val PREFS_NAME = "nexusdeck_catalog_health"
        const val KEY_SNAPSHOT = "catalog_health_snapshot_v1"
        const val KEY_SHOW_UNAVAILABLE = "show_unavailable_games"
        const val ASSET_PATH = "catalog-health.json"
        const val SNAPSHOT_VERSION = 1

        /**
         * Serialisasi snapshot ke JSON. Format ini HARUS sama dengan yang
         * ditulis `tools/check-catalog.py` — keduanya membaca/menulis
         * `version`, `generatedAtEpochMs`, `generatedBy`, dan `entries`
         * (map id → objek LinkHealth).
         */
        fun snapshotToJson(snapshot: CatalogHealthSnapshot): String {
            val root = JSONObject()
            root.put("version", snapshot.version)
            root.put("generatedAtEpochMs", snapshot.generatedAtEpochMs)
            root.put("generatedBy", snapshot.generatedBy)
            val entries = JSONObject()
            for ((gameId, health) in snapshot.entries) {
                val entry = JSONObject()
                entry.put("status", health.status.name)
                if (health.httpStatus != null) {
                    entry.put("httpStatus", health.httpStatus)
                }
                entry.put("checkedAtEpochMs", health.checkedAtEpochMs)
                if (health.lastAliveAtEpochMs != null) {
                    entry.put("lastAliveAtEpochMs", health.lastAliveAtEpochMs)
                }
                entry.put("consecutiveFailures", health.consecutiveFailures)
                if (health.finalUrl != null) {
                    entry.put("finalUrl", health.finalUrl)
                }
                if (health.reason != null) {
                    entry.put("reason", health.reason)
                }
                entries.put(gameId, entry)
            }
            root.put("entries", entries)
            return root.toString()
        }

        /**
         * Parsing snapshot dari JSON. Mengembalikan null bila versi tidak
         * dikenal atau formatnya rusak — pemanggil memperlakukannya sebagai
         * "tidak ada snapshot".
         */
        fun snapshotFromJson(json: String): CatalogHealthSnapshot? {
            val root = JSONObject(json)
            if (root.optInt("version", -1) != SNAPSHOT_VERSION) return null
            val entries = LinkedHashMap<String, LinkHealth>()
            val entriesJson = root.optJSONObject("entries") ?: JSONObject()
            val keys = entriesJson.keys()
            while (keys.hasNext()) {
                val gameId = keys.next()
                val entry = entriesJson.optJSONObject(gameId) ?: continue
                val status = try {
                    LinkStatus.valueOf(entry.optString("status", "NOT_CHECKED"))
                } catch (_: IllegalArgumentException) {
                    LinkStatus.NOT_CHECKED
                }
                entries[gameId] = LinkHealth(
                    gameId = gameId,
                    status = status,
                    httpStatus = if (entry.has("httpStatus")) {
                        entry.optInt("httpStatus")
                    } else {
                        null
                    },
                    checkedAtEpochMs = entry.optLong("checkedAtEpochMs", 0L),
                    lastAliveAtEpochMs = if (entry.has("lastAliveAtEpochMs")) {
                        entry.optLong("lastAliveAtEpochMs")
                    } else {
                        null
                    },
                    consecutiveFailures = entry.optInt("consecutiveFailures", 0),
                    finalUrl = if (entry.has("finalUrl")) {
                        entry.optString("finalUrl")
                    } else {
                        null
                    },
                    reason = if (entry.has("reason")) {
                        entry.optString("reason")
                    } else {
                        null
                    }
                )
            }
            return CatalogHealthSnapshot(
                version = SNAPSHOT_VERSION,
                generatedAtEpochMs = root.optLong("generatedAtEpochMs", 0L),
                generatedBy = root.optString("generatedBy", "device"),
                entries = entries
            )
        }
    }
}
