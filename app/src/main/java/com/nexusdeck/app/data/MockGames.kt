package com.nexusdeck.app.data

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator

object MockGames {

    /**
     * WARNING — catalog URLs below are UNVERIFIED placeholders. They were
     * never checked to point at real, downloadable game files (spot checks
     * on 2026-11-09: pokemon_emerald.gba and castlevania_sotn.chd returned
     * HTTP 503 from archive.org; gow_ghost_of_sparta.iso 302-redirected to
     * an unverified host). Do NOT present them as verified downloads and do
     * NOT "fix" them to real commercial ROM links. The app must only load
     * user-owned / homebrew ROMs; RealGameDownloader rejects anything that
     * fails binary/size validation.
     *
     * v1.1.6 — automatic verification (LinkVerifier): every URL below is
     * now checked BEFORE DownloadManager is enqueued (HTTP status,
     * Content-Type, Content-Length, plus a 512-byte probe for HTML). A dead
     * link fails in seconds with its real reason instead of the vague
     * "tidak ada progres" after waiting on a file that will never arrive.
     * Directory URLs (ending in "/") can never pass verification — a direct
     * download link must point at ONE binary file. No new commercial ROM
     * links were added here: a link that fails verification stays failed,
     * and the supported path for these titles is importing a ROM the user
     * already owns (GameInstallManager picks up a validated local file
     * without downloading — see RealGameDownloader.getVerifiedLocalGameFile).
     *
     * Featured Catalog (16 Game Populer Lintas Platform: PS1, PSP, PS2, PC, PS3, Nintendo)
     * Menggunakan URL Box Art Resmi Wikipedia & Steam CDN (Zero Unsplash Stock Photos).
     */
    val catalog: List<GameItem> = listOf(
        // ================= PS1 =================
        GameItem(
            id = "ps1_castlevania_sotn",
            title = "Castlevania: Symphony of the Night",
            platform = GamePlatform.PS1,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/5/5e/Castlevania_SOTN_PAL.jpg",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.DuckStation,
            fileSizeLabel = "445 MB",
            downloadUrl = "https://archive.org/download/ps1-chd-collection/castlevania_sotn.chd"
        ),
        GameItem(
            id = "ps1_tekken_3",
            title = "Tekken 3",
            platform = GamePlatform.PS1,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/a/a4/Tekken_3_Poster.jpg",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.DuckStation,
            fileSizeLabel = "480 MB",
            downloadUrl = "https://archive.org/download/ps1-chd-collection/tekken_3.chd"
        ),

        // ================= PSP =================
        GameItem(
            id = "psp_gow_ghost_of_sparta",
            title = "God of War: Ghost of Sparta",
            platform = GamePlatform.PSP,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/5/54/God_of_War_Ghost_of_Sparta_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/5/53/Tekken_6_PSP_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/b/b0/Vice_city_stories_box.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/8/8e/Need_for_Speed_Most_Wanted_Box_Art.jpg",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "492 MB",
            downloadUrl = "https://archive.org/download/psp-homebrew-library/nfs_most_wanted_510.iso"
        ),

        // ================= Nintendo / Retro (Lemuroid Zero-Setup) =================
        GameItem(
            id = "gba_pokemon_emerald",
            title = "Pokemon Emerald Version",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/f/f4/Pokemon-emerald-boxart.jpg",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.Lemuroid,
            fileSizeLabel = "16 MB",
            downloadUrl = "https://archive.org/download/nintendo-gba-collection/pokemon_emerald.gba"
        ),
        GameItem(
            id = "gba_super_mario_adv4",
            title = "Super Mario Advance 4",
            platform = GamePlatform.NINTENDO,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/1/18/Super_Mario_Advance_4_Box.jpg",
            minRamGb = 2,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = TargetEmulator.Lemuroid,
            fileSizeLabel = "8 MB",
            downloadUrl = "https://archive.org/download/nintendo-gba-collection/super_mario_advance_4.gba"
        ),

        // ================= PS2 =================
        GameItem(
            id = "ps2_resident_evil_4",
            title = "Resident Evil 4",
            platform = GamePlatform.PS2,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/d/df/Resident_Evil_4_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/7/7e/God_of_War_2_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/c/c4/GTASABOX.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/e/ec/Need_for_Speed_Underground_2_box.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/6/62/DBZ_Budokai_Tenkaichi_3.jpg",
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
            coverUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/22380/header.jpg",
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
            coverUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/220/header.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/c/c4/GTASABOX.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/b/b0/Persona_5_cover_art.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/a/a7/Red_Dead_Redemption.jpg",
            minRamGb = 12,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3,
            fileSizeLabel = "7.45 GB",
            downloadUrl = "https://archive.org/download/ps3-pkg-archive/red_dead_redemption_ps3.iso"
        )
    )

    /**
     * Katalog tambahan untuk memperkaya hasil pencarian instan & feed online lintas konsol
     * dengan box art otentik.
     */
    val extendedCloudRepository: List<GameItem> = catalog + listOf(
        GameItem(
            id = "psp_naruto_un_impact",
            title = "Naruto Shippuden: Ultimate Ninja Impact",
            platform = GamePlatform.PSP,
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/f/f7/Naruto_Shippuden_Ultimate_Ninja_Impact.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/6/6c/Naruto_Shippuden_Ultimate_Ninja_5.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/c/cb/Dragon_Ball_Z_Shin_Budokai_Another_Road_NA_Cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/4/4e/Tekken5_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/0/08/Bully_Coverart.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/e/e0/God_of_War_Chains_of_Olympus_cover.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/5/59/Black_box_art.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/0/07/Monster_Hunter_Freedom_Unite.jpg",
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
            coverUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/12210/header.jpg",
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
            coverUrl = "https://upload.wikimedia.org/wikipedia/en/4/46/Video_Game_Cover_-_The_Last_of_Us.jpg",
            minRamGb = 12,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3,
            fileSizeLabel = "24.2 GB",
            downloadUrl = "https://archive.org/download/ps3-pkg-archive/the_last_of_us_ps3.iso",
            isWebResult = true
        )
    )
}
