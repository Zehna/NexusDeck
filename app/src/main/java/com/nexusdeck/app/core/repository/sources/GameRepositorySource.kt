package com.nexusdeck.app.core.repository.sources

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform

interface GameRepositorySource {
    val sourceName: String
    val priority: Int

    fun supportsPlatform(platform: GamePlatform?): Boolean

    suspend fun search(
        query: String,
        platform: GamePlatform?,
        page: Int,
        limit: Int = 15
    ): List<GameItem>

    suspend fun getPopular(
        platform: GamePlatform?,
        page: Int,
        limit: Int = 15
    ): List<GameItem>
}
