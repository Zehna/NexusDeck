package com.taras.nexusplay.lib.saves.migrators

import com.taras.nexusplay.lib.library.CoreID
import com.taras.nexusplay.lib.library.SystemCoreConfig
import com.taras.nexusplay.lib.library.db.entity.Game
import com.taras.nexusplay.lib.storage.DirectoriesManager

interface SavesMigrator {
    fun loadPreviousSaveForGame(
        game: Game,
        directoriesManager: DirectoriesManager,
    ): ByteArray?
}

fun SystemCoreConfig.getSavesMigrator(): SavesMigrator? {
    return when (this.coreID) {
        CoreID.MELONDS -> MelonDsSavesMigrator
        else -> null
    }
}
