package com.taras.nexusplay.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.taras.nexusplay.touchinput.radial.LocalLemuroidPadTheme
import com.taras.nexusplay.touchinput.radial.ui.LemuroidControlBackground
import com.taras.nexusplay.touchinput.radial.ui.LemuroidCrossForeground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlCross
import gg.padkit.ids.Id

context(PadKitScope)
@Composable
fun LemuroidControlCross(
    modifier: Modifier = Modifier,
    id: Id.DiscreteDirection,
    allowDiagonals: Boolean = true,
    background: @Composable () -> Unit = {
        LemuroidControlBackground()
    },
    foreground: @Composable (State<Offset>) -> Unit = {
        LemuroidCrossForeground(
            allowDiagonals = allowDiagonals,
            directionState = it,
        )
    },
) {
    val theme = LocalLemuroidPadTheme.current
    ControlCross(
        modifier = modifier.padding(theme.padding),
        id = id,
        allowDiagonals = allowDiagonals,
        background = background,
        foreground = foreground,
    )
}
