package com.taras.nexusplay.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.taras.nexusplay.touchinput.controller.R
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlButton
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlCross
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlFaceButtons
import com.taras.nexusplay.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonL
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonR
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.taras.nexusplay.touchinput.radial.settings.TouchControllerSettingsManager
import com.taras.nexusplay.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.MelonDSLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { LemuroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            SecondaryButtonL()
            SecondaryButtonSelect(position = 2)
            SecondaryButtonMenuPlaceholder(settings)
            LemuroidControlButton(
                modifier = Modifier.radialPosition(-120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                icon = R.drawable.button_mic,
            )
            LemuroidControlButton(
                modifier = Modifier.radialPosition(-60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                icon = R.drawable.button_close_screen,
            )
        },
    )
}

@Composable
fun PadKitScope.MelonDSRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlFaceButtons(
                ids =
                    persistentListOf(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                    ),
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { LemuroidButtonForeground(pressed = it, label = "A") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { LemuroidButtonForeground(pressed = it, label = "B") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { LemuroidButtonForeground(pressed = it, label = "Y") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { LemuroidButtonForeground(pressed = it, label = "X") },
                    ),
            )
        },
        secondaryDials = {
            SecondaryButtonR()
            SecondaryButtonStart(position = 2)
            SecondaryButtonMenu(settings)
            Box(modifier = Modifier.fillMaxSize().radialPosition(-120f))
        },
    )
}
