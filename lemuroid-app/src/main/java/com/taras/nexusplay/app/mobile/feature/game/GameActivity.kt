package com.taras.nexusplay.app.mobile.feature.game

import androidx.compose.runtime.Composable
import com.taras.nexusplay.app.mobile.feature.gamemenu.GameMenuActivity
import com.taras.nexusplay.app.shared.game.BaseGameActivity
import com.taras.nexusplay.app.shared.game.BaseGameScreenViewModel

class GameActivity : BaseGameActivity() {
    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}
