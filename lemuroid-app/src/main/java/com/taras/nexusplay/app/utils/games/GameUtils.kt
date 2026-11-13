package com.taras.nexusplay.app.utils.games

import android.content.Context
import com.taras.nexusplay.lib.library.GameSystem
import com.taras.nexusplay.lib.library.db.entity.Game

class GameUtils {
    companion object {
        fun getGameSubtitle(
            context: Context,
            game: Game,
        ): String {
            val systemName = getSystemNameForGame(context, game)
            val developerName =
                if (game.developer?.isNotBlank() == true) {
                    "- ${game.developer}"
                } else {
                    ""
                }
            return "$systemName $developerName"
        }

        private fun getSystemNameForGame(
            context: Context,
            game: Game,
        ): String {
            val systemTitleResource = GameSystem.findById(game.systemId).shortTitleResId
            return context.getString(systemTitleResource)
        }
    }
}
