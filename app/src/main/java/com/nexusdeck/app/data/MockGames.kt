package com.nexusdeck.app.data

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator

object MockGames {

    /**
     * Featured Catalog (14 Game Populer Lintas Platform sesuai spesifikasi PSP, PS2, PC, PS3)
     */
    val catalog: List<GameItem> = listOf(
        // ================= PSP =================
        GameItem(
            id = "psp_gow_ghost_of_sparta",
            title = "God of War: Ghost of Sparta",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "1.14 GB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/gow_ghost_of_sparta.iso"
        ),
        GameItem(
            id = "psp_tekken_6",
            title = "Tekken 6",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "815 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/tekken_6_psp.iso"
        ),
        GameItem(
            id = "psp_gta_vcs",
            title = "Grand Theft Auto: Vice City Stories",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "864 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/gta_vice_city_stories.iso"
        ),
        GameItem(
            id = "psp_nfs_mw",
            title = "Need for Speed: Most Wanted",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "492 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/nfs_most_wanted_510.iso"
        ),

        // ================= Nintendo / Retro =================
        GameItem(
            id = "gba_pokemon_emerald",
            title = "Pokemon Emerald Version",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://images.unsplash.com/photo-1613770324026-69487fa44b36?auto=format&fit=crop&w=600&q=80",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.NexusRetroCore,
            fileSizeLabel = "16 MB",
            downloadUrl = "https://archive.org/download/nintendo-gba-collection/pokemon_emerald.gba"
        ),
        GameItem(
            id = "gba_super_mario_adv4",
            title = "Super Mario Advance 4",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.NexusRetroCore,
            fileSizeLabel = "8 MB",
            downloadUrl = "https://archive.org/download/nintendo-gba-collection/super_mario_advance_4.gba"
        ),

        // ================= PS2 =================
        GameItem(
            id = "ps2_resident_evil_4",
            title = "Resident Evil 4",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "3.42 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/resident_evil_4_ps2.iso"
        ),
        GameItem(
            id = "ps2_gow_2",
            title = "God of War II",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=600&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "6.78 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/god_of_war_ii_ps2.iso"
        ),
        GameItem(
            id = "ps2_gta_sa",
            title = "Grand Theft Auto: San Andreas",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "4.19 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/gta_san_andreas_ps2.iso"
        ),
        GameItem(
            id = "ps2_nfs_ug2",
            title = "Need for Speed Underground 2",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1544829099-b9a0c07fad1a?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "2.15 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/nfs_underground_2_ps2.iso"
        ),
        GameItem(
            id = "ps2_dbz_bt3",
            title = "Dragon Ball Z: Budokai Tenkaichi 3",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "2.84 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/dbz_budokai_tenkaichi_3.iso"
        ),

        // ================= PC =================
        GameItem(
            id = "pc_fallout_nv",
            title = "Fallout: New Vegas",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=600&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "5.85 GB",
            downloadUrl = "https://archive.org/download/pc-classic-demos/fallout_new_vegas_setup.zip"
        ),
        GameItem(
            id = "pc_half_life_2",
            title = "Half-Life 2",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "3.90 GB",
            downloadUrl = "https://archive.org/download/pc-classic-demos/half_life_2_pc.zip"
        ),
        GameItem(
            id = "pc_gta_sa",
            title = "Grand Theft Auto: San Andreas (PC)",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1552820728-8b83bb6b773f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "4.68 GB",
            downloadUrl = "https://archive.org/download/pc-classic-demos/gta_san_andreas_pc.zip"
        ),

        // ================= PS3 =================
        GameItem(
            id = "ps3_persona_5",
            title = "Persona 5",
            platform = GamePlatform.PS3,
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80",
            minRamGb = 8,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3,
            fileSizeLabel = "14.6 GB",
            downloadUrl = "https://archive.org/download/ps3-pkg-archive/persona_5_ps3.iso"
        ),
        GameItem(
            id = "ps3_rdr",
            title = "Red Dead Redemption",
            platform = GamePlatform.PS3,
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            minRamGb = 12,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3,
            fileSizeLabel = "7.45 GB",
            downloadUrl = "https://archive.org/download/ps3-pkg-archive/red_dead_redemption_ps3.iso"
        )
    )

    /**
     * Katalog tambahan untuk memperkaya hasil pencarian instan & feed online lintas konsol
     */
    val extendedCloudRepository: List<GameItem> = catalog + listOf(
        GameItem(
            id = "psp_naruto_un_impact",
            title = "Naruto Shippuden: Ultimate Ninja Impact",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "912 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/naruto_ultimate_ninja_impact.iso",
            isWebResult = true
        ),
        GameItem(
            id = "ps2_naruto_un5",
            title = "Naruto Shippuden: Ultimate Ninja 5",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "3.65 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/naruto_ultimate_ninja_5.iso",
            isWebResult = true
        ),
        GameItem(
            id = "psp_dbz_shin_budokai_2",
            title = "Dragon Ball Z: Shin Budokai - Another Road",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "540 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/dbz_shin_budokai_2.iso",
            isWebResult = true
        ),
        GameItem(
            id = "ps2_tekken_5",
            title = "Tekken 5",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "3.88 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/tekken_5_ps2.iso",
            isWebResult = true
        ),
        GameItem(
            id = "ps2_bully",
            title = "Bully: Canis Canem Edit",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "2.95 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/bully_ps2.iso",
            isWebResult = true
        ),
        GameItem(
            id = "psp_gow_chains_of_olympus",
            title = "God of War: Chains of Olympus",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "1.08 GB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/gow_chains_of_olympus.iso",
            isWebResult = true
        ),
        GameItem(
            id = "ps2_black",
            title = "BLACK (Criterion Edition)",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=600&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2,
            fileSizeLabel = "1.72 GB",
            downloadUrl = "https://archive.org/download/ps2-homebrew-demos/black_ps2.iso",
            isWebResult = true
        ),
        GameItem(
            id = "psp_mh_freedom_unite",
            title = "Monster Hunter Freedom Unite",
            platform = GamePlatform.PSP,
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "792 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/monster_hunter_freedom_unite.iso",
            isWebResult = true
        ),
        GameItem(
            id = "pc_gta_iv",
            title = "Grand Theft Auto IV: Complete Edition",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1552820728-8b83bb6b773f?auto=format&fit=crop&w=600&q=80",
            minRamGb = 8,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative,
            fileSizeLabel = "13.4 GB",
            downloadUrl = "https://archive.org/download/pc-classic-demos/gta_iv_complete.zip",
            isWebResult = true
        ),
        GameItem(
            id = "ps3_tlou",
            title = "The Last of Us",
            platform = GamePlatform.PS3,
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
            minRamGb = 12,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3,
            fileSizeLabel = "24.2 GB",
            downloadUrl = "https://archive.org/download/ps3-pkg-archive/the_last_of_us_ps3.iso",
            isWebResult = true
        )
    )
}
