package com.nexusdeck.app.ui

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.NexusDeckTheme
import com.nexusdeck.app.core.launcher.GameLauncher
import com.nexusdeck.app.core.launcher.LauncherResult
import com.nexusdeck.app.core.manager.GameInstallManager
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.profiler.HardwareProfiler
import com.nexusdeck.app.data.MockGames
import com.nexusdeck.app.ui.components.ConsoleControllerLegendBar
import com.nexusdeck.app.ui.components.ConsoleFilterTabs
import com.nexusdeck.app.ui.components.GameCoverArt
import com.nexusdeck.app.ui.components.consoleCardFocus
import com.nexusdeck.app.ui.components.nextPlatformTab

private data class ActiveGameSession(
    val game: GameItem,
    val romPath: String,
    val engineConfig: EngineConfig,
    val autoTuneActive: Boolean
)

@Composable
fun DashboardScreen(
    hardwareSpec: HardwareSpec,
    games: List<GameItem> = MockGames.catalog,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val installManager = remember(context) {
        GameInstallManager(context, coroutineScope)
    }
    val gameStates by installManager.gameStates.collectAsState()

    var selectedPlatform by remember { mutableStateOf<GamePlatform?>(null) }
    var selectedGameForSheet by remember { mutableStateOf<GameItem?>(null) }
    var activeGameSession by remember { mutableStateOf<ActiveGameSession?>(null) }
    var missingEmulatorResult by remember {
        mutableStateOf<LauncherResult.EmulatorNotFound?>(null)
    }

    // Jika sesi In-App Game Player sedang aktif, tampilkan layar fullscreen InAppGameScreen
    val currentSession = activeGameSession
    if (currentSession != null) {
        InAppGameScreen(
            game = currentSession.game,
            romPath = currentSession.romPath,
            engineConfig = currentSession.engineConfig,
            onExitToDashboard = {
                activeGameSession = null
            },
            onLaunchExternalEmulator = {
                val result = GameLauncher.launchGame(
                    context = context,
                    game = currentSession.game,
                    romPath = currentSession.romPath,
                    engineConfig = currentSession.engineConfig,
                    autoTuneEnabled = currentSession.autoTuneActive
                )
                when (result) {
                    is LauncherResult.Launched -> {
                        activeGameSession = null
                    }
                    is LauncherResult.EmulatorNotFound -> {
                        activeGameSession = null
                        missingEmulatorResult = result
                    }
                    is LauncherResult.LaunchError -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        return
    }

    val filteredGames = remember(games, selectedPlatform) {
        if (selectedPlatform == null) games
        else games.filter { it.platform == selectedPlatform }
    }

    var focusedIndex by remember(selectedPlatform) { mutableIntStateOf(0) }
    val focusRequesters = remember(filteredGames) {
        List(filteredGames.size) { FocusRequester() }
    }

    // Berikan fokus awal ke kartu pertama saat daftar tab berganti
    LaunchedEffect(filteredGames) {
        if (filteredGames.isNotEmpty()) {
            val safeIndex = focusedIndex.coerceIn(0, filteredGames.lastIndex)
            focusedIndex = safeIndex
            try {
                focusRequesters.getOrNull(safeIndex)?.requestFocus()
            } catch (_: Exception) {
                // Abaikan jika komposisi belum siap menerima fokus
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1726),
                        Color(0xFF090D16)
                    )
                )
            )
            // Global Gamepad Key Handler untuk D-Pad, A (Cross), B (Circle), dan Y (Triangle)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.nativeKeyEvent.keyCode) {
                    // Tombol Y / Triangle -> Ganti Tab Kategori Platform
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (selectedGameForSheet == null) {
                            selectedPlatform = nextPlatformTab(selectedPlatform)
                            true
                        } else {
                            false
                        }
                    }

                    // Tombol B / Circle -> Tutup Game Detail BottomSheet atau Dialog
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        when {
                            missingEmulatorResult != null -> {
                                missingEmulatorResult = null
                                true
                            }
                            selectedGameForSheet != null -> {
                                selectedGameForSheet = null
                                true
                            }
                            else -> false
                        }
                    }

                    // Tombol A / Cross -> Buka Detail Game yang sedang disorot
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (selectedGameForSheet == null && filteredGames.isNotEmpty()) {
                            val targetGame = filteredGames.getOrNull(
                                focusedIndex.coerceIn(0, filteredGames.lastIndex)
                            )
                            if (targetGame != null) {
                                selectedGameForSheet = targetGame
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }

                    // D-Pad Left / Right -> Pindah Fokus Antar Kartu Game secara Eksplisit
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (selectedGameForSheet == null && filteredGames.isNotEmpty()) {
                            val nextIndex = (focusedIndex + 1).coerceAtMost(filteredGames.lastIndex)
                            focusedIndex = nextIndex
                            try {
                                focusRequesters.getOrNull(nextIndex)?.requestFocus()
                            } catch (_: Exception) {
                            }
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (selectedGameForSheet == null && filteredGames.isNotEmpty()) {
                            val prevIndex = (focusedIndex - 1).coerceAtLeast(0)
                            focusedIndex = prevIndex
                            try {
                                focusRequesters.getOrNull(prevIndex)?.requestFocus()
                            } catch (_: Exception) {
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        // Header Atas: Brand & Chip Informasi Perangkat
        DashboardHeader(hardwareSpec = hardwareSpec)

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Tab Platform Bergaya Konsol dengan Counter Jumlah Game
        ConsoleFilterTabs(
            games = games,
            selectedPlatform = selectedPlatform,
            onSelectPlatform = { selectedPlatform = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Body: Grid Kartu Game dengan D-Pad Focus & Coil Box Art
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(
                items = filteredGames,
                key = { _, game -> game.id }
            ) { index, game ->
                val compatibilityTier = remember(hardwareSpec, game) {
                    HardwareProfiler.evaluateCompatibility(hardwareSpec, game)
                }
                val gameState = gameStates[game.id] ?: GameState.NotInstalled
                val isCardFocused = focusedIndex == index
                val focusRequester = focusRequesters.getOrElse(index) { FocusRequester() }

                GameCatalogCard(
                    game = game,
                    compatibilityTier = compatibilityTier,
                    gameState = gameState,
                    isFocused = isCardFocused,
                    focusRequester = focusRequester,
                    onFocusChanged = { hasFocus ->
                        if (hasFocus) {
                            focusedIndex = index
                        }
                    },
                    onClick = {
                        focusedIndex = index
                        selectedGameForSheet = game
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer Bar Petunjuk Kontrol Stik / Gamepad
        ConsoleControllerLegendBar(
            isDetailSheetOpen = selectedGameForSheet != null
        )
    }

    // BottomSheet Detail Game, Auto-Tuning Profile, & Instalasi / Launch
    val activeGame = selectedGameForSheet
    if (activeGame != null) {
        val activeCompatibility = remember(hardwareSpec, activeGame) {
            HardwareProfiler.evaluateCompatibility(hardwareSpec, activeGame)
        }
        val activeGameState = gameStates[activeGame.id] ?: GameState.NotInstalled

        GameDetailBottomSheet(
            game = activeGame,
            hardwareSpec = hardwareSpec,
            compatibilityTier = activeCompatibility,
            gameState = activeGameState,
            onInstallClick = {
                installManager.installGame(activeGame)
            },
            onPlayInAppClick = { romPath, engineConfig, autoTuneActive ->
                selectedGameForSheet = null
                activeGameSession = ActiveGameSession(
                    game = activeGame,
                    romPath = romPath,
                    engineConfig = engineConfig,
                    autoTuneActive = autoTuneActive
                )
            },
            onLaunchExternalClick = { romPath, engineConfig, autoTuneActive ->
                val result = GameLauncher.launchGame(
                    context = context,
                    game = activeGame,
                    romPath = romPath,
                    engineConfig = engineConfig,
                    autoTuneEnabled = autoTuneActive
                )
                when (result) {
                    is LauncherResult.Launched -> {
                        selectedGameForSheet = null
                    }
                    is LauncherResult.EmulatorNotFound -> {
                        missingEmulatorResult = result
                    }
                    is LauncherResult.LaunchError -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = {
                selectedGameForSheet = null
            }
        )
    }

    // Alert Dialog jika Emulator Target Belum Terpasang
    val missingEmulator = missingEmulatorResult
    if (missingEmulator != null) {
        EmulatorNotFoundDialog(
            missingEmulator = missingEmulator,
            onDismiss = { missingEmulatorResult = null }
        )
    }
}

@Composable
private fun DashboardHeader(hardwareSpec: HardwareSpec) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "NEXUSDECK",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Universal Gaming Orchestrator",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Chip Informasi Perangkat Terdeteksi
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF162032),
            border = BorderStroke(1.dp, Color(0xFF2A3B56))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device: ${hardwareSpec.socModel} | ${hardwareSpec.gpuModel} | ${hardwareSpec.totalRamGb} GB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF090D16)
                ) {
                    Text(
                        text = hardwareSpec.vulkanVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GameCatalogCard(
    game: GameItem,
    compatibilityTier: CompatibilityTier,
    gameState: GameState = GameState.NotInstalled,
    isFocused: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformAccent = when (game.platform) {
        GamePlatform.PC -> Color(0xFF00E5FF)
        GamePlatform.PS2 -> Color(0xFF3B82F6)
        GamePlatform.PS3 -> Color(0xFFA855F7)
    }

    val isInstalled = gameState is GameState.Installed
    val borderColor = when {
        isFocused -> Color(0xFF00E5FF)
        isInstalled -> Color(0xFF22C55E).copy(alpha = 0.65f)
        else -> Color(0xFF23304A)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .consoleCardFocus(
                isFocused = isFocused,
                focusRequester = focusRequester,
                onFocusChanged = onFocusChanged,
                onSelectAction = onClick,
                glowColor = Color(0xFF00E5FF)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF17243B) else Color(0xFF131C2E)
        ),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 6.dp
        )
    ) {
        Column {
            // Cover Banner Header dengan Coil AsyncImage & Fallback Offline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
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
                    // Tag Platform (PC / PS2 / PS3)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC090D16),
                        border = BorderStroke(1.dp, platformAccent.copy(alpha = 0.8f)),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = game.platform.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = platformAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Tag Target Emulator (GameNative / ARMSX2 / ARMSX3)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC090D16),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = game.targetEmulator.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Status Pill (Installed / Downloading / Extracting) di Kiri Bawah Cover
                    InstallStatusPill(
                        gameState = gameState,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )

                    // Play / Detail Icon Aksentuasi
                    Surface(
                        shape = CircleShape,
                        color = if (isInstalled) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Open ${game.title}",
                                tint = Color(0xFF061018),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Detail Game & Smart Compatibility Badge
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Min RAM: ${game.minRamGb} GB • ${game.targetEmulator.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                CompatibilityBadge(tier = compatibilityTier)
            }
        }
    }
}

@Composable
private fun InstallStatusPill(
    gameState: GameState,
    modifier: Modifier = Modifier
) {
    val (label, textColor, bgColor) = when (gameState) {
        is GameState.NotInstalled -> Triple("Belum Diinstal", Color(0xFFCBD5E1), Color(0xCC090D16))
        is GameState.Downloading -> {
            val pct = (gameState.progress * 100).toInt().coerceIn(0, 100)
            Triple("Mengunduh $pct%", Color(0xFF00E5FF), Color(0xDD090D16))
        }
        is GameState.Extracting -> Triple("Mengekstrak...", Color(0xFFFACC15), Color(0xDD090D16))
        is GameState.Installed -> Triple("READY TO PLAY", Color(0xFF22C55E), Color(0xDD052E16))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CompatibilityBadge(
    tier: CompatibilityTier,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = tier.containerColor,
        border = BorderStroke(1.dp, tier.badgeColor.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tier.badgeColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tier.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = tier.badgeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 480
)
@Composable
fun DashboardScreenPreview() {
    NexusDeckTheme {
        DashboardScreen(
            hardwareSpec = HardwareSpec(
                socModel = "Snapdragon 8 Gen 2",
                gpuModel = "Adreno 740",
                totalRamGb = 8,
                vulkanVersion = "Vulkan 1.3"
            )
        )
    }
}
