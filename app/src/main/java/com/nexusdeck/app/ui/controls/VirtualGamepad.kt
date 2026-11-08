package com.nexusdeck.app.ui.controls

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VirtualGamepadButton(val label: String, val accentColor: Color) {
    A("A", Color(0xFF22C55E)),
    B("B", Color(0xFFEF4444)),
    X("X", Color(0xFF38BDF8)),
    Y("Y", Color(0xFFFACC15)),
    L1("L1", Color(0xFF00E5FF)),
    R1("R1", Color(0xFF00E5FF))
}

@Composable
fun VirtualGamepad(
    onJoystickMove: (x: Float, y: Float) -> Unit,
    onButtonPress: (button: VirtualGamepadButton, isPressed: Boolean) -> Unit,
    onPauseMenuClick: () -> Unit,
    overlayOpacity: Float = 0.52f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        // Baris Atas: Shoulder L1 (Kiri Atas), Tombol Pause/Menu (Tengah Atas), Shoulder R1 (Kanan Atas)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoulderTriggerButton(
                button = VirtualGamepadButton.L1,
                baseAlpha = overlayOpacity,
                onPressChanged = { pressed -> onButtonPress(VirtualGamepadButton.L1, pressed) }
            )

            PauseMenuButton(
                baseAlpha = overlayOpacity + 0.15f,
                onClick = onPauseMenuClick
            )

            ShoulderTriggerButton(
                button = VirtualGamepadButton.R1,
                baseAlpha = overlayOpacity,
                onPressChanged = { pressed -> onButtonPress(VirtualGamepadButton.R1, pressed) }
            )
        }

        // Kiri Bawah: Virtual Analog Stick (-1.0f s/d 1.0f)
        VirtualAnalogStick(
            baseAlpha = overlayOpacity,
            onJoystickMove = onJoystickMove,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp)
        )

        // Kanan Bawah: Empat Action Buttons (A, B, X, Y) dalam Diamond Layout
        ActionButtonDiamond(
            baseAlpha = overlayOpacity,
            onButtonPress = onButtonPress,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 10.dp)
        )
    }
}

@Composable
fun VirtualAnalogStick(
    baseAlpha: Float,
    onJoystickMove: (x: Float, y: Float) -> Unit,
    outerSize: Dp = 148.dp,
    knobSize: Dp = 62.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val maxRadiusPx = with(density) { ((outerSize - knobSize) / 2f).toPx() }

    var knobOffsetX by remember { mutableFloatStateOf(0f) }
    var knobOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.90f else baseAlpha,
        label = "AnalogStickAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outerSize)
            .alpha(animatedAlpha)
            .pointerInput(maxRadiusPx) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = {
                        isDragging = false
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onJoystickMove(0f, 0f)
                    },
                    onDragCancel = {
                        isDragging = false
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onJoystickMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val rawX = knobOffsetX + dragAmount.x
                        val rawY = knobOffsetY + dragAmount.y
                        val distance = hypot(rawX, rawY)

                        if (distance <= maxRadiusPx) {
                            knobOffsetX = rawX
                            knobOffsetY = rawY
                        } else {
                            val angle = atan2(rawY, rawX)
                            knobOffsetX = cos(angle) * maxRadiusPx
                            knobOffsetY = sin(angle) * maxRadiusPx
                        }

                        val normalizedX = (knobOffsetX / maxRadiusPx).coerceIn(-1f, 1f)
                        val normalizedY = (knobOffsetY / maxRadiusPx).coerceIn(-1f, 1f)
                        onJoystickMove(normalizedX, normalizedY)
                    }
                )
            }
    ) {
        // Cincin Dasar Analog & Garis Sumbu
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x550F172A),
                        Color(0xAA090D16)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            drawCircle(
                color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF475569),
                radius = outerRadius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Garis arah halus di dalam cincin
            drawCircle(
                color = Color(0x4438BDF8),
                radius = outerRadius * 0.55f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Garis konektor dari titik pusat ke posisi thumbstick
            if (isDragging) {
                drawLine(
                    color = Color(0xAA00E5FF),
                    start = center,
                    end = Offset(center.x + knobOffsetX, center.y + knobOffsetY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Thumbstick Knob yang mengikuti jari
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = knobOffsetX.roundToInt(),
                        y = knobOffsetY.roundToInt()
                    )
                }
                .size(knobSize),
            shape = CircleShape,
            color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF1E293B),
            border = BorderStroke(
                width = 2.dp,
                color = if (isDragging) Color(0xFFFFFFFF) else Color(0xFF00E5FF)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "L3",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = if (isDragging) Color(0xFF061018) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun ActionButtonDiamond(
    baseAlpha: Float,
    onButtonPress: (button: VirtualGamepadButton, isPressed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = 54.dp
    val containerSize = 164.dp

    Box(
        modifier = modifier.size(containerSize)
    ) {
        // Y (Atas)
        RoundActionButton(
            button = VirtualGamepadButton.Y,
            buttonSize = buttonSize,
            baseAlpha = baseAlpha,
            onPressChanged = { onButtonPress(VirtualGamepadButton.Y, it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // X (Kiri)
        RoundActionButton(
            button = VirtualGamepadButton.X,
            buttonSize = buttonSize,
            baseAlpha = baseAlpha,
            onPressChanged = { onButtonPress(VirtualGamepadButton.X, it) },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // B (Kanan)
        RoundActionButton(
            button = VirtualGamepadButton.B,
            buttonSize = buttonSize,
            baseAlpha = baseAlpha,
            onPressChanged = { onButtonPress(VirtualGamepadButton.B, it) },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // A (Bawah)
        RoundActionButton(
            button = VirtualGamepadButton.A,
            buttonSize = buttonSize,
            baseAlpha = baseAlpha,
            onPressChanged = { onButtonPress(VirtualGamepadButton.A, it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RoundActionButton(
    button: VirtualGamepadButton,
    buttonSize: Dp,
    baseAlpha: Float,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ActionButtonScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else baseAlpha,
        label = "ActionButtonAlpha"
    )

    Surface(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha)
            .pointerInput(button) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            },
        shape = CircleShape,
        color = if (isPressed) button.accentColor else Color(0xFF0F172A),
        border = BorderStroke(
            width = if (isPressed) 2.5.dp else 2.dp,
            color = button.accentColor
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = if (isPressed) Color(0xFF061018) else button.accentColor
            )
        }
    }
}

@Composable
private fun ShoulderTriggerButton(
    button: VirtualGamepadButton,
    baseAlpha: Float,
    onPressChanged: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else baseAlpha,
        label = "ShoulderAlpha"
    )

    Surface(
        modifier = Modifier
            .width(96.dp)
            .height(42.dp)
            .alpha(alpha)
            .pointerInput(button) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isPressed) button.accentColor else Color(0xFF111827),
        border = BorderStroke(1.5.dp, button.accentColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = if (isPressed) Color(0xFF061018) else button.accentColor
            )
        }
    }
}

@Composable
private fun PauseMenuButton(
    baseAlpha: Float,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .alpha(if (isPressed) 0.95f else baseAlpha)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            onClick()
                        }
                    }
                )
            },
        shape = RoundedCornerShape(50),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF475569))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "⏸",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "MENU / EXIT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFFF8FAFC)
            )
        }
    }
}
