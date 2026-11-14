package com.taras.nexusplay.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.taras.nexusplay.touchinput.radial.LocalLemuroidPadTheme
import com.taras.nexusplay.touchinput.radial.ui.LemuroidButtonForeground
import com.taras.nexusplay.touchinput.radial.ui.LemuroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun LemuroidControlButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String? = null,
    icon: Int? = null,
) {
    val theme = LocalLemuroidPadTheme.current
    ControlButton(
        modifier = modifier.padding(theme.padding),
        id = id,
        foreground = { LemuroidButtonForeground(pressed = it, icon = icon, label = label) },
        background = { LemuroidControlBackground() },
    )
}
