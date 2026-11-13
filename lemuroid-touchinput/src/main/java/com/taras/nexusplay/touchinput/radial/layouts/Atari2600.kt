package com.taras.nexusplay.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlButton
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlCross
import com.taras.nexusplay.touchinput.radial.controls.LemuroidControlFaceButtons
import com.taras.nexusplay.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.taras.nexusplay.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.taras.nexusplay.touchinput.radial.settings.TouchControllerSettingsManager
import com.taras.nexusplay.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.Atari2600Left(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { LemuroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            LemuroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
                label = "DIFF.A",
            )
            LemuroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                label = "DIFF.B",
            )
            SecondaryButtonMenuPlaceholder(settings)
        },
    )
}

@Composable
fun PadKitScope.Atari2600Right(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlFaceButtons(
                ids = persistentListOf(Id.Key(KeyEvent.KEYCODE_BUTTON_B)),
                includeComposite = false,
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { LemuroidButtonForeground(pressed = it) },
                    ),
            )
        },
        secondaryDials = {
            LemuroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
                label = "RESET",
            )
            LemuroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
                label = "SELECT",
            )
            SecondaryButtonMenu(settings)
            Box(modifier = Modifier.fillMaxSize().radialPosition(-120f))
        },
    )
}
