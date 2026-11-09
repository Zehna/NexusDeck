package com.nexusdeck.app.core.repository

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import com.nexusdeck.app.core.repository.sources.ArchiveOrgSource
import com.nexusdeck.app.core.repository.sources.FreewarePcSource
import com.nexusdeck.app.core.repository.sources.GameRepositorySource
import com.nexusdeck.app.data.MockGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class OnlineGameCatalogService(
    val sources: List<GameRepositorySource> = listOf(
        ArchiveOrgSource(),
        FreewarePcSource()
        // v1.1.9 — MyrientMirrorSource DIKARANTINA: Myrient tutup pada
        // 31 Maret 2026 (semua URL-nya kini membalas halaman penutup,
        // bukan file). Sumbernya tetap ada di kode untuk arsip, tapi
        // tidak lagi didaftarkan sebagai sumber aktif — game-game Myrient
        // tidak boleh muncul di katalog sampai ada mirror pengganti yang
        // terverifikasi menyediakan unduhan HTTP langsung.
    )
) {

    /**
     * Multi-Source Live Search dengan Failover Otomatis.
     * Menggabungkan hasil dari multi-provider (Internet Archive, Myrient Mirror, Freeware PC).
     * Jika satu provider lambat atau gagal merespons, pencarian otomatis berlanjut ke mirror berikutnya.
     */
    fun searchOnlineGames(
        query: String,
        platform: GamePlatform? = null,
        page: Int = 1
    ): Flow<List<GameItem>> = flow {
        val cleanQuery = query.trim()
        val combinedResults = LinkedHashMap<String, GameItem>()

        // 1. Pada page 1, sertakan local featured catalog terlebih dahulu agar respon instan
        if (page == 1) {
            val localMatches = MockGames.extendedCloudRepository.filter { item ->
                val platformMatch = platform == null || item.platform == platform
                val queryMatch = cleanQuery.isEmpty() ||
                    item.title.contains(cleanQuery, ignoreCase = true) ||
                    item.platform.displayName.contains(cleanQuery, ignoreCase = true)
                platformMatch && queryMatch
            }
            localMatches.forEach { combinedResults[it.id] = it }
        }

        // 2. Query multi-source providers dengan automatic failover
        val matchingSources = sources.filter { it.supportsPlatform(platform) }
            .sortedBy { it.priority }

        for (source in matchingSources) {
            try {
                val sourceItems = source.search(
                    query = cleanQuery,
                    platform = platform,
                    page = page,
                    limit = 12
                )
                if (sourceItems.isNotEmpty()) {
                    sourceItems.forEach { item ->
                        combinedResults[item.id] = item
                    }
                }
            } catch (_: Exception) {
                // Failover otomatis: lanjut ke provider mirror berikutnya tanpa crash
            }
        }

        // v1.1.9 — TIDAK ADA fallback sintetis lagi. Versi lama membuat
        // entri game palsu ("Failover Web Mirror") dengan URL archive.org
        // yang ditebak-nebak (`.../download/$slug/$slug.iso`) — persis
        // yang dilarang aturan katalog: kalau game-nya tidak ada, jangan
        // dibuat ada. Bila semua mirror offline, hasil pencarian kosong
        // dan UI menampilkan empty state, bukan game bohongan.

        // 4. Prioritaskan format modern hemat memori (.chd / .cso) di atas format mentah (.iso)
        val sortedResults = combinedResults.values.sortedWith(
            compareByDescending<GameItem> {
                CompressionAdvisor.getFormatPriorityScore(it.downloadUrl.ifEmpty { it.title })
            }.thenByDescending { !it.isWebResult }
        )

        emit(sortedResults)
    }.flowOn(Dispatchers.IO)

    /**
     * Mengambil daftar game terpopuler per platform dari multi-source + featured catalog.
     */
    suspend fun getPopularGames(
        platform: GamePlatform? = null,
        page: Int = 1
    ): List<GameItem> = withContext(Dispatchers.IO) {
        val combinedResults = LinkedHashMap<String, GameItem>()

        if (page == 1) {
            val featured = if (platform == null) MockGames.catalog
            else MockGames.catalog.filter { it.platform == platform }
            featured.forEach { combinedResults[it.id] = it }
        }

        val matchingSources = sources.filter { it.supportsPlatform(platform) }
            .sortedBy { it.priority }

        for (source in matchingSources) {
            try {
                val popular = source.getPopular(platform = platform, page = page, limit = 8)
                popular.forEach { combinedResults[it.id] = it }
            } catch (_: Exception) {
                // Failover silently
            }
        }

        combinedResults.values.toList()
    }
}
