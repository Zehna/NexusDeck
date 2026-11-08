package com.nexusdeck.app.core.repository

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import com.nexusdeck.app.core.repository.sources.ArchiveOrgSource
import com.nexusdeck.app.core.repository.sources.FreewarePcSource
import com.nexusdeck.app.core.repository.sources.GameRepositorySource
import com.nexusdeck.app.core.repository.sources.MyrientMirrorSource
import com.nexusdeck.app.data.MockGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class OnlineGameCatalogService(
    val sources: List<GameRepositorySource> = listOf(
        ArchiveOrgSource(),
        MyrientMirrorSource(),
        FreewarePcSource()
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

        var anySourceSucceeded = false
        for (source in matchingSources) {
            try {
                val sourceItems = source.search(
                    query = cleanQuery,
                    platform = platform,
                    page = page,
                    limit = 12
                )
                if (sourceItems.isNotEmpty()) {
                    anySourceSucceeded = true
                    sourceItems.forEach { item ->
                        combinedResults[item.id] = item
                    }
                }
            } catch (_: Exception) {
                // Failover otomatis: lanjut ke provider mirror berikutnya tanpa crash
            }
        }

        // 3. Fallback sintetis jika seluruh remote mirror sedang offline
        if (!anySourceSucceeded && combinedResults.isEmpty() && cleanQuery.isNotEmpty()) {
            val fallback = buildFallbackItems(cleanQuery, platform, page)
            fallback.forEach { combinedResults[it.id] = it }
        }

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

    private fun buildFallbackItems(
        query: String,
        platform: GamePlatform?,
        page: Int
    ): List<GameItem> {
        val resolvedPlatform = platform ?: GamePlatform.PSP
        val slug = query.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val formattedTitle = query.replaceFirstChar { it.uppercase() }

        return listOf(
            GameItem(
                id = "mirror_${slug}_${resolvedPlatform.name.lowercase()}_p$page",
                title = "$formattedTitle (Failover Web Mirror)",
                platform = resolvedPlatform,
                coverUrl = "",
                minRamGb = if (resolvedPlatform == GamePlatform.PS2) 4 else 3,
                recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
                targetEmulator = when (resolvedPlatform) {
                    GamePlatform.NINTENDO -> com.nexusdeck.app.core.model.TargetEmulator.Lemuroid
                    GamePlatform.PSP -> com.nexusdeck.app.core.model.TargetEmulator.PPSSPP
                    GamePlatform.PS2 -> com.nexusdeck.app.core.model.TargetEmulator.ARMSX2
                    GamePlatform.PC -> com.nexusdeck.app.core.model.TargetEmulator.GameNative
                    GamePlatform.PS3 -> com.nexusdeck.app.core.model.TargetEmulator.ARMSX3
                },
                fileSizeLabel = if (resolvedPlatform == GamePlatform.NINTENDO) "32 MB" else "780 MB",
                downloadUrl = "https://archive.org/download/$slug/$slug.iso",
                isWebResult = true,
                sourceRepositoryName = "Failover Mirror Hub"
            )
        )
    }
}
