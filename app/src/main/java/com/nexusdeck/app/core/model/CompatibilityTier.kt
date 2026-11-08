package com.nexusdeck.app.core.model

import androidx.compose.ui.graphics.Color

enum class CompatibilityTier(
    val label: String,
    val badgeColor: Color,
    val containerColor: Color
) {
    VERIFIED_60FPS(
        label = "Lancar 60 FPS",
        badgeColor = Color(0xFF22C55E),
        containerColor = Color(0xFF052E16)
    ),
    PLAYABLE_TWEAKED(
        label = "Bisa Main ~30-45 FPS",
        badgeColor = Color(0xFFEAB308),
        containerColor = Color(0xFF422006)
    ),
    UNSUPPORTED(
        label = "Spek Belum Memadai",
        badgeColor = Color(0xFFEF4444),
        containerColor = Color(0xFF450A0A)
    )
}
