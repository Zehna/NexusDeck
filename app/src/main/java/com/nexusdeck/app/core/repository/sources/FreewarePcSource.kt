package com.nexusdeck.app.core.repository.sources

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class FreewarePcSource : GameRepositorySource {

    override val sourceName: String = "Freeware & Open-Source PC Vault"
    override val priority: Int = 3

    override fun supportsPlatform(platform: GamePlatform?): Boolean {
        return platform == null || platform == GamePlatform.PC
    }

    private val freewareCatalog: List<GameItem> = listOf(
        GameItem(
            id = "pc_openra",
            title = "OpenRA (Red Alert & Tiberian Dawn Engine)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "86 MB",
            downloadUrl = "https://github.com/OpenRA/OpenRA/releases/download/release-20231010/OpenRA-Red-Alert-x86_64.AppImage",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "pc_freedoom",
            title = "Freedoom: Phase 1 & 2 (Complete Open Game)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "42 MB",
            downloadUrl = "https://github.com/freedoom/freedoom/releases/download/v0.12.1/freedoom-0.12.1.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "pc_ur_quan_masters",
            title = "The Ur-Quan Masters (Star Control II Open Source)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "148 MB",
            downloadUrl = "https://sourceforge.net/projects/urquanmastersp/files/latest/download",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "pc_openttd",
            title = "OpenTTD (Transport Tycoon Deluxe Open Engine)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "38 MB",
            downloadUrl = "https://github.com/OpenTTD/OpenTTD/releases/download/14.1/openttd-14.1-windows-win64.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "pc_endless_sky",
            title = "Endless Sky (Space Exploration RPG)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "235 MB",
            downloadUrl = "https://github.com/endless-sky/endless-sky/releases/download/v0.10.6/EndlessSky-win64.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        )
    )

    override suspend fun search(
        query: String,
        platform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> = withContext(Dispatchers.Default) {
        if (platform != null && platform != GamePlatform.PC) {
            return@withContext emptyList()
        }

        val clean = query.trim().lowercase(Locale.US)
        val matches = freewareCatalog.filter { item ->
            clean.isEmpty() ||
                item.title.lowercase(Locale.US).contains(clean) ||
                item.id.contains(clean)
        }

        if (matches.isNotEmpty()) {
            matches.take(limit)
        } else if (clean.contains("pc") || clean.contains("freeware") || clean.contains("doom")) {
            freewareCatalog.take(limit)
        } else {
            emptyList()
        }
    }

    override suspend fun getPopular(
        platform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> = withContext(Dispatchers.Default) {
        if (platform == null || platform == GamePlatform.PC) {
            freewareCatalog.take(limit)
        } else {
            emptyList()
        }
    }
}
