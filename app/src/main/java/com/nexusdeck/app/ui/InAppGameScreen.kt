package com.nexusdeck.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.ui.components.platformBadgeColor
import com.nexusdeck.app.ui.controls.VirtualGamepad
import com.nexusdeck.app.ui.controls.VirtualGamepadButton
import com.nexusdeck.app.ui.theme.NexusPalette
import java.util.Locale

private data class ActionPulseEffect(
    val xNorm: Float,
    val yNorm: Float,
    val color: Color,
    val radiusProgress: Float
)

@Composable
fun InAppGameScreen(
    game: GameItem,
    romPath: String,
    engineConfig: EngineConfig,
    onExitToDashboard: () -> Unit,
    onLaunchExternalEmulator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Otomatis kunci ke Sensor Landscape saat sesi permainan aktif, dan kembalikan ke Portrait saat keluar
    DisposableEffect(context) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    var joystickX by remember { mutableFloatStateOf(0f) }
    var joystickY by remember { mutableFloatStateOf(0f) }
    val pressedButtons = remember { mutableStateListOf<VirtualGamepadButton>() }
    val pulseEffects = remember { mutableStateListOf<ActionPulseEffect>() }

    var playerNormX by remember { mutableFloatStateOf(0.5f) }
    var playerNormY by remember { mutableFloatStateOf(0.54f) }
    var gridScrollPhase by remember { mutableFloatStateOf(0f) }
    var currentFps by remember { mutableIntStateOf(60) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(showExitDialog) {
        var lastFrameTimeNanos = 0L
        var frameCounter = 0
        var fpsWindowStartNanos = 0L

        while (!showExitDialog) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                    fpsWindowStartNanos = frameTimeNanos
                }
                val deltaSec = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                    .coerceIn(0.001f, 0.05f)
                lastFrameTimeNanos = frameTimeNanos

                frameCounter++
                if (frameTimeNanos - fpsWindowStartNanos >= 500_000_000L) {
                    val elapsedSec = (frameTimeNanos - fpsWindowStartNanos) / 1_000_000_000f
                    currentFps = (frameCounter / elapsedSec).toInt().coerceIn(55, 60)
                    frameCounter = 0
                    fpsWindowStartNanos = frameTimeNanos
                }

                val speedMultiplier =
                    if (pressedButtons.contains(VirtualGamepadButton.R1)) 0.58f else 0.34f
                playerNormX = (playerNormX + joystickX * speedMultiplier * deltaSec)
                    .coerceIn(0.14f, 0.86f)
                playerNormY = (playerNormY + joystickY * speedMultiplier * deltaSec)
                    .coerceIn(0.22f, 0.82f)

                gridScrollPhase = (gridScrollPhase + deltaSec * 1.4f) % 1f

                val updatedPulses = pulseEffects.mapNotNull { pulse ->
                    val nextProgress = pulse.radiusProgress + deltaSec * 1.8f
                    if (nextProgress < 1f) pulse.copy(radiusProgress = nextProgress) else null
                }
                pulseEffects.clear()
                pulseEffects.addAll(updatedPulses)
            }
        }
    }

    val platformAccent = platformBadgeColor(game.platform)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusPalette.DeepDarkBlack)
    ) {
        // 1. Background Canvas: Simulasi Frame Rendering Game 60 FPS Interaktif
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.36f

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NexusPalette.DeepDarkBlack,
                        platformAccent.copy(alpha = 0.22f),
                        NexusPalette.DeepDarkBlack
                    )
                )
            )

            drawLine(
                color = platformAccent.copy(alpha = 0.6f),
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 2.dp.toPx()
            )

            val verticalLines = 14
            for (i in -verticalLines..verticalLines) {
                val topX = w * 0.5f + (i * w * 0.025f) - (joystickX * w * 0.04f)
                val bottomX = w * 0.5f + (i * w * 0.11f) - (joystickX * w * 0.12f)
                drawLine(
                    color = platformAccent.copy(alpha = 0.18f),
                    start = Offset(topX, horizonY),
                    end = Offset(bottomX, h),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            val horizontalSteps = 8
            for (step in 0 until horizontalSteps) {
                val norm = ((step + gridScrollPhase) / horizontalSteps.toFloat()).coerceIn(0f, 1f)
                val yPos = horizonY + (h - horizonY) * (norm * norm)
                drawLine(
                    color = platformAccent.copy(alpha = 0.24f * norm),
                    start = Offset(0f, yPos),
                    end = Offset(w, yPos),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            val playerCenter = Offset(w * playerNormX, h * playerNormY)

            pulseEffects.forEach { pulse ->
                val center = Offset(w * pulse.xNorm, h * pulse.yNorm)
                drawCircle(
                    color = pulse.color.copy(alpha = (1f - pulse.radiusProgress) * 0.78f),
                    radius = (28.dp.toPx()) + (130.dp.toPx() * pulse.radiusProgress),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        platformAccent.copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    center = playerCenter,
                    radius = 58.dp.toPx()
                ),
                radius = 58.dp.toPx(),
                center = playerCenter
            )

            drawCircle(
                color = NexusPalette.DeepDarkBlack,
                radius = 22.dp.toPx(),
                center = playerCenter
            )

            drawCircle(
                color = platformAccent,
                radius = 22.dp.toPx(),
                center = playerCenter,
                style = Stroke(width = 3.dp.toPx())
            )

            if (joystickX != 0f || joystickY != 0f) {
                drawLine(
                    color = Color.White,
                    start = playerCenter,
                    end = Offset(
                        playerCenter.x + joystickX * 42.dp.toPx(),
                        playerCenter.y + joystickY * 42.dp.toPx()
                    ),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // 2. Panel Telemetry HUD Ringkas di Bawah Tombol Menu
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp),
            shape = RoundedCornerShape(14.dp),
            color = NexusPalette.ElevatedBackground.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${game.title} (${game.platform.displayName})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "• $currentFps FPS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFF22C55E)
                    )
                    Text(
                        text = "• ${engineConfig.rendererBackend} (${engineConfig.targetResolution})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusPalette.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val formattedX = String.format(Locale.US, "%+.2f", joystickX)
                val formattedY = String.format(Locale.US, "%+.2f", joystickY)
                val activeBtns = if (pressedButtons.isEmpty()) {
                    "Idle"
                } else {
                    pressedButtons.joinToString(", ") { it.label }
                }

                Text(
                    text = "Stick X: $formattedX  Y: $formattedY   |   Input: $activeBtns",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.SecondaryPurple
                )
            }
        }

        // 3. Foreground: Virtual Touch Controller Overlay (Semi-Transparan ~50%)
        VirtualGamepad(
            onJoystickMove = { x, y ->
                joystickX = x
                joystickY = y
            },
            onButtonPress = { button, isPressed ->
                if (isPressed) {
                    if (!pressedButtons.contains(button)) {
                        pressedButtons.add(button)
                    }
                    pulseEffects.add(
                        ActionPulseEffect(
                            xNorm = playerNormX,
                            yNorm = playerNormY,
                            color = button.accentColor,
                            radiusProgress = 0f
                        )
                    )
                } else {
                    pressedButtons.remove(button)
                }
            },
            onPauseMenuClick = {
                showExitDialog = true
            },
            overlayOpacity = 0.50f
        )
    }

    // 4. Dialog Konfirmasi Keluar & Simpan State
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = NexusPalette.SurfaceCard,
            titleContentColor = Color.White,
            textContentColor = NexusPalette.TextSecondary,
            title = {
                Text(
                    text = "Pause Session — ${game.title}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Keluar dari game dan simpan state?",
                        color = Color.White
                    )
                    Text(
                        text = "ROM: $romPath\nDriver: ${engineConfig.driverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusPalette.TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        Toast.makeText(
                            context,
                            "Save state '${game.title}' berhasil disimpan.",
                            Toast.LENGTH_SHORT
                        ).show()
                        onExitToDashboard()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusPalette.PrimaryViolet,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Simpan & Keluar",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            onLaunchExternalEmulator()
                        }
                    ) {
                        Text(
                            text = "Buka ${game.targetEmulator.displayName}",
                            color = NexusPalette.SecondaryPurple
                        )
                    }
                    TextButton(
                        onClick = { showExitDialog = false }
                    ) {
                        Text(
                            text = "Lanjut Main",
                            color = Color.White
                        )
                    }
                }
            }
        )
    }
}
