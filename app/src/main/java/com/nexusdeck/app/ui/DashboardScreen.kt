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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.nexusdeck.app.core.downloader.SniffedDownload
import com.nexusdeck.app.core.launcher.LauncherResult
import com.nexusdeck.app.core.manager.GameInstallManager
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.EngineConfig
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.optimizer.AutoTuningEngine
import com.nexusdeck.app.core.profiler.HardwareProfiler
import com.nexusdeck.app.core.repository.OnlineGameCatalogService
import com.nexusdeck.app.data.MockGames
import com.nexusdeck.app.ui.browser.NexusWebBrowser
import com.nexusdeck.app.ui.components.BottomNavBar
import com.nexusdeck.app.ui.components.CategoryPillFilter
import com.nexusdeck.app.ui.components.ConsoleFilterTabs
import com.nexusdeck.app.ui.components.EngineInstallerDialog
import com.nexusdeck.app.ui.components.GameCoverArt
import com.nexusdeck.app.ui.components.NexusBottomTab
import com.nexusdeck.app.ui.components.consoleCardFocus
import com.nexusdeck.app.ui.components.nextCategoryFilter
import com.nexusdeck.app.ui.components.platformBadgeColor
import com.nexusdeck.app.ui.theme.NexusPalette
import com.nexusdeck.app.core.engine.EngineManager
import kotlinx.coroutines.delay

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
    val onlineCatalogService = remember { OnlineGameCatalogService() }

    val gameStates by installManager.gameStates.collectAsState()
    val savedLibraryGames by installManager.savedLibraryGames.collectAsState()
    val favoriteIds by installManager.favoriteIds.collectAsState()
    val lastPlayedGameId by installManager.lastPlayedGameId.collectAsState()

    var selectedBottomTab by remember { mutableStateOf(NexusBottomTab.HOME) }
    var selectedCategoryPill by remember { mutableStateOf(CategoryPillFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val liveCatalogGames = remember { mutableStateListOf<GameItem>().apply { addAll(games) } }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingOnline by remember { mutableStateOf(false) }
    var isPaginatingMore by remember { mutableStateOf(false) }
    var canLoadMorePages by remember { mutableStateOf(true) }

    var selectedGameForSheet by remember { mutableStateOf<GameItem?>(null) }
    var activeGameSession by remember { mutableStateOf<ActiveGameSession?>(null) }
    var missingEmulatorResult by remember {
        mutableStateOf<LauncherResult.EmulatorNotFound?>(null)
    }
    var detectedDownloadForDialog by remember { mutableStateOf<SniffedDownload?>(null) }

    // Jika sesi In-App Game Player sedang aktif, otomatis masuk ke layar Landscape InAppGameScreen
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

    // Live Search & Filter terhadap OnlineGameCatalogService
    LaunchedEffect(searchQuery, selectedCategoryPill) {
        currentPage = 1
        canLoadMorePages = !selectedCategoryPill.featuredOnly

        if (selectedCategoryPill.featuredOnly && searchQuery.isBlank()) {
            liveCatalogGames.clear()
            liveCatalogGames.addAll(MockGames.catalog)
            isLoadingOnline = false
            return@LaunchedEffect
        }

        if (searchQuery.isNotBlank()) {
            delay(320L) // Debounce saat pengguna mengetik judul
        }

        isLoadingOnline = true
        onlineCatalogService.searchOnlineGames(
            query = searchQuery,
            platform = selectedCategoryPill.platform,
            page = 1
        ).collect { results ->
            liveCatalogGames.clear()
            liveCatalogGames.addAll(results)
            isLoadingOnline = false
        }
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            selectedBottomTab == NexusBottomTab.HOME &&
                canLoadMorePages &&
                !isLoadingOnline &&
                !isPaginatingMore &&
                totalItems >= 6 &&
                lastVisibleIndex >= totalItems - 2
        }
    }

    // Infinite Scroll Pagination dari Web Repository
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && currentPage < 5) {
            isPaginatingMore = true
            val nextPage = currentPage + 1
            onlineCatalogService.searchOnlineGames(
                query = searchQuery,
                platform = selectedCategoryPill.platform,
                page = nextPage
            ).collect { moreGames ->
                val existingIds = liveCatalogGames.map { it.id }.toSet()
                val uniqueNew = moreGames.filter { it.id !in existingIds }
                if (uniqueNew.isEmpty()) {
                    canLoadMorePages = false
                } else {
                    liveCatalogGames.addAll(uniqueNew)
                    currentPage = nextPage
                }
                isPaginatingMore = false
            }
        }
    }

    val installedCount = remember(gameStates) {
        gameStates.values.count { it is GameState.Installed }
    }

    val displayedGames = remember(
        selectedBottomTab,
        selectedCategoryPill,
        liveCatalogGames.toList(),
        savedLibraryGames,
        favoriteIds,
        gameStates
    ) {
        when (selectedBottomTab) {
            NexusBottomTab.HOME -> {
                if (selectedCategoryPill.isFavoritesOnly) {
                    val allKnown = LinkedHashMap<String, GameItem>()
                    savedLibraryGames.forEach { (id, g) -> allKnown[id] = g }
                    liveCatalogGames.forEach { allKnown[it.id] = it }
                    allKnown.values.filter { it.id in favoriteIds }
                } else {
                    liveCatalogGames.toList()
                }
            }
            NexusBottomTab.FAVORITES -> {
                val allKnown = LinkedHashMap<String, GameItem>()
                savedLibraryGames.forEach { (id, g) -> allKnown[id] = g }
                liveCatalogGames.forEach { allKnown[it.id] = it }
                allKnown.values.filter { it.id in favoriteIds }
            }
            NexusBottomTab.LIBRARY -> {
                val allKnown = LinkedHashMap<String, GameItem>()
                savedLibraryGames.forEach { (id, g) -> allKnown[id] = g }
                liveCatalogGames.forEach { allKnown[it.id] = it }
                allKnown.values.filter { game ->
                    val st = gameStates[game.id]
                    st is GameState.Installed || st is GameState.Downloading || st is GameState.Extracting
                }
            }
            NexusBottomTab.BROWSER,
            NexusBottomTab.SETTINGS -> emptyList()
        }
    }

    var focusedIndex by remember(selectedBottomTab, selectedCategoryPill) { mutableIntStateOf(0) }
    val focusRequesters = remember(displayedGames) {
        List(displayedGames.size) { FocusRequester() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusPalette.DeepDarkBlack)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (selectedGameForSheet == null) {
                            selectedCategoryPill = nextCategoryFilter(selectedCategoryPill)
                            true
                        } else {
                            false
                        }
                    }
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
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Header Atas: Brand Logo & Badge Hardware Tipis di Pojok Kanan
            FullRoidHeader(
                hardwareSpec = hardwareSpec,
                onSettingsClick = { selectedBottomTab = NexusBottomTab.SETTINGS }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedBottomTab == NexusBottomTab.SETTINGS) {
                SettingsTabContent(
                    hardwareSpec = hardwareSpec,
                    installedCount = installedCount,
                    modifier = Modifier.weight(1f)
                )
            } else if (selectedBottomTab == NexusBottomTab.BROWSER) {
                NexusWebBrowser(
                    onDownloadIntercepted = { sniffed ->
                        detectedDownloadForDialog = sniffed
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 2. Kolom Pencarian Rounded Pill (Home Tab)
                if (selectedBottomTab == NexusBottomTab.HOME) {
                    FullRoidSearchBar(
                        query = searchQuery,
                        isSearching = isLoadingOnline,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Kategori Pills Horizontal Scroll (All, Catalog, PSP, PlayStation 2, PC Games, PlayStation 3)
                    ConsoleFilterTabs(
                        selectedFilter = selectedCategoryPill,
                        onSelectFilter = { selectedCategoryPill = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    SectionTabBanner(
                        tab = selectedBottomTab,
                        itemCount = displayedGames.size
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 4. Grid Katalog 2 Kolom Vertikal ala Poster Box Art + Infinite Scroll
                if (displayedGames.isEmpty() && !isLoadingOnline) {
                    EmptyTabPlaceholder(
                        tab = selectedBottomTab,
                        onExploreCatalog = {
                            selectedBottomTab = NexusBottomTab.HOME
                            selectedCategoryPill = CategoryPillFilter.ALL
                            searchQuery = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(
                            items = displayedGames,
                            key = { _, game -> game.id }
                        ) { index, game ->
                            val compatibilityTier = remember(hardwareSpec, game) {
                                HardwareProfiler.evaluateCompatibility(hardwareSpec, game)
                            }
                            val gameState = gameStates[game.id] ?: GameState.NotInstalled
                            val isFavorite = game.id in favoriteIds
                            val isFocused = focusedIndex == index
                            val focusRequester = focusRequesters.getOrElse(index) { FocusRequester() }

                            PosterGameCard(
                                game = game,
                                compatibilityTier = compatibilityTier,
                                gameState = gameState,
                                isFavorite = isFavorite,
                                isFocused = isFocused,
                                focusRequester = focusRequester,
                                onFocusChanged = { hasFocus ->
                                    if (hasFocus) focusedIndex = index
                                },
                                onToggleFavorite = {
                                    installManager.toggleFavorite(game)
                                },
                                onClick = {
                                    focusedIndex = index
                                    selectedGameForSheet = game
                                }
                            )
                        }

                        if (isLoadingOnline || isPaginatingMore) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp,
                                            color = NexusPalette.SecondaryPurple
                                        )
                                        Text(
                                            text = "Memuat katalog game online...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NexusPalette.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Modern Bottom Navigation Bar + Floating Center Quick-Resume Button
        BottomNavBar(
            selectedTab = selectedBottomTab,
            installedCount = installedCount,
            onSelectTab = { selectedBottomTab = it },
            onQuickResumeClick = {
                val allKnown = LinkedHashMap<String, GameItem>().apply {
                    savedLibraryGames.forEach { (id, g) -> put(id, g) }
                    liveCatalogGames.forEach { put(it.id, it) }
                }
                val resumeCandidate = lastPlayedGameId?.let { allKnown[it] }
                    ?: allKnown.values.firstOrNull { gameStates[it.id] is GameState.Installed }

                if (resumeCandidate != null) {
                    val state = gameStates[resumeCandidate.id]
                    val romPath = (state as? GameState.Installed)?.romPath
                        ?: "/games/${resumeCandidate.platform.name.lowercase()}/${resumeCandidate.id}.iso"
                    val optimal = AutoTuningEngine.generateOptimalConfig(hardwareSpec, resumeCandidate)
                    installManager.markLastPlayed(resumeCandidate)
                    activeGameSession = ActiveGameSession(
                        game = resumeCandidate,
                        romPath = romPath,
                        engineConfig = optimal,
                        autoTuneActive = true
                    )
                } else {
                    val featuredFirst = liveCatalogGames.firstOrNull() ?: MockGames.catalog.first()
                    selectedGameForSheet = featuredFirst
                    Toast.makeText(
                        context,
                        "Unduh game terlebih dahulu untuk Quick-Resume.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // BottomSheet Detail Game, Direct Web Download, & In-App Play
    val activeGame = selectedGameForSheet
    if (activeGame != null) {
        val activeCompatibility = remember(hardwareSpec, activeGame) {
            HardwareProfiler.evaluateCompatibility(hardwareSpec, activeGame)
        }
        val activeGameState = gameStates[activeGame.id] ?: GameState.NotInstalled
        val isFav = activeGame.id in favoriteIds

        GameDetailBottomSheet(
            game = activeGame,
            hardwareSpec = hardwareSpec,
            compatibilityTier = activeCompatibility,
            gameState = activeGameState,
            isFavorite = isFav,
            onToggleFavorite = {
                installManager.toggleFavorite(activeGame)
            },
            onInstallClick = {
                installManager.installGame(activeGame)
                Toast.makeText(
                    context,
                    "Mengunduh ${activeGame.title} ke /games/${activeGame.platform.name.lowercase()}/...",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPlayInAppClick = { romPath, engineConfig, autoTuneActive ->
                installManager.markLastPlayed(activeGame)
                selectedGameForSheet = null
                activeGameSession = ActiveGameSession(
                    game = activeGame,
                    romPath = romPath,
                    engineConfig = engineConfig,
                    autoTuneActive = autoTuneActive
                )
            },
            onLaunchExternalClick = { romPath, engineConfig, autoTuneActive ->
                installManager.markLastPlayed(activeGame)
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

    val missingEmulator = missingEmulatorResult
    if (missingEmulator != null) {
        val targetGame = selectedGameForSheet ?: activeGameSession?.game
        val engineInfo = missingEmulator.engineInfo ?: targetGame?.let { EngineManager.getEngineForGame(it) } ?: EngineManager.ppssppEngine
        EngineInstallerDialog(
            engineInfo = engineInfo,
            gameTitle = targetGame?.title ?: missingEmulator.emulatorName,
            onDismiss = { missingEmulatorResult = null },
            onLaunchAfterInstall = {
                missingEmulatorResult = null
                targetGame?.let { game ->
                    val state = installManager.getStateForGame(game.id)
                    val romPath = (state as? GameState.Installed)?.romPath ?: ""
                    if (romPath.isNotBlank()) {
                        val optimalConfig = AutoTuningEngine.generateOptimalConfig(spec = hardwareSpec, game = game)
                        val res = GameLauncher.launchGame(
                            context = context,
                            game = game,
                            romPath = romPath,
                            engineConfig = optimalConfig,
                            autoTuneEnabled = true
                        )
                        if (res is LauncherResult.Launched) {
                            selectedGameForSheet = null
                            activeGameSession = null
                        }
                    }
                }
            }
        )
    }

    val detectedDownload = detectedDownloadForDialog
    if (detectedDownload != null) {
        SniffedGameDownloadDialog(
            sniffed = detectedDownload,
            onConfirm = {
                val gameItem = detectedDownload.toGameItem()
                installManager.installGame(gameItem)
                Toast.makeText(
                    context,
                    "Game terdeteksi ditambahkan ke Library: ${gameItem.title}",
                    Toast.LENGTH_SHORT
                ).show()
                detectedDownloadForDialog = null
                selectedBottomTab = NexusBottomTab.LIBRARY
            },
            onDismiss = {
                detectedDownloadForDialog = null
            }
        )
    }
}

@Composable
private fun FullRoidHeader(
    hardwareSpec: HardwareSpec,
    onSettingsClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brush = NexusPalette.VerticalVioletGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = "NexusDeck",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )
                Text(
                    text = "Universal Cloud & Console Hub",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextSecondary
                )
            }
        }

        // Badge Status Hardware Tipis di Pojok Kanan
        Surface(
            shape = RoundedCornerShape(50),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSettingsClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(NexusPalette.SecondaryPurple)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${hardwareSpec.socModel.take(16)} • ${hardwareSpec.totalRamGb}GB",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FullRoidSearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(50),
        color = NexusPalette.SurfaceCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (query.isNotEmpty()) NexusPalette.PrimaryViolet else NexusPalette.CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = NexusPalette.SecondaryPurple,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search games across PS2, PSP, PC, PS3...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusPalette.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(NexusPalette.SecondaryPurple),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NexusPalette.SecondaryPurple
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(NexusPalette.ElevatedBackground)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = NexusPalette.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PosterGameCard(
    game: GameItem,
    compatibilityTier: CompatibilityTier,
    gameState: GameState,
    isFavorite: Boolean,
    isFocused: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformColor = platformBadgeColor(game.platform)
    val isInstalled = gameState is GameState.Installed
    val borderColor = when {
        isFocused -> NexusPalette.SecondaryPurple
        isInstalled -> Color(0xFF22C55E).copy(alpha = 0.7f)
        else -> NexusPalette.CardBorder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .consoleCardFocus(
                isFocused = isFocused,
                focusRequester = focusRequester,
                onFocusChanged = onFocusChanged,
                onSelectAction = onClick,
                glowColor = NexusPalette.PrimaryViolet
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NexusPalette.SurfaceCard
        ),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = borderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full Vertical Poster Artwork
            GameCoverArt(
                coverUrl = game.coverUrl,
                title = game.title,
                platform = game.platform,
                modifier = Modifier.fillMaxSize()
            )

            // Pojok Kiri Atas: Tombol Favorite Kecil
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA0B0B0E))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFEC4899) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Pojok Kanan Atas: Floating Badge Platform (PSP, PS2, PC, PS3)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xD90B0B0E),
                border = BorderStroke(1.dp, platformColor.copy(alpha = 0.85f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = game.platform.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = platformColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Bagian Bawah Poster: Info Judul, Ukuran File, Status Unduhan & Kompatibilitas
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Status Pill jika sedang diunduh atau sudah terinstal
                if (gameState !is GameState.NotInstalled) {
                    PosterInstallBadge(gameState = gameState)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Dual-Engine Execution Indicator Tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                        Color(0xFF22C55E).copy(alpha = 0.2f)
                    else
                        NexusPalette.PrimaryViolet.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                            "• In-App Playable"
                        else
                            "• Native Handoff",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = if (game.executionMode == com.nexusdeck.app.core.model.EngineExecutionMode.IN_APP_PLAYABLE)
                            Color(0xFF22C55E)
                        else
                            NexusPalette.SecondaryPurple,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 18.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.fileSizeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusPalette.SecondaryPurple
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(compatibilityTier.badgeColor)
                        )
                        Text(
                            text = when (compatibilityTier) {
                                CompatibilityTier.VERIFIED_60FPS -> "60 FPS"
                                CompatibilityTier.PLAYABLE_TWEAKED -> "30-45 FPS"
                                CompatibilityTier.UNSUPPORTED -> "Low Spec"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = compatibilityTier.badgeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterInstallBadge(gameState: GameState) {
    val (text, color) = when (gameState) {
        is GameState.Downloading -> {
            val pct = (gameState.progress * 100).toInt().coerceIn(0, 100)
            "Downloading $pct%" to NexusPalette.SecondaryPurple
        }
        is GameState.Extracting -> "Optimizing..." to Color(0xFFFACC15)
        is GameState.Installed -> "✓ INSTALLED" to Color(0xFF22C55E)
        is GameState.NotInstalled -> "" to Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xDD0B0B0E),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SectionTabBanner(
    tab: NexusBottomTab,
    itemCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NexusPalette.SurfaceCard,
        border = BorderStroke(1.dp, NexusPalette.CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (tab) {
                        NexusBottomTab.FAVORITES -> "Favorite Games"
                        NexusBottomTab.LIBRARY -> "Downloads & Local Library"
                        else -> tab.label
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )
                Text(
                    text = when (tab) {
                        NexusBottomTab.FAVORITES -> "Koleksi game pilihan yang kamu tandai"
                        NexusBottomTab.LIBRARY -> "Tersimpan di /games/[platform]/ & siap dimainkan"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = NexusPalette.ElevatedBackground,
                border = BorderStroke(1.dp, NexusPalette.PrimaryViolet.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "$itemCount Games",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusPalette.SecondaryPurple,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTabPlaceholder(
    tab: NexusBottomTab,
    onExploreCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (tab == NexusBottomTab.FAVORITES) "Belum Ada Game Favorit" else "Library Masih Kosong",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (tab == NexusBottomTab.FAVORITES) {
                    "Ketuk ikon hati pada poster game untuk menyimpannya ke daftar favorit."
                } else {
                    "Pilih game dari katalog web lalu ketuk 'Download Langsung ke Nexus' agar tersimpan di perangkat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NexusPalette.TextSecondary
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(brush = NexusPalette.VioletGradient)
                    .clickable(onClick = onExploreCatalog)
                    .padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Text(
                    text = "Jelajahi Katalog Game",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    hardwareSpec: HardwareSpec,
    installedCount: Int,
    modifier: Modifier = Modifier
) {
    var turnipCustomDriver by remember {
        mutableStateOf(hardwareSpec.gpuModel.contains("adreno", ignoreCase = true))
    }
    var asyncShaderCache by remember { mutableStateOf(true) }
    var sustainedThermalMode by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Kartu Profil Hardware Terdeteksi
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hardware Profiler Diagnostics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingInfoRow("SoC / Chipset", hardwareSpec.socModel)
                SettingInfoRow("GPU Renderer", hardwareSpec.gpuModel)
                SettingInfoRow("System Memory (RAM)", "${hardwareSpec.totalRamGb} GB")
                SettingInfoRow("Graphics API", hardwareSpec.vulkanVersion)
                SettingInfoRow("Installed ROMs", "$installedCount Games (/games/[platform]/)")
            }
        }

        // Kartu Pengaturan Driver Turnip & Engine
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Driver & Auto-Tuning Engine",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingToggleRow(
                    title = "Mesa Turnip v24.2.0 Custom Driver",
                    subtitle = "Gunakan driver Vulkan kustom untuk GPU Adreno",
                    checked = turnipCustomDriver,
                    onCheckedChange = { turnipCustomDriver = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleRow(
                    title = "DXVK 2.3.1 Async Shader Pipeline",
                    subtitle = "Eliminasi stutter kompilasi shader pada game PC & PS3",
                    checked = asyncShaderCache,
                    onCheckedChange = { asyncShaderCache = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleRow(
                    title = "Smart Thermal Frame Pacing",
                    subtitle = "Jaga suhu perangkat tetap stabil pada sesi bermain panjang",
                    checked = sustainedThermalMode,
                    onCheckedChange = { sustainedThermalMode = it }
                )
            }
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NexusPalette.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NexusPalette.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexusPalette.PrimaryViolet,
                uncheckedThumbColor = NexusPalette.TextSecondary,
                uncheckedTrackColor = NexusPalette.ElevatedBackground
            )
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

@Composable
fun SniffedGameDownloadDialog(
    sniffed: SniffedDownload,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val platformColor = platformBadgeColor(sniffed.platform)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusPalette.SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = NexusPalette.TextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(brush = NexusPalette.VerticalVioletGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎮",
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = "Game Terdeteksi!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF13121A),
                    border = BorderStroke(1.dp, NexusPalette.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = sniffed.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 2
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = platformColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, platformColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = sniffed.platform.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = platformColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NexusPalette.CardBorder
                            ) {
                                Text(
                                    text = sniffed.fileSizeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = NexusPalette.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "•  ${sniffed.targetEmulator.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusPalette.TextMuted
                            )
                        }
                    }
                }

                Text(
                    text = "Simpan langsung file game ini ke NexusDeck dan unduh di latar belakang?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusPalette.TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexusPalette.PrimaryViolet,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Simpan ke NexusDeck",
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

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun DashboardScreenPortraitPreview() {
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
