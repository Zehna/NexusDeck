package com.taras.nexusplay.lib.core.assetsmanager

import android.content.SharedPreferences
import com.taras.nexusplay.lib.core.CoreUpdater
import com.taras.nexusplay.lib.library.CoreID
import com.taras.nexusplay.lib.storage.DirectoriesManager

class NoAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {}

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
    }
}
