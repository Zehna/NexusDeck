package com.nexusdeck.app.data

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator

object MockGames {
    val catalog: List<GameItem> = listOf(
        GameItem(
            id = "pc_fallout_nv",
            title = "Fallout: New Vegas",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=800&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative
        ),
        GameItem(
            id = "pc_gta_iv",
            title = "Grand Theft Auto IV",
            platform = GamePlatform.PC,
            coverUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=800&q=80",
            minRamGb = 8,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.GameNative
        ),
        GameItem(
            id = "ps2_persona_4",
            title = "Persona 4",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=800&q=80",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9", "helio_g99"),
            targetEmulator = TargetEmulator.ARMSX2
        ),
        GameItem(
            id = "ps2_gow_2",
            title = "God of War II",
            platform = GamePlatform.PS2,
            coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=800&q=80",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX2
        ),
        GameItem(
            id = "ps3_persona_5",
            title = "Persona 5",
            platform = GamePlatform.PS3,
            coverUrl = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=800&q=80",
            minRamGb = 8,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_9"),
            targetEmulator = TargetEmulator.ARMSX3
        ),
        GameItem(
            id = "ps3_tlou",
            title = "The Last of Us",
            platform = GamePlatform.PS3,
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=800&q=80",
            minRamGb = 12,
            recommendedSocKeywords = listOf("snapdragon_8_gen_3", "snapdragon_8_elite"),
            targetEmulator = TargetEmulator.ARMSX3
        )
    )
}
