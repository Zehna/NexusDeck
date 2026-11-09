package com.nexusdeck.app.ui

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import com.nexusdeck.app.core.achievements.RetroAchievementsService
import com.nexusdeck.app.core.achievements.RetroGameTrophySummary
import com.nexusdeck.app.core.launcher.LauncherResult
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.optimizer.AutoTuningEngine
import com.nexusdeck.app.core.optimizer.CompressionAdvice
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import com.nexusdeck.app.ui.components.EngineInstallerDialog
import com.nexusdeck.app.ui.components.GameCoverArt
import com.nexusdeck.app.ui.components.platformBadgeColor
import com.nexusdeck.app.ui.theme.NexusPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailBottomSheet(
    game: GameItem,
    hardwareSpec: HardwareSpec,
    compatibilityTier: CompatibilityTier,
    gameState: GameState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onInstallClick: () -> Unit,
    onPlayInAppClick: (romPath: String, engineConfig: EngineConfig, autoTuneActive: Boolean) -> Unit,
    onLaunchExternalClick: (romPath: String, engineConfig: EngineConfig, autoTuneActive: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var autoTuneActive by remember(game.id) { mutableStateOf(true) }

    val optimalConfig = remember(hardwareSpec, game) {
        AutoTuningEngine.generateOptimalConfig(hardwareSpec, game)
    }
    val stockConfig = remember(game) {
        AutoTuningEngine.generateStockConfig(game)
    }
    val activeEngineConfig = if (autoTuneActive) optimalConfig else stockConfig
    val platformAccent = platformBadgeColor(game.platform)

    val compressionAdvice = remember(game) {
        CompressionAdvisor.analyzeGameFormat(game.platform, game.fileSizeLabel, game.platform.defaultExtension)
    }
    val trophySummary = remember(game) {
        RetroAchievementsService(context).getCuratedOrSyntheticTrophies(game.title, game.platform)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusPalette.ElevatedBackground,
        contentColor = NexusPalette.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Poster + Info Utama ala FullRoid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Poster Vertikal Box Art
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    GameCoverArt(
                        coverUrl = game.coverUrl,
                        title = game.title,
                        platform = game.platform,
                        modifier = Modifier.matchParentSize()
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC0B0B0E),
                        border = BorderStroke(1.dp, platformAccent.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = game.platform.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = platformAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Detail Judul, Ukuran File Asli, Core Emulator, & Favorit
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color(0xFFEC4899) else NexusPalette.TextSecondary
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = NexusPalette.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pills: Ukuran File Asli, Core Emulator, & Min RAM
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NexusPalette.SurfaceCard,
                            border = BorderStroke(1.dp, NexusPalette.CardBorder)
                        ) {
                            Text(
                                text = "📦 ${game.fileSizeLabel}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NexusPalette.SecondaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NexusPalette.SurfaceCard,
                            border = BorderStroke(1.dp, NexusPalette.CardBorder)
                        ) {
                            Text(
                                text = game.targetEmulator.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusPalette.TextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NexusPalette.SurfaceCard,
                            border = BorderStroke(1.dp, NexusPalette.CardBorder)
                        ) {
                            Text(
                                text = "${game.minRamGb}GB RAM",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusPalette.TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badge Jalur Eksekusi (In-App Playable vs Native Engine Handoff)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                            Color(0xFF22C55E).copy(alpha = 0.15f)
                        else
                            NexusPalette.PrimaryViolet.copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                                Color(0xFF22C55E).copy(alpha = 0.7f)
                            else
                                NexusPalette.SecondaryPurple.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚡ ${game.executionMode.label}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                                    Color(0xFF22C55E)
                                else
                                    NexusPalette.SecondaryPurple
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${game.sourceRepositoryName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusPalette.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CompatibilityBadge(tier = compatibilityTier)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Kartu Panel Konsol: ⚡ Auto-Tuning Profile
            AutoTuningProfilePanel(
                gamePlatform = game.platform,
                gpuModel = hardwareSpec.gpuModel,
                engineConfig = activeEngineConfig,
                autoTuneActive = autoTuneActive,
                onAutoTuneToggle = { autoTuneActive = it }
            )

            // Kartu 💡 Rekomendasi Format & Kompresi Cerdas (.chd / .cso)
            if (compressionAdvice.shouldRecommend) {
                Spacer(modifier = Modifier.height(14.dp))
                StorageCompressionAdvisorCard(advice = compressionAdvice)
            }

            // Section 🏆 RetroAchievements Trofi & Poin
            Spacer(modifier = Modifier.height(14.dp))
            RetroAchievementsTrophyPanel(summary = trophySummary)

            Spacer(modifier = Modifier.height(18.dp))

            // Area Tombol Aksi Dinamis (One-Click Direct Web Download / Play)
            GameActionSection(
                game = game,
                gameState = gameState,
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
        color = NexusPalette.SurfaceCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (autoTuneActive) NexusPalette.PrimaryViolet.copy(alpha = 0.65f) else NexusPalette.CardBorder
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
                Text(
                    text = "⚡ Auto-Tuning Profile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (autoTuneActive) "Active" else "Off",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (autoTuneActive) NexusPalette.SecondaryPurple else NexusPalette.TextSecondary
                    )
                    Switch(
                        checked = autoTuneActive,
                        onCheckedChange = onAutoTuneToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NexusPalette.PrimaryViolet,
                            uncheckedThumbColor = NexusPalette.TextSecondary,
                            uncheckedTrackColor = NexusPalette.ElevatedBackground
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

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (autoTuneActive) {
                    "✓ Dioptimasi otomatis untuk $gpuModel agar stabil dan hemat suhu."
                } else {
                    "• Auto-Tune dinonaktifkan. Menggunakan profil standar bawaan emulator."
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (autoTuneActive) Color(0xFF22C55E) else NexusPalette.TextSecondary
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
        color = NexusPalette.ElevatedBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) NexusPalette.PrimaryViolet.copy(alpha = 0.4f) else NexusPalette.CardBorder
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = NexusPalette.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (highlighted) Color.White else NexusPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun StorageCompressionAdvisorCard(
    advice: CompressionAdvice,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141320),
        border = BorderStroke(1.dp, NexusPalette.NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "💡", fontSize = 16.sp)
                Text(
                    text = advice.adviceHeadline,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NexusPalette.NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = advice.detailedMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NexusPalette.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NexusPalette.NeonCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NexusPalette.NeonCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Hemat ~${advice.estimatedSavingsPercent}% (${advice.formattedSavings})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NexusPalette.NeonCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Rekomendasi format: ${advice.recommendedFormat}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextMuted
                )
            }
        }
    }
}

@Composable
private fun RetroAchievementsTrophyPanel(
    summary: RetroGameTrophySummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NexusPalette.SurfaceCard,
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🏆", fontSize = 16.sp)
                    Text(
                        text = "RetroAchievements",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${summary.totalAchievements} Trofi • ${summary.totalPoints} Poin",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preview 3 Trofi Teratas
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.topAchievements.forEach { trophy ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF13121A),
                        border = BorderStroke(1.dp, Color(0xFF262438)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (trophy.isUnlocked) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        else Color(0xFF262438)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (trophy.isUnlocked) "⭐" else "🔒",
                                    fontSize = 14.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trophy.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                if (trophy.description.isNotBlank()) {
                                    Text(
                                        text = trophy.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusPalette.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF232135)
                            ) {
                                Text(
                                    text = "+${trophy.points} PTS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameActionSection(
    game: GameItem,
    gameState: GameState,
    onInstallClick: () -> Unit,
    onPlayInAppClick: (romPath: String) -> Unit,
    onLaunchExternalClick: (romPath: String) -> Unit
) {
    val platformFolder = "/games/${game.platform.name.lowercase()}/"

    when (gameState) {
        is GameState.NotInstalled -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Direktori: $platformFolder • Ukuran Paket: ${game.fileSizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextSecondary
                )

                // Tombol Gradasi Ungu: [Download Langsung ke Nexus]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brush = NexusPalette.VioletGradient)
                        .clickable(onClick = onInstallClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ Download & Play (${game.fileSizeLabel})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }

        is GameState.Downloading -> {
            val percent = (gameState.progress * 100).toInt().coerceIn(0, 100)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = NexusPalette.SurfaceCard,
                border = BorderStroke(1.dp, NexusPalette.CardBorder)
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
                            text = "Mengunduh ke $platformFolder...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = NexusPalette.SecondaryPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { gameState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = NexusPalette.PrimaryViolet,
                        trackColor = NexusPalette.DeepDarkBlack
                    )
                }
            }
        }

        is GameState.Extracting -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = NexusPalette.SurfaceCard,
                border = BorderStroke(1.dp, NexusPalette.CardBorder)
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
                            text = "Menyimpan & mengoptimasi di $platformFolder...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusPalette.SecondaryPurple
                        )
                        Text(
                            text = "Verifying",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusPalette.SecondaryPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = NexusPalette.SecondaryPurple,
                        trackColor = NexusPalette.DeepDarkBlack
                    )
                }
            }
        }

        is GameState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "✕ ${gameState.message}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFF87171)
                )
                Text(
                    text = "Target Direktori: $platformFolder • Ukuran Paket: ${game.fileSizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextSecondary
                )
                Text(
                    text = "Bawa ROM sendiri: letakkan file ${game.id}.${game.platform.defaultExtension} di $platformFolder lalu tap Coba Unduh Lagi — file lokal yang valid langsung dipakai tanpa mengunduh.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brush = NexusPalette.VioletGradient)
                        .clickable(onClick = onInstallClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻ Coba Unduh Lagi (${game.fileSizeLabel})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }

        is GameState.Installed -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "✓ Tersimpan di Library: ${gameState.romPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22C55E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayInAppClick(gameState.romPath) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color(0xFF052E16)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLAY GAME",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = { onLaunchExternalClick(gameState.romPath) },
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NexusPalette.CardBorder)
                    ) {
                        Text(
                            text = game.targetEmulator.displayName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusPalette.TextPrimary
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
    val engine = missingEmulator.engineInfo
    if (engine != null) {
        EngineInstallerDialog(
            engineInfo = engine,
            gameTitle = missingEmulator.emulatorName,
            onDismiss = onDismiss
        )
        return
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusPalette.SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = NexusPalette.TextSecondary,
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
                    containerColor = NexusPalette.PrimaryViolet,
                    contentColor = Color.White
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
                    color = NexusPalette.TextSecondary
                )
            }
        }
    )
}
