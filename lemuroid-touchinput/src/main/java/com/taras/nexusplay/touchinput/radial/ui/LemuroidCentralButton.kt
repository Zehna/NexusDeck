package com.taras.nexusplay.touchinput.radial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.taras.nexusplay.touchinput.radial.LocalLemuroidPadTheme

@Composable
fun LemuroidCentralButton(
    pressedState: State<Boolean>,
    label: String? = null,
) {
    val theme = LocalLemuroidPadTheme.current
    Box(modifier = Modifier.padding(theme.padding)) {
        LemuroidControlBackground()
        LemuroidButtonForeground(
            pressed = pressedState,
            label = label,
        )
    }
}
