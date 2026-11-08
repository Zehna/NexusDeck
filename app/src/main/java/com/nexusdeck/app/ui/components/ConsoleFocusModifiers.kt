package com.nexusdeck.app.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.consoleCardFocus(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSelectAction: () -> Unit,
    glowColor: Color = Color(0xFF00E5FF)
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ConsoleCardFocusScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        label = "ConsoleCardGlowAlpha"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (glowAlpha > 0.01f) {
                // Efek outer neon glow berlapis di sekeliling kartu yang sedang fokus
                drawRoundRect(
                    color = glowColor.copy(alpha = 0.22f * glowAlpha),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(width = 8.dp.toPx())
                )
                drawRoundRect(
                    color = glowColor.copy(alpha = 0.85f * glowAlpha),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
        }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        onSelectAction()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .focusable()
}

@Composable
fun ConsoleControllerLegendBar(
    isDetailSheetOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0D1524),
        border = BorderStroke(1.dp, Color(0xFF1E2D47))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GAMEPAD READY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color(0xFF00E5FF)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControllerHintItem(
                    buttonLabel = "D-Pad / Stick",
                    actionLabel = "Navigasi Kartu",
                    accentColor = Color(0xFF38BDF8)
                )
                ControllerHintItem(
                    buttonLabel = "A / ✕",
                    actionLabel = if (isDetailSheetOpen) "Install / Play" else "Buka Detail",
                    accentColor = Color(0xFF22C55E)
                )
                ControllerHintItem(
                    buttonLabel = "Y / △",
                    actionLabel = "Ganti Tab Platform",
                    accentColor = Color(0xFFFACC15)
                )
                ControllerHintItem(
                    buttonLabel = "B / ○",
                    actionLabel = "Tutup Detail",
                    accentColor = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
private fun ControllerHintItem(
    buttonLabel: String,
    actionLabel: String,
    accentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
        ) {
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = accentColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFCBD5E1)
        )
    }
}
