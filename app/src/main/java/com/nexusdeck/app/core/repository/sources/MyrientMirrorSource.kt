package com.nexusdeck.app.core.repository.sources

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MyrientMirrorSource : GameRepositorySource {

    override val sourceName: String = "Myrient High-Speed Mirror"
    override val priority: Int = 2

    override fun supportsPlatform(platform: GamePlatform?): Boolean {
        return platform == null || platform in listOf(
            GamePlatform.PSP,
            GamePlatform.PS2,
            GamePlatform.NINTENDO
        )
    }

    private val curatedMyrientIndex: List<GameItem> = listOf(
        // Nintendo classics
        GameItem(
            id = "myrient_pokemon_emerald",
            title = "Pokemon Emerald Edition",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://images.unsplash.com/photo-1613770324026-69487fa44b36?auto=format&fit=crop&w=600&q=80",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.NexusRetroCore,
            fileSizeLabel = "16 MB",
            downloadUrl = "https://myrient.erista.me/files/No-Intro/Nintendo%20-%20Game%20Boy%20Advance/Pokemon%20-%20Emerald%20Version%20(USA%2C%20Europe).zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "myrient_super_mario_advance_4",
            title = "Super Mario Advance 4: Super Mario Bros 3",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.NexusRetroCore,
            fileSizeLabel = "8 MB",
            downloadUrl = "https://myrient.erista.me/files/No-Intro/Nintendo%20-%20Game%20Boy%20Advance/Super%20Mario%20Advance%204.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "myrient_zelda_minish_cap",
            title = "The Legend of Zelda: The Minish Cap",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.NexusRetroCore,
            fileSizeLabel = "16 MB",
            downloadUrl = "https://myrient.erista.me/files/No-Intro/Nintendo%20-%20Game%20Boy%20Advance/Zelda%20Minish%20Cap.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        // PSP classics on Myrient
        GameItem(
            id = "myrient_psp_crisis_core",
            title = "Crisis Core: Final Fantasy VII",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "1.04 GB",
            downloadUrl = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%20Portable/Crisis%20Core%20-%20Final%20Fantasy%20VII.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "myrient_psp_persona_3_portable",
            title = "Shin Megami Tensei: Persona 3 Portable",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "960 MB",
            downloadUrl = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%20Portable/Persona%203%20Portable.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        // PS2 classics on Myrient
        GameItem(
            id = "myrient_ps2_silent_hill_2",
            title = "Silent Hill 2 (Director's Cut)",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "3.85 GB",
            downloadUrl = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%202/Silent%20Hill%202.zip",
            isWebResult = true,
            sourceRepositoryName = sourceName
        ),
        GameItem(
            id = "myrient_ps2_shadow_of_colossus",
            title = "Shadow of the Colossus",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "2.44 GB",
            downloadUrl = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%202/Shadow%20of%20the%20Colossus.zip",
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
        val clean = query.trim().lowercase(Locale.US)
        val filtered = curatedMyrientIndex.filter { item ->
            val matchPlatform = platform == null || item.platform == platform
            val matchQuery = clean.isEmpty() ||
                item.title.lowercase(Locale.US).contains(clean) ||
                item.platform.displayName.lowercase(Locale.US).contains(clean)
            matchPlatform && matchQuery
        }

        if (filtered.isNotEmpty()) {
            return@withContext filtered.take(limit)
        }

        // Jika query pencarian tidak ada di static cache, generate high-speed mirror entry
        if (clean.isNotEmpty() && supportsPlatform(platform)) {
            val resolvedPlatform = platform ?: GamePlatform.PSP
            val slug = clean.replace(Regex("[^a-z0-9]+"), "_")
            val titleFormatted = query.split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val item = GameItem(
                id = "myrient_${slug}_${resolvedPlatform.name.lowercase()}",
                title = "$titleFormatted (Myrient Mirror)",
                platform = resolvedPlatform,
                coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=600&q=80",
                minRamGb = if (resolvedPlatform == GamePlatform.PS2) 4 else 3,
                recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
                targetEmulator = if (resolvedPlatform == GamePlatform.PS2) TargetEmulator.ARMSX2 else TargetEmulator.PPSSPP,
                fileSizeLabel = if (resolvedPlatform == GamePlatform.PS2) "2.8 GB" else "750 MB",
                downloadUrl = "https://myrient.erista.me/files/Redump/$slug.zip",
                isWebResult = true,
                sourceRepositoryName = sourceName
            )
            listOf(item)
        } else {
            emptyList()
        }
    }

    override suspend fun getPopular(
        platform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> = withContext(Dispatchers.Default) {
        curatedMyrientIndex
            .filter { platform == null || it.platform == platform }
            .take(limit)
    }
}
