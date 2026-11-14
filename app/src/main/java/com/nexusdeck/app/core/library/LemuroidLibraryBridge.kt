package com.nexusdeck.app.core.library

import android.content.Context
import android.content.Intent
import com.taras.nexusplay.app.shared.game.ExternalGameLauncherActivity
import com.taras.nexusplay.app.shared.library.LibraryIndexScheduler
import com.taras.nexusplay.lib.preferences.SharedPreferencesHelper

object LemuroidLibraryBridge {
    const val EXTRA_GAME_FILE_URI = "EXTRA_GAME_FILE_URI"

    fun ensureFolderRegistered(context: Context, folderStore: LibraryFolderStore): Boolean {
        val preferences = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val preferenceKey = context.getString(com.taras.nexusplay.lib.R.string.pref_key_extenral_folder)
        if (preferences.getString(preferenceKey, null).isNullOrBlank()) {
            val folderUri = folderStore.allFolders().keys.firstOrNull() ?: return false
            preferences.edit().putString(preferenceKey, folderUri).apply()
            LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        }
        return true
    }

    fun launchGame(context: Context, fileUri: String): Boolean {
        return runCatching {
            context.startActivity(
                Intent(context, ExternalGameLauncherActivity::class.java)
                    .putExtra(EXTRA_GAME_FILE_URI, fileUri)
            )
        }.isSuccess
    }
}
