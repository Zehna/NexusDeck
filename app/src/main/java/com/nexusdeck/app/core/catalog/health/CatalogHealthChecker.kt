package com.nexusdeck.app.core.catalog.health

import com.nexusdeck.app.core.downloader.LinkVerifier
import com.nexusdeck.app.core.downloader.LinkVerifier.LinkVerdict
import com.nexusdeck.app.core.downloader.RealGameDownloader
import com.nexusdeck.app.core.model.GameItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * CatalogHealthChecker — penyapuan latar yang memeriksa ulang tautan
 * katalog (v1.1.9 — katalog yang menyeleksi sendiri).
 *
 * Aturan main:
 * - Hanya item yang sudah basi yang diperiksa bila `staleOnly = true`
 *   (ALIVE/DEAD basi setelah 24 jam, UNKNOWN setelah 1 jam, NOT_CHECKED
 *   selalu basi).
 * - Maks. 4 permintaan bersamaan secara global, maks. 2 per host untuk
 *   archive.org — supaya satu sapuan tidak membombardir satu host.
 * - Satu item yang gagal tidak menggagalkan sapuan (`supervisorScope` +
 *   `runCatching` per item); setiap putusan langsung dicatat ke store
 *   begitu mendarat, jadi grid diperbarui bertahap, bukan menunggu sapuan
 *   selesai.
 * - Satu sapuan penuh dibatasi 5 menit; bila kehabisan waktu, hasil yang
 *   sudah tercatat tetap dipakai.
 * - Single-flight: bila sapuan sudah berjalan, pemanggilan berikutnya
 *   langsung mengembalikan snapshot saat ini tanpa memulai sapuan baru.
 */
class CatalogHealthChecker(private val store: CatalogHealthStore) {

    private val refreshMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refresh(
        games: List<GameItem>,
        staleOnly: Boolean = true,
        nowMs: () -> Long = { System.currentTimeMillis() }
    ): Map<String, LinkHealth> {
        if (!refreshMutex.tryLock()) {
            return store.snapshot.value?.entries ?: emptyMap()
        }
        _isRefreshing.value = true
        try {
            val result = withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
                supervisorScope {
                    val globalSemaphore = Semaphore(MAX_CONCURRENT)
                    val perHostSemaphores = ConcurrentHashMap<String, Semaphore>()
                    val jobs = games.map { game ->
                        async(Dispatchers.IO) {
                            if (game.downloadUrl.isBlank()) return@async null
                            val existing = store.healthFor(game.id)
                            if (staleOnly && existing != null && !existing.isStale(nowMs())) {
                                return@async null
                            }
                            globalSemaphore.withPermit {
                                val host = try {
                                    URL(game.downloadUrl).host.lowercase()
                                } catch (_: Exception) {
                                    ""
                                }
                                val verdict = if (host.contains("archive.org")) {
                                    val hostSemaphore = perHostSemaphores.getOrPut(host) {
                                        Semaphore(MAX_PER_HOST_ARCHIVE_ORG)
                                    }
                                    hostSemaphore.withPermit { checkOne(game) }
                                } else {
                                    checkOne(game)
                                }
                                store.recordVerdict(game.id, verdict, nowMs())
                                game.id to (store.healthFor(game.id) ?: return@async null)
                            }
                        }
                    }
                    jobs.mapNotNull { job ->
                        runCatching { job.await() }.getOrNull()
                    }.toMap()
                }
            }
            return result ?: (store.snapshot.value?.entries ?: emptyMap())
        } finally {
            _isRefreshing.value = false
            refreshMutex.unlock()
        }
    }

    private suspend fun checkOne(game: GameItem): LinkVerdict {
        return runCatching {
            LinkVerifier.verifyDetailed(
                game.downloadUrl,
                RealGameDownloader.minimumDownloadBytes(game)
            )
        }.getOrElse {
            LinkVerdict.Unknown(null, "exception", null)
        }
    }

    companion object {
        /** Maks. permintaan bersamaan dalam satu sapuan. */
        const val MAX_CONCURRENT = 4

        /** Maks. permintaan bersamaan ke satu host archive.org. */
        const val MAX_PER_HOST_ARCHIVE_ORG = 2

        /** Batas waktu satu sapuan penuh. */
        const val SWEEP_TIMEOUT_MS = 5L * 60L * 1000L
    }
}
