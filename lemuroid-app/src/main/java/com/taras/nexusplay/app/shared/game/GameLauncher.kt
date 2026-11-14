package com.taras.nexusplay.app.shared.game

import android.app.Activity
import com.taras.nexusplay.R
import com.taras.nexusplay.app.shared.main.GameLaunchTaskHandler
import com.taras.nexusplay.common.displayToast
import com.taras.nexusplay.lib.core.CoresSelection
import com.taras.nexusplay.lib.library.GameSystem
import com.taras.nexusplay.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        GlobalScope.launch {
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
        }

        return true
    }
}
