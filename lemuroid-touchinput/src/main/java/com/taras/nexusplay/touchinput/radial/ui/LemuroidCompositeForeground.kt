package com.taras.nexusplay.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.taras.nexusplay.touchinput.radial.LocalLemuroidPadTheme

@Composable
fun LemuroidCompositeForeground(pressed: State<Boolean>) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        fillColor = theme.compositeFill(pressed.value),
        shadowColor = theme.level2Shadow,
        shadowWidth = theme.level2ShadowWidth,
    )
}
