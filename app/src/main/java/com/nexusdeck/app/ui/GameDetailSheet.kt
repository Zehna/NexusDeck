package com.nexusdeck.app.ui

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.core.launcher.LauncherResult
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.optimizer.AutoTuningEngine
import com.nexusdeck.app.ui.components.GameCoverArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailBottomSheet(
    game: GameItem,
    hardwareSpec: HardwareSpec,
    compatibilityTier: CompatibilityTier,
    gameState: GameState,
    onInstallClick: () -> Unit,
    onPlayInAppClick: (romPath: String, engineConfig: EngineConfig, autoTuneActive: Boolean) -> Unit,
    onLaunchExternalClick: (romPath: String, engineConfig: EngineConfig, autoTuneActive: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var autoTuneActive by remember(game.id) { mutableStateOf(true) }

    val optimalConfig = remember(hardwareSpec, game) {
        AutoTuningEngine.generateOptimalConfig(hardwareSpec, game)
    }
    val stockConfig = remember(game) {
        AutoTuningEngine.generateStockConfig(game)
    }
    val activeEngineConfig = if (autoTuneActive) optimalConfig else stockConfig

    val platformAccent = when (game.platform) {
        GamePlatform.PC -> Color(0xFF00E5FF)
        GamePlatform.PS2 -> Color(0xFF3B82F6)
        GamePlatform.PS3 -> Color(0xFFA855F7)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111827),
        contentColor = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_B,
                            KeyEvent.KEYCODE_ESCAPE,
                            KeyEvent.KEYCODE_BACK -> {
                                onDismiss()
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_A -> {
                                when (gameState) {
                                    is GameState.NotInstalled -> onInstallClick()
                                    is GameState.Installed -> onPlayInAppClick(
                                        gameState.romPath,
                                        activeEngineConfig,
                                        autoTuneActive
                                    )
                                    else -> Unit
                                }
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_Y -> {
                                autoTuneActive = !autoTuneActive
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Visual Artwork Card dengan Coil + Offline Fallback
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    GameCoverArt(
                        coverUrl = game.coverUrl,
                        title = game.title,
                        platform = game.platform,
                        modifier = Modifier.matchParentSize()
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC090D16),
                            border = BorderStroke(1.dp, platformAccent.copy(alpha = 0.8f)),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = game.platform.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = platformAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xCC090D16),
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = game.targetEmulator.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Judul Game, Metadata, dan Status Kompatibilitas
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color(0xFFF8FAFC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Emulator: ${game.targetEmulator.displayName} • Min RAM: ${game.minRamGb} GB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup (Tombol B)",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CompatibilityBadge(tier = compatibilityTier)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kartu Panel Konsol: ⚡ Auto-Tuning Profile
            AutoTuningProfilePanel(
                gamePlatform = game.platform,
                gpuModel = hardwareSpec.gpuModel,
                engineConfig = activeEngineConfig,
                autoTuneActive = autoTuneActive,
                onAutoTuneToggle = { autoTuneActive = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Area Tombol Aksi Dinamis berdasarkan GameState
            GameActionSection(
                gameState = gameState,
                emulatorName = game.targetEmulator.displayName,
                onInstallClick = onInstallClick,
                onPlayInAppClick = { romPath ->
                    onPlayInAppClick(romPath, activeEngineConfig, autoTuneActive)
                },
                onLaunchExternalClick = { romPath ->
                    onLaunchExternalClick(romPath, activeEngineConfig, autoTuneActive)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoTuningProfilePanel(
    gamePlatform: GamePlatform,
    gpuModel: String,
    engineConfig: EngineConfig,
    autoTuneActive: Boolean,
    onAutoTuneToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B1322),
        border = BorderStroke(
            width = 1.dp,
            color = if (autoTuneActive) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF23304A)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚡ Auto-Tuning Profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFFF8FAFC)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Auto-Tune Active",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (autoTuneActive) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                    )
                    Switch(
                        checked = autoTuneActive,
                        onCheckedChange = onAutoTuneToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF061018),
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TuningParamChip(
                    label = "Driver GPU",
                    value = engineConfig.driverName,
                    highlighted = autoTuneActive
                )
                TuningParamChip(
                    label = "Resolusi",
                    value = engineConfig.targetResolution,
                    highlighted = autoTuneActive
                )
                TuningParamChip(
                    label = "Render",
                    value = engineConfig.rendererBackend,
                    highlighted = autoTuneActive
                )
                if (gamePlatform == GamePlatform.PC) {
                    TuningParamChip(
                        label = "DXVK",
                        value = engineConfig.dxvkVersion,
                        highlighted = autoTuneActive
                    )
                }
                TuningParamChip(
                    label = "CPU JIT",
                    value = engineConfig.cpuDynarecPreset,
                    highlighted = autoTuneActive
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (autoTuneActive) {
                    "✓ Dioptimasi otomatis untuk $gpuModel agar stabil dan hemat suhu."
                } else {
                    "• Auto-Tune dinonaktifkan. Menggunakan profil standar bawaan emulator."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (autoTuneActive) Color(0xFF22C55E) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun TuningParamChip(
    label: String,
    value: String,
    highlighted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF162238),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) Color(0xFF2A3F66) else Color(0xFF1E293B)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (highlighted) Color(0xFFE2E8F0) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun GameActionSection(
    gameState: GameState,
    emulatorName: String,
    onInstallClick: () -> Unit,
    onPlayInAppClick: (romPath: String) -> Unit,
    onLaunchExternalClick: (romPath: String) -> Unit
) {
    when (gameState) {
        is GameState.NotInstalled -> {
            Button(
                onClick = onInstallClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF061018)
                )
            ) {
                Text(
                    text = "Download & Install  [A / ✕]",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                )
            }
        }

        is GameState.Downloading -> {
            val percent = (gameState.progress * 100).toInt().coerceIn(0, 100)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mengunduh...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color(0xFF00E5FF)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { gameState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF0F172A)
                    )
                }
            }
        }

        is GameState.Extracting -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mengekstrak file game...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFFDE047)
                        )
                        Text(
                            text = "Optimizing",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFFDE047)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFFFACC15),
                        trackColor = Color(0xFF0F172A)
                    )
                }
            }
        }

        is GameState.Installed -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ROM Path: ${gameState.romPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onPlayInAppClick(gameState.romPath) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color(0xFF052E16)
                        )
                    ) {
                        Text(
                            text = "▶ PLAY GAME  [A / ✕]",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = { onLaunchExternalClick(gameState.romPath) },
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = "Buka $emulatorName",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmulatorNotFoundDialog(
    missingEmulator: LauncherResult.EmulatorNotFound,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131C2E),
        titleContentColor = Color(0xFFF8FAFC),
        textContentColor = Color(0xFFCBD5E1),
        title = {
            Text(
                text = "Emulator Belum Terpasang",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                text = "Emulator ${missingEmulator.emulatorName} (${missingEmulator.packageName}) belum terpasang di HP kamu. Buka halaman unduh resmi?"
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(missingEmulator.downloadUrl)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "Tidak dapat membuka browser untuk ${missingEmulator.downloadUrl}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF061018)
                )
            ) {
                Text(
                    text = "Buka Halaman Unduh",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Batal",
                    color = Color(0xFF94A3B8)
                )
            }
        }
    )
}
