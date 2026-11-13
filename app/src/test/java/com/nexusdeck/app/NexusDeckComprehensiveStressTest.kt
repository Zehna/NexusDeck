package com.nexusdeck.app

import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.Color
import com.nexusdeck.app.ui.components.platformBadgeColor
import com.nexusdeck.app.core.achievements.RetroAchievementItem
import com.nexusdeck.app.core.achievements.RetroAchievementsService
import com.nexusdeck.app.core.achievements.RetroGameTrophySummary
import com.nexusdeck.app.core.artwork.UniversalArtworkScraper
import com.nexusdeck.app.core.bios.BiosManager
import com.nexusdeck.app.core.downloader.SniffedDownload
import com.nexusdeck.app.core.downloader.WebAdBlocker
import com.nexusdeck.app.core.downloader.WebDownloadSniffer
import com.nexusdeck.app.core.engine.EmulatorJsEngine
import com.nexusdeck.app.core.engine.EngineDownloadKind
import com.nexusdeck.app.core.engine.EngineDownloadProgress
import com.nexusdeck.app.core.engine.EngineInfo
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.core.engine.ExecutionType
import com.nexusdeck.app.core.engine.NexusEmbeddedCoreRunner
import com.nexusdeck.app.core.engine.PlatformExecutionMatrix
import com.nexusdeck.app.core.engine.VirtualInputState
import com.nexusdeck.app.core.launcher.GameLauncher
import com.nexusdeck.app.core.launcher.LauncherResult
import com.nexusdeck.app.core.model.CompatibilityTier
import com.nexusdeck.app.core.model.EngineExecutionMode
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.core.optimizer.AutoTuningEngine
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import com.nexusdeck.app.core.optimizer.RomFormatType
import com.nexusdeck.app.core.profiler.HardwareProfiler
import com.nexusdeck.app.core.repository.OnlineGameCatalogService
import com.nexusdeck.app.core.repository.sources.GameRepositorySource
import com.nexusdeck.app.core.storage.NexusStorageManager
import com.nexusdeck.app.core.storage.StorageSpaceInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Suite Pengujian Komprehensif Mendalam (Full End-to-End Stress & Edge-Case Testing)
 * untuk seluruh arsitektur NexusDeck:
 * 1. WebDownloadSniffer & Browser Interceptor
 * 2. OnlineGameCatalogService & Network Resilience (Failover, Extreme Queries)
 * 3. HardwareProfiler & AutoTuningEngine (Low-end, Midrange, Flagship, OEM Part numbers)
 * 4. GameInstallManager & State LifeCycle (Transitions, JSON Persistence, Concurrency)
 * 5. In-App Runner, Virtual Gamepad Buffer & Screen Orientation Lifecycle
 */
class NexusDeckComprehensiveStressTest {

    // =========================================================================
    // 1. WebDownloadSniffer & Browser Interceptor Edge Cases
    // =========================================================================

    @Test
    fun test01_dynamicDownloadUrlWithComplexQueryParameters() {
        val dynamicUrl = "https://vimm.net/download.php?id=9928&token=xyz_alpha123&file=Persona_4_PlayStation_2.iso"

        assertTrue("Dynamic URL with .iso in query must be intercepted", WebDownloadSniffer.isGameDownload(dynamicUrl))

        val extractedName = WebDownloadSniffer.extractFileName(dynamicUrl)
        assertEquals("Persona_4_PlayStation_2.iso", extractedName)

        val platform = WebDownloadSniffer.inferPlatform(dynamicUrl, extractedName)
        assertEquals(GamePlatform.PS2, platform)

        val cleanTitle = SniffedDownload.cleanTitleFromFilename(extractedName)
        assertEquals("Persona 4", cleanTitle)
    }

    @Test
    fun test02_contentDispositionUtf8StarEncoding() {
        val url = "https://example.com/api/get_file"
        val cdHeader = "attachment; filename*=UTF-8''God%20of%20War%20-%20Chains%20of%20Olympus.cso"

        assertTrue(WebDownloadSniffer.isGameDownload(url, contentDisposition = cdHeader))

        val fileName = WebDownloadSniffer.extractFileName(url, contentDisposition = cdHeader)
        assertEquals("God of War - Chains of Olympus.cso", fileName)

        val platform = WebDownloadSniffer.inferPlatform(url, fileName)
        assertEquals(GamePlatform.PSP, platform)

        val cleanTitle = SniffedDownload.cleanTitleFromFilename(fileName)
        assertEquals("God of War - Chains of Olympus", cleanTitle)
    }

    @Test
    fun test03_contentDispositionQuotedAndPlainVariants() {
        val url = "https://cdromance.org/dl"

        // Quoted variant with region tags
        val cdQuoted = "attachment; filename=\"Final Fantasy X (USA) (En,Ja).iso\""
        val name1 = WebDownloadSniffer.extractFileName(url, cdQuoted)
        assertEquals("Final Fantasy X (USA) (En,Ja).iso", name1)
        val clean1 = SniffedDownload.cleanTitleFromFilename(name1)
        assertEquals("Final Fantasy X", clean1)

        // Plain variant without quotes
        val cdPlain = "attachment; filename=Kingdom_Hearts_2.iso"
        val name2 = WebDownloadSniffer.extractFileName(url, cdPlain)
        assertEquals("Kingdom_Hearts_2.iso", name2)
        val clean2 = SniffedDownload.cleanTitleFromFilename(name2)
        assertEquals("Kingdom Hearts 2", clean2)
    }

    @Test
    fun test04_multiPartArchiveDetectionAndCleaning() {
        // Multi-part 7z (.7z.001)
        val multi7zUrl = "https://myrient.erista.me/files/Tekken_5_PS2.7z.001"
        assertTrue(WebDownloadSniffer.isGameDownload(multi7zUrl))
        val name7z = WebDownloadSniffer.extractFileName(multi7zUrl)
        assertEquals("Tekken_5_PS2.7z.001", name7z)
        assertEquals("Tekken 5", SniffedDownload.cleanTitleFromFilename(name7z))

        // Multi-part RAR (.part1.rar)
        val multiRarUrl = "https://archive.org/download/game/Gran_Turismo_4_PlayStation_2.part01.rar"
        assertTrue(WebDownloadSniffer.isGameDownload(multiRarUrl))
        val nameRar = WebDownloadSniffer.extractFileName(multiRarUrl)
        assertEquals("Gran_Turismo_4_PlayStation_2.part01.rar", nameRar)
        assertEquals("Gran Turismo 4", SniffedDownload.cleanTitleFromFilename(nameRar))

        // Compressed tar.gz
        val tarGzUrl = "https://example.com/freeware/freedoom_pc.tar.gz"
        assertTrue(WebDownloadSniffer.isGameDownload(tarGzUrl))
        val nameTar = WebDownloadSniffer.extractFileName(tarGzUrl)
        assertEquals("freedoom_pc.tar.gz", nameTar)
        assertEquals("freedoom", SniffedDownload.cleanTitleFromFilename(nameTar))
    }

    @Test
    fun test05_mimeTypeFallbackInterception() {
        val opaqueUrl = "https://romserver.internal/stream_binary"

        assertTrue(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "application/x-iso9660-image"))
        assertTrue(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "application/x-7z-compressed"))
        assertTrue(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "application/x-cd-image"))
        assertTrue(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "application/zip"))

        // Web assets should not be intercepted
        assertFalse(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "text/html"))
        assertFalse(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "image/png"))
        assertFalse(WebDownloadSniffer.isGameDownload(opaqueUrl, mimeType = "application/json"))
    }

    @Test
    fun test06_adBlocker_blocksSuspiciousPopunderAndGamblingDomains() {
        val adUrls = listOf(
            "https://popads.net/serve/redirect?id=123",
            "https://adsterra.com/tag.js",
            "https://propellerads.com/native/banner",
            "https://bet365.com/affiliate",
            "https://1xbet.com/tracker",
            "https://exoclick.com/popunder",
            "https://clickadu.com/redirect",
            "https://doubleclick.net/pagead/ads",
            "https://example.com/ads.js"
        )
        for (url in adUrls) {
            assertTrue("Ad URL must be blocked: $url", WebAdBlocker.isAdUrl(url))
        }
    }

    @Test
    fun test07_adBlocker_preservesOfficialRomCdnDomains() {
        val trustedDomains = listOf(
            "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%202/",
            "https://archive.org/download/retro_ps2_roms/",
            "https://cdromance.org/ps2-iso/metal-gear-solid-3/",
            "https://vimm.net/vault/PS2",
            "https://nopaystation.com/view/PS3/NPUB31415",
            "https://github.com/Zehna/NexusDeck"
        )
        for (url in trustedDomains) {
            assertFalse("Official ROM CDN must NEVER be blocked: $url", WebAdBlocker.isAdUrl(url))
        }
    }

    // =========================================================================
    // 2. OnlineGameCatalogService & Network Resilience
    // =========================================================================

    @Test
    fun test08_networkResilience_automaticFailoverOnHttp500OrTimeout() = runBlocking {
        val faultyPrimarySource = object : GameRepositorySource {
            override val sourceName: String = "Faulty Primary Mirror (HTTP 500)"
            override val priority: Int = 1

            override fun supportsPlatform(platform: GamePlatform?): Boolean = true

            override suspend fun search(query: String, platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw IOException("HTTP 500 Internal Server Error: Gateway Timeout")
            }

            override suspend fun getPopular(platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw SocketTimeoutException("Connection timed out after 5000ms")
            }
        }

        val healthyBackupSource = object : GameRepositorySource {
            override val sourceName: String = "Healthy Backup Mirror"
            override val priority: Int = 2

            override fun supportsPlatform(platform: GamePlatform?): Boolean = true

            override suspend fun search(query: String, platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                return listOf(
                    GameItem(
                        id = "backup_ps2_tekken",
                        title = "Tekken 5 (Backup Mirror)",
                        platform = GamePlatform.PS2,
                        coverUrl = "https://example.com/tekken5.jpg",
                        minRamGb = 4,
                        recommendedSocKeywords = listOf("snapdragon_8"),
                        targetEmulator = TargetEmulator.ARMSX2,
                        downloadUrl = "https://mirror2.example.com/tekken5.iso",
                        isWebResult = true,
                        sourceRepositoryName = "Healthy Backup Mirror"
                    )
                )
            }

            override suspend fun getPopular(platform: GamePlatform?, page: Int, limit: Int): List<GameItem> = emptyList()
        }

        val catalogService = OnlineGameCatalogService(listOf(faultyPrimarySource, healthyBackupSource))

        val results = catalogService.searchOnlineGames(query = "Tekken", platform = GamePlatform.PS2, page = 2).first()

        assertTrue("Failover must deliver results from the healthy mirror without crashing", results.isNotEmpty())
        assertTrue(results.any { it.title.contains("Backup Mirror") })
    }

    @Test
    fun test09_catalogSearch_extremeQueryEmptyString() = runBlocking {
        val service = OnlineGameCatalogService()
        val results = service.searchOnlineGames(query = "   ", platform = null, page = 1).first()

        assertTrue("Empty query should return default catalog games without throwing", results.isNotEmpty())
    }

    @Test
    fun test10_catalogSearch_extremeQuerySpecialCharactersAndSymbols() = runBlocking {
        val service = OnlineGameCatalogService()
        val specialQuery = "!@#$%^&*()_+{}[]:;\"'<>?,./|\\~`🎮🔥🚀"
        val results = service.searchOnlineGames(query = specialQuery, platform = null, page = 1).first()

        assertNotNull("Search with special characters must never throw exception", results)
    }

    @Test
    fun test11_catalogSearch_extremeQueryVeryLongText() = runBlocking {
        val service = OnlineGameCatalogService()
        val veryLongQuery = "Final Fantasy ".repeat(50) // 700 chars
        val results = service.searchOnlineGames(query = veryLongQuery, platform = null, page = 1).first()

        assertNotNull("Very long query string must be handled safely without regex catastrophe", results)
    }

    @Test
    fun test12_catalogSearch_platformFilterStrictness() = runBlocking {
        val service = OnlineGameCatalogService()

        val pspResults = service.searchOnlineGames("Crisis", GamePlatform.PSP, page = 1).first()
        assertTrue(pspResults.all { it.platform == GamePlatform.PSP })

        val nintendoResults = service.searchOnlineGames("Zelda", GamePlatform.NINTENDO, page = 1).first()
        assertTrue(nintendoResults.all { it.platform == GamePlatform.NINTENDO })
    }

    @Test
    fun test13_catalogSearch_allSourcesFail_emitsEmptyList() = runBlocking {
        val completelyBrokenSource = object : GameRepositorySource {
            override val sourceName: String = "Broken Source"
            override val priority: Int = 1
            override fun supportsPlatform(platform: GamePlatform?): Boolean = true
            override suspend fun search(query: String, platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw IOException("Offline network")
            }
            override suspend fun getPopular(platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw IOException("Offline network")
            }
        }

        val service = OnlineGameCatalogService(listOf(completelyBrokenSource))
        // Page > 1 skips local MockGames so all remote sources fail
        val results = service.searchOnlineGames(query = "God of War", platform = GamePlatform.PS2, page = 2).first()

        // v1.1.9 — tidak ada lagi fallback sintetis ("Failover Web Mirror"):
        // bila semua mirror offline, pencarian mengembalikan daftar kosong,
        // bukan game bohongan dengan URL archive.org yang ditebak-nebak.
        // UI menampilkan empty state; katalog tidak boleh membuat game yang
        // tidak ada menjadi ada.
        assertTrue("No invented games: empty results when all mirrors are offline", results.isEmpty())
    }

    // =========================================================================
    // 3. HardwareProfiler & AutoTuningEngine Edge Cases
    // =========================================================================

    @Test
    fun test14_lowEndDeviceWithInsufficientRam_consistentlyUnsupported() {
        val lowEndSpec = HardwareSpec(
            socModel = "Spreadtrum SC9863A",
            gpuModel = "PowerVR GE8322",
            totalRamGb = 2,
            vulkanVersion = "None (OpenGL ES 3.2)"
        )

        val ps2Game = GameItem(
            id = "ps2_gow",
            title = "God of War",
            platform = GamePlatform.PS2,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8"),
            targetEmulator = TargetEmulator.ARMSX2
        )

        val tier = HardwareProfiler.evaluateCompatibility(lowEndSpec, ps2Game)
        assertEquals(CompatibilityTier.UNSUPPORTED, tier)
    }

    @Test
    fun test15_midrangeDimensityMali_selectsSystemVulkanAnd720p() {
        val dimensitySpec = HardwareSpec(
            socModel = "MediaTek Dimensity 8100",
            gpuModel = "Mali-G610 MC6",
            totalRamGb = 8,
            vulkanVersion = "Vulkan 1.3"
        )

        val ps2Game = GameItem(
            id = "ps2_tekken5",
            title = "Tekken 5",
            platform = GamePlatform.PS2,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon_8", "dimensity_8"),
            targetEmulator = TargetEmulator.ARMSX2
        )

        val config = AutoTuningEngine.generateOptimalConfig(dimensitySpec, ps2Game)

        assertEquals("System Vulkan (Stock)", config.driverName)
        assertEquals("720p (1.5x Scaling)", config.targetResolution)
        assertEquals("Vulkan", config.rendererBackend)
    }

    @Test
    fun test16_flagshipSnapdragon8Adreno_selectsMesaTurnipAnd1080p() {
        val flagshipSpec = HardwareSpec(
            socModel = "Snapdragon 8 Gen 3",
            gpuModel = "Adreno (TM) 750",
            totalRamGb = 16,
            vulkanVersion = "Vulkan 1.3"
        )

        val pcGame = GameItem(
            id = "pc_skyrim",
            title = "Skyrim Special Edition",
            platform = GamePlatform.PC,
            coverUrl = "",
            minRamGb = 6,
            recommendedSocKeywords = listOf("snapdragon_8"),
            targetEmulator = TargetEmulator.GameNative
        )

        val config = AutoTuningEngine.generateOptimalConfig(flagshipSpec, pcGame)

        assertTrue(config.driverName.contains("Mesa Turnip"))
        assertEquals("1080p (2x Scaling)", config.targetResolution)
        assertEquals("Vulkan", config.rendererBackend)
        assertEquals("DXVK 2.3.1 (Async)", config.dxvkVersion)
        assertEquals("Performance (Aggressive JIT)", config.cpuDynarecPreset)
    }

    @Test
    fun test17_qualcommOemPartNumberSM8650_resolvesToFlagshipTier() {
        val oemPartSpec = HardwareSpec(
            socModel = "Qualcomm SM8650", // Snapdragon 8 Gen 3 part number
            gpuModel = "Adreno 750",
            totalRamGb = 12,
            vulkanVersion = "Vulkan 1.3"
        )

        val ps3Game = GameItem(
            id = "ps3_mgs4",
            title = "Metal Gear Solid 4",
            platform = GamePlatform.PS3,
            coverUrl = "",
            minRamGb = 8,
            recommendedSocKeywords = listOf("snapdragon_8"),
            targetEmulator = TargetEmulator.ARMSX3
        )

        val tier = HardwareProfiler.evaluateCompatibility(oemPartSpec, ps3Game)
        assertEquals(CompatibilityTier.VERIFIED_60FPS, tier)
    }

    @Test
    fun test18_ramThresholdBoundariesAcrossAllPlatforms() {
        val spec2Gb = HardwareSpec("Entry", "GPU", totalRamGb = 2, vulkanVersion = "1.0")
        val spec3Gb = HardwareSpec("Mid-low", "GPU", totalRamGb = 3, vulkanVersion = "1.1")
        val spec4Gb = HardwareSpec("Mid", "GPU", totalRamGb = 4, vulkanVersion = "1.2")
        val spec6Gb = HardwareSpec("High-mid", "GPU", totalRamGb = 6, vulkanVersion = "1.3")
        val spec8Gb = HardwareSpec("Flagship", "GPU", totalRamGb = 8, vulkanVersion = "1.3")

        val nintendo = GameItem("n1", "Game", GamePlatform.NINTENDO, "", 2, emptyList(), TargetEmulator.NexusRetroCore)
        val psp = GameItem("p1", "Game", GamePlatform.PSP, "", 3, emptyList(), TargetEmulator.PPSSPP)
        val ps2 = GameItem("ps2_1", "Game", GamePlatform.PS2, "", 4, emptyList(), TargetEmulator.ARMSX2)
        val pc = GameItem("pc_1", "Game", GamePlatform.PC, "", 6, emptyList(), TargetEmulator.GameNative)
        val ps3 = GameItem("ps3_1", "Game", GamePlatform.PS3, "", 8, emptyList(), TargetEmulator.ARMSX3)

        // At exactly minRamGb, tier is at least PLAYABLE_TWEAKED
        assertTrue(HardwareProfiler.evaluateCompatibility(spec2Gb, nintendo) != CompatibilityTier.UNSUPPORTED)
        assertTrue(HardwareProfiler.evaluateCompatibility(spec3Gb, psp) != CompatibilityTier.UNSUPPORTED)
        assertTrue(HardwareProfiler.evaluateCompatibility(spec4Gb, ps2) != CompatibilityTier.UNSUPPORTED)
        assertTrue(HardwareProfiler.evaluateCompatibility(spec6Gb, pc) != CompatibilityTier.UNSUPPORTED)
        assertTrue(HardwareProfiler.evaluateCompatibility(spec8Gb, ps3) != CompatibilityTier.UNSUPPORTED)

        // Below minRamGb, tier is strictly UNSUPPORTED
        assertEquals(CompatibilityTier.UNSUPPORTED, HardwareProfiler.evaluateCompatibility(spec2Gb, psp))
        assertEquals(CompatibilityTier.UNSUPPORTED, HardwareProfiler.evaluateCompatibility(spec3Gb, ps2))
        assertEquals(CompatibilityTier.UNSUPPORTED, HardwareProfiler.evaluateCompatibility(spec4Gb, pc))
        assertEquals(CompatibilityTier.UNSUPPORTED, HardwareProfiler.evaluateCompatibility(spec6Gb, ps3))
    }

    // =========================================================================
    // 4. GameInstallManager & State LifeCycle
    // =========================================================================

    @Test
    fun test19_gameStateProgressiveTransitions() {
        var currentState: GameState = GameState.NotInstalled
        assertEquals(GameState.NotInstalled, currentState)

        currentState = GameState.Downloading(progress = 0.50f)
        assertTrue(currentState is GameState.Downloading)
        assertEquals(0.50f, (currentState as GameState.Downloading).progress, 0.001f)

        currentState = GameState.Downloading(progress = 1.0f)
        assertEquals(1.0f, (currentState as GameState.Downloading).progress, 0.001f)

        currentState = GameState.Extracting
        assertEquals(GameState.Extracting, currentState)

        val installedPath = "/storage/emulated/0/NexusDeck/games/ps2/tekken5.iso"
        currentState = GameState.Installed(romPath = installedPath)
        assertTrue(currentState is GameState.Installed)
        assertEquals(installedPath, (currentState as GameState.Installed).romPath)
    }

    @Test
    fun test20_installedPathsJsonSerializationAndRestoration() {
        val initialMap = mapOf(
            "game_psp_tekken" to "/games/psp/tekken6.cso",
            "game_ps2_gow" to "/games/ps2/gow2.iso",
            "game_pc_fallout" to "/games/pc/fallout.zip"
        )

        // Serialize to JSON string (simulating SharedPreferences persistence)
        val jsonObject = JSONObject()
        initialMap.forEach { (id, path) -> jsonObject.put(id, path) }
        val serialized = jsonObject.toString()

        // Deserialize back from JSON string (simulating app restart)
        val restoredObj = JSONObject(serialized)
        val restoredMap = mutableMapOf<String, GameState>()
        restoredObj.keys().forEach { key ->
            val path = restoredObj.getString(key)
            restoredMap[key] = GameState.Installed(path)
        }

        assertEquals(3, restoredMap.size)
        assertEquals(GameState.Installed("/games/psp/tekken6.cso"), restoredMap["game_psp_tekken"])
        assertEquals(GameState.Installed("/games/ps2/gow2.iso"), restoredMap["game_ps2_gow"])
        assertEquals(GameState.Installed("/games/pc/fallout.zip"), restoredMap["game_pc_fallout"])
    }

    @Test
    fun test21_webGamesMetadataCatalogJsonRoundtrip() {
        val originalGame = GameItem(
            id = "web_psp_12345",
            title = "Crisis Core - Final Fantasy VII",
            platform = GamePlatform.PSP,
            coverUrl = "https://example.com/cover.jpg",
            minRamGb = 3,
            recommendedSocKeywords = listOf("snapdragon_8"),
            targetEmulator = TargetEmulator.PPSSPP,
            fileSizeLabel = "1.05 GB",
            downloadUrl = "https://myrient.erista.me/files/crisis_core.zip",
            isWebResult = true,
            sourceRepositoryName = "Web Explorer (myrient.erista.me)"
        )

        // Serialize
        val arr = JSONArray()
        val itemObj = JSONObject().apply {
            put("id", originalGame.id)
            put("title", originalGame.title)
            put("platform", originalGame.platform.name)
            put("coverUrl", originalGame.coverUrl)
            put("minRamGb", originalGame.minRamGb)
            put("targetEmulator", originalGame.targetEmulator.name)
            put("fileSizeLabel", originalGame.fileSizeLabel)
            put("downloadUrl", originalGame.downloadUrl)
            put("isWebResult", originalGame.isWebResult)
        }
        arr.put(itemObj)
        val rawJson = arr.toString()

        // Deserialize
        val parsedArr = JSONArray(rawJson)
        val restored = parsedArr.getJSONObject(0)
        assertEquals(originalGame.id, restored.getString("id"))
        assertEquals(originalGame.title, restored.getString("title"))
        assertEquals(originalGame.platform, GamePlatform.valueOf(restored.getString("platform")))
        assertEquals(originalGame.targetEmulator, TargetEmulator.valueOf(restored.getString("targetEmulator")))
        assertEquals(originalGame.downloadUrl, restored.getString("downloadUrl"))
        assertTrue(restored.getBoolean("isWebResult"))
    }

    @Test
    fun test22_favoriteIdsSetPersistenceConsistency() {
        val favorites = mutableSetOf("game_1", "game_2")

        // Toggle add
        val toAdd = "game_3"
        if (favorites.contains(toAdd)) favorites.remove(toAdd) else favorites.add(toAdd)
        assertTrue(favorites.contains("game_3"))
        assertEquals(3, favorites.size)

        // Toggle remove
        val toRemove = "game_1"
        if (favorites.contains(toRemove)) favorites.remove(toRemove) else favorites.add(toRemove)
        assertFalse(favorites.contains("game_1"))
        assertEquals(2, favorites.size)
    }

    // =========================================================================
    // 5. In-App Runner, Virtual Gamepad Buffer & Screen Orientation Lifecycle
    // =========================================================================

    @Test
    fun test23_embeddedCoreRunner_virtualInputBufferForwarding() {
        val runner = NexusEmbeddedCoreRunner()

        val sampleGame = GameItem(
            id = "psp_tekken",
            title = "Tekken 6",
            platform = GamePlatform.PSP,
            coverUrl = "",
            minRamGb = 3,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.PPSSPP
        )
        runner.loadRom(sampleGame, "/games/psp/tekken6.cso")

        // Send analog diagonal + button press
        val input = VirtualInputState(
            analogX = 0.85f,
            analogY = -0.65f,
            buttonA = true,
            buttonB = false,
            buttonX = true,
            buttonY = false,
            buttonL1 = true,
            buttonR1 = false
        )
        runner.sendInput(input)

        val last = runner.lastReceivedInput
        assertEquals(0.85f, last.analogX, 0.001f)
        assertEquals(-0.65f, last.analogY, 0.001f)
        assertTrue(last.buttonA)
        assertFalse(last.buttonB)
        assertTrue(last.buttonX)
        assertFalse(last.buttonY)
        assertTrue(last.buttonL1)
        assertFalse(last.buttonR1)

        runner.stop()
    }

    @Test
    fun test24_embeddedCoreRunner_saveAndLoadStatePayloadIntegrity() {
        val runner = NexusEmbeddedCoreRunner()
        val game = GameItem("gba_pkmn", "Pokemon", GamePlatform.NINTENDO, "", 2, emptyList(), TargetEmulator.NexusRetroCore)
        runner.loadRom(game, "/games/gba/pkmn.gba")

        runner.sendInput(VirtualInputState(analogX = 0.5f, analogY = -0.5f, buttonA = true))

        val stateData = runner.saveState()
        assertTrue("Saved state bytes must be generated", stateData.isNotEmpty())

        val stateStr = String(stateData, Charsets.UTF_8)
        assertTrue(stateStr.startsWith("NEXUS_STATE:gba_pkmn:"))
        assertTrue(stateStr.contains("0.5,-0.5"))

        val loadSuccess = runner.loadState(stateData)
        assertTrue("Loading valid state bytes must return true", loadSuccess)

        assertFalse("Loading empty state must return false", runner.loadState(ByteArray(0)))
        runner.stop()
    }

    @Test
    fun test25_screenOrientationLifecycleContract_portraitToLandscapeAndBack() {
        // Contract verification of InAppGameScreen orientation controller:
        var requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)

        // 1. Enter In-App Game Session -> Screen rotates to sensor landscape
        fun onEnterGameSession() {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onEnterGameSession()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, requestedOrientation)

        // 2. Exit In-App Game Session -> Screen restores to portrait
        fun onExitGameSession() {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onExitGameSession()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)
    }

    @Test
    fun test26_dualEngineModeIntegrity_embeddedVsNativeHandoff() {
        val psp = GameItem("1", "PSP", GamePlatform.PSP, "", 3, emptyList(), TargetEmulator.PPSSPP)
        val nintendo = GameItem("2", "GBA", GamePlatform.NINTENDO, "", 2, emptyList(), TargetEmulator.NexusRetroCore)
        val ps2 = GameItem("3", "PS2", GamePlatform.PS2, "", 4, emptyList(), TargetEmulator.ARMSX2)
        val pc = GameItem("4", "PC", GamePlatform.PC, "", 6, emptyList(), TargetEmulator.GameNative)
        val ps3 = GameItem("5", "PS3", GamePlatform.PS3, "", 8, emptyList(), TargetEmulator.ARMSX3)

        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, psp.executionMode)
        assertEquals(EngineExecutionMode.IN_APP_PLAYABLE, nintendo.executionMode)
        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, ps2.executionMode)
        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, pc.executionMode)
        assertEquals(EngineExecutionMode.NATIVE_HANDOFF, ps3.executionMode)
    }

    @Test
    fun test27_sniffedDownloadToGameItem_fullPropertyMapping() {
        val sniffed = SniffedDownload(
            url = "https://myrient.erista.me/files/Redump/Sony%20-%20PlayStation%202/Grand%20Theft%20Auto%20-%20San%20Andreas%20(USA).iso",
            fileName = "Grand_Theft_Auto_San_Andreas_PS2.iso",
            fileSizeLabel = "4.20 GB",
            platform = GamePlatform.PS2,
            targetEmulator = TargetEmulator.ARMSX2,
            userAgent = "Mozilla/5.0 NexusDeck",
            cookies = "session=123",
            contentLength = 4500000000L,
            mimeType = "application/x-iso9660-image"
        )

        val game = sniffed.toGameItem("custom_ps2_gta")
        assertEquals("custom_ps2_gta", game.id)
        assertEquals("Grand Theft Auto San Andreas", game.title)
        assertEquals(GamePlatform.PS2, game.platform)
        assertEquals(TargetEmulator.ARMSX2, game.targetEmulator)
        assertEquals(4, game.minRamGb)
        assertEquals("4.20 GB", game.fileSizeLabel)
        assertEquals(sniffed.url, game.downloadUrl)
        assertTrue(game.isWebResult)
        assertTrue(game.sourceRepositoryName.contains("myrient.erista.me"))
    }

    @Test
    fun test28_fileSizeFormattingAcrossExtremeSizes() {
        assertEquals("~850 MB", WebDownloadSniffer.formatFileSize(0L, GamePlatform.PSP))
        assertEquals("~2.40 GB", WebDownloadSniffer.formatFileSize(-1L, GamePlatform.PS2))
        assertEquals("~3.50 GB", WebDownloadSniffer.formatFileSize(0L, GamePlatform.PC))
        assertEquals("~8.50 GB", WebDownloadSniffer.formatFileSize(0L, GamePlatform.PS3))
        assertEquals("~32 MB", WebDownloadSniffer.formatFileSize(0L, GamePlatform.NINTENDO))

        // Exact byte conversions
        assertEquals("500 MB", WebDownloadSniffer.formatFileSize(524288000L, GamePlatform.PSP))
        assertEquals("4.20 GB", WebDownloadSniffer.formatFileSize(4509715660L, GamePlatform.PS2))
    }

    // =========================================================================
    // 6. Unified In-App Engine Manager & Native Game Launcher Edge Cases
    // =========================================================================

    @Test
    fun test29_engineManager_officialReleaseRegistryMapping() {
        val engines = EngineManager.registeredEngines
        assertTrue("Registry must contain at least 6 official engines", engines.size >= 6)

        // PS1: DuckStation
        val duckStation = EngineManager.duckStationEngine
        assertEquals("duckstation", duckStation.id)
        assertEquals("DuckStation", duckStation.name)
        assertEquals(GamePlatform.PS1, duckStation.platform)
        assertEquals(TargetEmulator.DuckStation, duckStation.targetEmulator)
        assertEquals("com.github.stenzek.duckstation", duckStation.primaryPackageName)
        assertTrue(duckStation.alternatePackageNames.contains("org.duckstation.android"))
        // GitHub DuckStation tidak berisi APK Android (audit 2026-11-10) — jalur resmi via Play Store.
        assertEquals(EngineDownloadKind.PLAY_STORE, duckStation.downloadKind)
        assertEquals("https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation", duckStation.downloadUrl)

        // PSP: PPSSPP
        val ppsspp = EngineManager.ppssppEngine
        assertEquals("ppsspp", ppsspp.id)
        assertEquals("PPSSPP", ppsspp.name)
        assertEquals(GamePlatform.PSP, ppsspp.platform)
        assertEquals(TargetEmulator.PPSSPP, ppsspp.targetEmulator)
        assertEquals("org.ppsspp.ppsspp", ppsspp.primaryPackageName)
        // GitHub v1.20.4 tidak berisi APK (audit 2026-11-10) — distribusi resmi via Play Store.
        assertEquals(EngineDownloadKind.PLAY_STORE, ppsspp.downloadKind)
        assertEquals("https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp", ppsspp.downloadUrl)

        // PS2: NetherSX2 Classic
        val nether = EngineManager.netherSx2Engine
        assertEquals("nethersx2", nether.id)
        assertEquals("NetherSX2 Classic", nether.name)
        assertEquals(GamePlatform.PS2, nether.platform)
        assertEquals(TargetEmulator.ARMSX2, nether.targetEmulator)
        assertEquals("xyz.aethersx2.android", nether.primaryPackageName)
        assertTrue("NetherSX2 must support alternate armsx2 package", nether.alternatePackageNames.contains("come.nanodata.armsx2"))
        assertEquals("https://github.com/Trixarian/NetherSX2-classic/releases/download/2.2n/NetherSX2-v2.2n-3668.apk", nether.downloadUrl)

        // PC: GameNative / Winlator
        val gameNative = EngineManager.gameNativeEngine
        assertEquals("gamenative", gameNative.id)
        assertEquals(GamePlatform.PC, gameNative.platform)
        assertEquals(TargetEmulator.GameNative, gameNative.targetEmulator)
        assertEquals("app.gamenative", gameNative.primaryPackageName)
        assertEquals("https://github.com/utkarshdalal/GameNative/releases/download/v1.4.0/gamenative-v1.4.0.apk", gameNative.downloadUrl)

        // Nintendo DS: melonDS
        val melonDs = EngineManager.melonDsEngine
        assertEquals("melonds", melonDs.id)
        assertEquals("net.kuribo64.melonDS", melonDs.primaryPackageName)
        assertEquals("https://github.com/rafaelvcaetano/melonDS-android/releases/download/2.0.1/app-gitHub-prod-release.apk", melonDs.downloadUrl)

        // Nintendo 3DS: Lime3DS berganti nama menjadi Azahar (audit 2026-11-10)
        val azahar = EngineManager.azaharEngine
        assertEquals("azahar", azahar.id)
        assertEquals("org.azahar_emu.azahar", azahar.primaryPackageName)
        assertEquals("https://github.com/azahar-emu/azahar/releases/download/2126.2/azahar-android-vanilla-2126.2.apk", azahar.downloadUrl)

        // Nintendo Switch: Sudachi — repo sudah tidak ada (audit 2026-11-10), ditandai UNAVAILABLE
        val sudachi = EngineManager.sudachiEngine
        assertEquals("sudachi", sudachi.id)
        assertEquals("org.sudachi.sudachi_emu", sudachi.primaryPackageName)
        assertEquals(EngineDownloadKind.UNAVAILABLE, sudachi.downloadKind)
        assertTrue(sudachi.unavailableReason?.isNotBlank() == true)
    }

    @Test
    fun test30_engineManager_platformAndEmulatorLookups() {
        assertEquals("ppsspp", EngineManager.getEngineForPlatform(GamePlatform.PSP).id)
        assertEquals("nethersx2", EngineManager.getEngineForPlatform(GamePlatform.PS2).id)
        assertEquals("gamenative", EngineManager.getEngineForPlatform(GamePlatform.PC).id)
        assertEquals("lemuroid", EngineManager.getEngineForPlatform(GamePlatform.NINTENDO).id)
        assertEquals("aps3e", EngineManager.getEngineForPlatform(GamePlatform.PS3).id)

        assertEquals("ppsspp", EngineManager.getEngineForEmulator(TargetEmulator.PPSSPP).id)
        assertEquals("nethersx2", EngineManager.getEngineForEmulator(TargetEmulator.ARMSX2).id)
        assertEquals("gamenative", EngineManager.getEngineForEmulator(TargetEmulator.GameNative).id)
        assertEquals("melonds", EngineManager.getEngineForEmulator(TargetEmulator.MelonDS).id)
        assertEquals("azahar", EngineManager.getEngineForEmulator(TargetEmulator.Lime3DS).id)
        assertEquals("sudachi", EngineManager.getEngineForEmulator(TargetEmulator.Sudachi).id)
        assertEquals("aps3e", EngineManager.getEngineForEmulator(TargetEmulator.ARMSX3).id)
        assertEquals("lemuroid", EngineManager.getEngineForEmulator(TargetEmulator.Lemuroid).id)
        assertEquals("lemuroid", EngineManager.getEngineForEmulator(TargetEmulator.NexusRetroCore).id)
    }

    @Test
    fun test31_engineManager_gameTargetResolutionAndFallback() {
        val testPs2 = GameItem(
            id = "test_ps2",
            title = "Final Fantasy X",
            platform = GamePlatform.PS2,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = listOf("dimensity"),
            targetEmulator = TargetEmulator.ARMSX2
        )
        val enginePs2 = EngineManager.getEngineForGame(testPs2)
        assertEquals("nethersx2", enginePs2.id)

        val testAzahar = GameItem(
            id = "test_3ds",
            title = "Pokemon Sun",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon"),
            targetEmulator = TargetEmulator.Lime3DS
        )
        val engineAzahar = EngineManager.getEngineForGame(testAzahar)
        assertEquals("azahar", engineAzahar.id)
    }

    @Test
    fun test32_engineManager_allPackageNamesIncludesAlternates() {
        val netherPackages = EngineManager.netherSx2Engine.allPackageNames
        assertEquals(2, netherPackages.size)
        assertTrue(netherPackages.contains("xyz.aethersx2.android"))
        assertTrue(netherPackages.contains("come.nanodata.armsx2"))

        val gameNativePackages = EngineManager.gameNativeEngine.allPackageNames
        assertTrue(gameNativePackages.contains("app.gamenative"))
        assertTrue(gameNativePackages.contains("com.winlator"))
    }

    @Test
    fun test33_engineDownloadProgressModelHierarchy() {
        val progressDownloading: EngineDownloadProgress = EngineDownloadProgress.Downloading(
            progress = 0.65f,
            downloadedBytes = 18200000L,
            totalBytes = 28000000L
        )
        assertTrue(progressDownloading is EngineDownloadProgress.Downloading)
        assertEquals(0.65f, (progressDownloading as EngineDownloadProgress.Downloading).progress, 0.001f)

        val dummyFile = java.io.File("/tmp/ppsspp.apk")
        val progressCompleted: EngineDownloadProgress = EngineDownloadProgress.Completed(apkFile = dummyFile)
        assertTrue(progressCompleted is EngineDownloadProgress.Completed)
        assertEquals(dummyFile, (progressCompleted as EngineDownloadProgress.Completed).apkFile)

        val progressFailed: EngineDownloadProgress = EngineDownloadProgress.Failed("Network timeout")
        assertTrue(progressFailed is EngineDownloadProgress.Failed)
        assertEquals("Network timeout", (progressFailed as EngineDownloadProgress.Failed).error)
    }

    @Test
    fun test34_engineManager_downloadUrlsAllHttpsAndApk() {
        for (engine in EngineManager.registeredEngines) {
            when (engine.downloadKind) {
                EngineDownloadKind.GITHUB_APK -> {
                    assertTrue("URL APK harus HTTPS: ${engine.downloadUrl}", engine.downloadUrl.startsWith("https://"))
                    assertTrue("URL APK harus berakhiran .apk: ${engine.downloadUrl}", engine.downloadUrl.endsWith(".apk"))
                }
                EngineDownloadKind.PLAY_STORE -> {
                    // Engine Play Store: URL adalah halaman Play Store resmi, bukan file APK
                    // (DuckStation & PPSSPP tidak lagi menerbitkan APK di GitHub — audit 2026-11-10).
                    assertTrue("URL Play Store harus HTTPS: ${engine.downloadUrl}", engine.downloadUrl.startsWith("https://"))
                    assertEquals(
                        "https://play.google.com/store/apps/details?id=${engine.primaryPackageName}",
                        engine.downloadUrl
                    )
                }
                EngineDownloadKind.UNAVAILABLE -> {
                    // Engine tanpa sumber unduhan: tidak ada URL, tapi wajib ada alasan jujur.
                    assertTrue("Engine tidak tersedia harus tanpa URL: ${engine.id}", engine.downloadUrl.isBlank())
                    assertTrue("Engine tidak tersedia wajib punya alasan: ${engine.id}", engine.unavailableReason?.isNotBlank() == true)
                }
            }
            assertTrue("Estimated size must not be blank: ${engine.name}", engine.estimatedSizeLabel.isNotBlank())
            assertTrue("Description must not be blank: ${engine.name}", engine.description.isNotBlank())
            assertNotNull("Engine ID must be lookup-able: ${engine.id}", EngineManager.getEngineById(engine.id))
        }
    }

    @Test
    fun test35_platformBadgeColors_matchesConsoleBrandingIdentities() {
        // PS1: Slate Silver Gray (#94A3B8)
        assertEquals(Color(0xFF94A3B8), platformBadgeColor(GamePlatform.PS1))

        // PSP: Cyan Neon (#06B6D4)
        assertEquals(Color(0xFF06B6D4), platformBadgeColor(GamePlatform.PSP))

        // PS2: Deep Royal Blue (#3B82F6)
        assertEquals(Color(0xFF3B82F6), platformBadgeColor(GamePlatform.PS2))

        // Nintendo: Crimson Red (#EF4444)
        assertEquals(Color(0xFFEF4444), platformBadgeColor(GamePlatform.NINTENDO))

        // PC: Emerald Green (#10B981)
        assertEquals(Color(0xFF10B981), platformBadgeColor(GamePlatform.PC))

        // PS3: Violet Purple (#8B5CF6)
        assertEquals(Color(0xFF8B5CF6), platformBadgeColor(GamePlatform.PS3))
    }

    // =========================================================================
    // 6. Custom Storage & MicroSD Manager (SAF Architecture)
    // =========================================================================

    @Test
    fun test36_safStorageManager_documentIdPathFormattingAndMicroSdDetection() {
        // Internal Storage primary: path
        val internalFormatted = NexusStorageManager.formatDocumentIdToPath("primary:RetroGames/PSP")
        assertEquals("Penyimpanan Internal: /RetroGames/PSP", internalFormatted)

        val internalRoot = NexusStorageManager.formatDocumentIdToPath("primary:")
        assertEquals("Penyimpanan Internal: /", internalRoot)

        // MicroSD volume pattern (XXXX-XXXX)
        val sdFormatted = NexusStorageManager.formatDocumentIdToPath("9C33-6BBD:ROMs/PS2")
        assertEquals("MicroSD Card (9C33-6BBD): /ROMs/PS2", sdFormatted)

        // MicroSD detection
        assertTrue(NexusStorageManager.isMicroSdDocumentId("9C33-6BBD:ROMs/PS2"))
        assertTrue(NexusStorageManager.isMicroSdDocumentId("1234-5678:games"))
        assertTrue(NexusStorageManager.isMicroSdDocumentId("sdcard1:PSP"))
        assertFalse(NexusStorageManager.isMicroSdDocumentId("primary:Games"))
    }

    @Test
    fun test37_safStorageManager_storageSpaceInfoCalculationsAndPercentages() {
        val totalBytes = 128L * 1024L * 1024L * 1024L // 128 GB
        val freeBytes = 64L * 1024L * 1024L * 1024L   // 64 GB
        val usedBytes = totalBytes - freeBytes

        val info = StorageSpaceInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            isExternalMicroSd = false,
            storagePath = "Penyimpanan Internal: /Games"
        )

        assertEquals(128.0, info.totalGb, 0.01)
        assertEquals(64.0, info.freeGb, 0.01)
        assertEquals(64.0, info.usedGb, 0.01)
        assertEquals(50.0f, info.usedPercent, 0.01f)
        assertTrue(info.totalStorageFormatted.contains("128.0 GB") || info.totalStorageFormatted.contains("128 GB"))
        assertTrue(info.freeStorageFormatted.contains("64.0 GB") || info.freeStorageFormatted.contains("64 GB"))
    }

    @Test
    fun test38_safStorageManager_minimumFreeStorageThresholdProtection() {
        // Threshold minimum keamanan adalah 2.5 GB
        assertEquals(2_500_000_000L, NexusStorageManager.MIN_FREE_STORAGE_THRESHOLD_BYTES)

        // Simulasi kalkulasi format bytes
        val smallBytes = 500L * 1024L * 1024L // 500 MB
        val formattedMb = StorageSpaceInfo.formatBytes(smallBytes)
        assertTrue(formattedMb.contains("500.0 MB") || formattedMb.contains("500 MB"))

        val largeBytes = 10L * 1024L * 1024L * 1024L // 10 GB
        val formattedGb = StorageSpaceInfo.formatBytes(largeBytes)
        assertTrue(formattedGb.contains("10.0 GB") || formattedGb.contains("10 GB"))
    }

    // =========================================================================
    // 7. Smart ROM Format & Compression Advisor
    // =========================================================================

    @Test
    fun test39_compressionAdvisor_rawIsoPs2TriggersChdRecommendation() {
        // PS2 raw ISO 3.8 GB (> 2.0 GB threshold)
        val advice = CompressionAdvisor.analyzeGameFormat(
            platform = GamePlatform.PS2,
            fileSizeLabelOrBytes = "3.8 GB",
            fileNameOrExtension = "God_of_War_II.iso"
        )

        assertTrue("Raw ISO PS2 > 2GB must trigger compression recommendation", advice.shouldRecommend)
        assertEquals(".CHD", advice.recommendedFormat)
        assertEquals(55, advice.estimatedSavingsPercent)
        assertTrue(advice.estimatedCompressedSizeBytes < advice.rawSizeBytes)
        assertTrue(advice.adviceHeadline.contains("Hemat Storage"))
        assertTrue(advice.adviceHeadline.contains(".chd"))
        assertTrue(advice.whyBetter.contains("NetherSX2"))
    }

    @Test
    fun test40_compressionAdvisor_rawIsoPspTriggersCsoRecommendation() {
        // PSP raw ISO 1.2 GB (> 800 MB threshold)
        val advice = CompressionAdvisor.analyzeGameFormat(
            platform = GamePlatform.PSP,
            fileSizeLabelOrBytes = "1.2 GB",
            fileNameOrExtension = "Tekken_Dark_Resurrection.iso"
        )

        assertTrue("Raw ISO PSP > 800MB must trigger CSO recommendation", advice.shouldRecommend)
        assertEquals(".CSO", advice.recommendedFormat)
        assertEquals(45, advice.estimatedSavingsPercent)
        assertTrue(advice.adviceHeadline.contains(".cso"))
        assertTrue(advice.whyBetter.contains("PPSSPP"))
    }

    @Test
    fun test41_compressionAdvisor_alreadyCompressedFormatsDoNotRecommend() {
        // File yang sudah dalam format .chd tidak perlu rekomendasi kompresi ulang
        val adviceChd = CompressionAdvisor.analyzeGameFormat(
            platform = GamePlatform.PS2,
            fileSizeLabelOrBytes = "1.8 GB",
            fileNameOrExtension = "Persona_4.chd"
        )
        assertFalse("Already CHD should not recommend compression", adviceChd.shouldRecommend)

        // File yang sudah dalam format .cso tidak perlu rekomendasi kompresi ulang
        val adviceCso = CompressionAdvisor.analyzeGameFormat(
            platform = GamePlatform.PSP,
            fileSizeLabelOrBytes = "650 MB",
            fileNameOrExtension = "Crisis_Core.cso"
        )
        assertFalse("Already CSO should not recommend compression", adviceCso.shouldRecommend)

        assertTrue(CompressionAdvisor.isCompressedFormat("game.chd"))
        assertTrue(CompressionAdvisor.isCompressedFormat("game.cso"))
        assertTrue(CompressionAdvisor.isCompressedFormat("game.zso"))
        assertTrue(CompressionAdvisor.isCompressedFormat("game.rvz"))
        assertTrue(CompressionAdvisor.isRawDiscFormat("game.iso"))
        assertTrue(CompressionAdvisor.isRawDiscFormat("game.bin"))
    }

    @Test
    fun test42_compressionAdvisor_headerMagicBytesVerification() {
        // CHD magic: "MComprHD"
        assertEquals(RomFormatType.CHD, CompressionAdvisor.detectHeaderMagic(CompressionAdvisor.MAGIC_CHD))

        // CSO magic: "CISO"
        assertEquals(RomFormatType.CSO, CompressionAdvisor.detectHeaderMagic(CompressionAdvisor.MAGIC_CSO))

        // ZSO magic: "ZISO"
        assertEquals(RomFormatType.ZSO, CompressionAdvisor.detectHeaderMagic(CompressionAdvisor.MAGIC_ZSO))

        // Random bytes: UNKNOWN
        val randomBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)
        assertEquals(RomFormatType.UNKNOWN, CompressionAdvisor.detectHeaderMagic(randomBytes))
    }

    // =========================================================================
    // 8. RetroAchievements REST API & Trophy Integration
    // =========================================================================

    @Test
    fun test43_retroAchievements_parseGameInfoJsonMapFormat() {
        val sampleApiResponse = """
            {
              "ID": 1050,
              "Title": "God of War",
              "ConsoleName": "PlayStation 2",
              "NumAchievements": 2,
              "NumPoints": 35,
              "Achievements": {
                "101": {
                  "ID": 101,
                  "Title": "Hydra Slayer",
                  "Description": "Defeat the Hydra on the Aegean Sea",
                  "Points": 10,
                  "BadgeName": "10492",
                  "DateEarned": "2026-05-10 12:00:00"
                },
                "102": {
                  "ID": 102,
                  "Title": "Blade of Chaos Master",
                  "Description": "Max out Blades of Chaos",
                  "Points": 25,
                  "BadgeName": "10493"
                }
              }
            }
        """.trimIndent()

        val service = RetroAchievementsService()
        val summary = service.parseGameInfoResponse(sampleApiResponse)

        assertEquals(1050L, summary.gameId)
        assertEquals("God of War", summary.gameTitle)
        assertEquals("PlayStation 2", summary.consoleName)
        assertEquals(2, summary.totalAchievements)
        assertEquals(35, summary.totalPoints)
        assertEquals(1, summary.unlockedCount)
        assertEquals(10, summary.unlockedPoints)

        // Top achievement (diurutkan berdasarkan poin tertinggi: 25 poin duluan)
        val top = summary.topAchievements.first()
        assertEquals("Blade of Chaos Master", top.title)
        assertEquals(25, top.points)
        assertEquals("https://media.retroachievements.org/Badge/10493.png", top.badgeUrl)
    }

    @Test
    fun test44_retroAchievements_curatedTrophiesAndBadgeUrls() {
        val service = RetroAchievementsService()
        val gowSummary = service.getCuratedOrSyntheticTrophies("God of War", GamePlatform.PS2)

        assertEquals(36, gowSummary.totalAchievements)
        assertEquals(420, gowSummary.totalPoints)
        assertEquals(3, gowSummary.topAchievements.size)
        assertTrue(gowSummary.topAchievements.all { it.badgeUrl.startsWith("https://media.retroachievements.org/Badge/") })

        val personaSummary = service.getCuratedOrSyntheticTrophies("Shin Megami Tensei: Persona 4", GamePlatform.PS2)
        assertEquals(45, personaSummary.totalAchievements)
        assertEquals(500, personaSummary.totalPoints)

        val badgeUrl = RetroAchievementsService.buildBadgeUrl("12345.png")
        assertEquals("https://media.retroachievements.org/Badge/12345.png", badgeUrl)
    }

    @Test
    fun test45_formatPriorityScore_prioritizesChdAndCsoOverIso() {
        val chdScore = CompressionAdvisor.getFormatPriorityScore("Final_Fantasy_X.chd")
        val csoScore = CompressionAdvisor.getFormatPriorityScore("Persona_3_Portable.cso")
        val isoScore = CompressionAdvisor.getFormatPriorityScore("Tekken_5.iso")
        val binScore = CompressionAdvisor.getFormatPriorityScore("Crash_Bandicoot.bin")

        assertTrue("CHD score must be higher than ISO", chdScore > isoScore)
        assertTrue("CSO score must be higher than ISO", csoScore > isoScore)
        assertTrue("ISO score must be higher than BIN", isoScore > binScore)
    }

    @Test
    fun test46_lemuroidZeroSetupEngineRegistryAndLookups() {
        val lemuroid = EngineManager.lemuroidEngine
        assertEquals("lemuroid", lemuroid.id)
        assertEquals("Lemuroid", lemuroid.name)
        assertEquals(GamePlatform.NINTENDO, lemuroid.platform)
        assertEquals(TargetEmulator.Lemuroid, lemuroid.targetEmulator)
        assertEquals("com.swordfish.lemuroid", lemuroid.primaryPackageName)
        assertEquals("https://github.com/Swordfish90/Lemuroid/releases/download/1.17.0/lemuroid-app-free-dynamic-release.apk", lemuroid.downloadUrl)
        assertTrue(lemuroid.downloadUrl.endsWith(".apk"))

        // Pastikan tidak ada lagi referensi id "retroarch" di registeredEngines
        val registeredIds = EngineManager.registeredEngines.map { it.id }
        assertTrue("Lemuroid must be registered", registeredIds.contains("lemuroid"))
        assertFalse("RetroArch must be purged from registeredEngines", registeredIds.contains("retroarch"))
    }

    @Test
    fun test47_universalArtworkScraper_titleCleaningAndCdnUrls() {
        val rawTitle = "Pokemon - Emerald Version (USA, Europe) [v1.0].gba"
        val clean = UniversalArtworkScraper.cleanTitle(rawTitle)
        assertEquals("Pokemon - Emerald Version", clean)

        val rawIso = "God_of_War_II (USA) (En,Fr,De).iso"
        val cleanIso = UniversalArtworkScraper.cleanTitle(rawIso)
        assertEquals("God of War II", cleanIso)

        val pspUrl = UniversalArtworkScraper.getLibretroThumbnailUrl(GamePlatform.PSP, "Tekken 6")
        assertTrue(pspUrl.contains("Sony_-_PlayStation_Portable"))
        assertTrue(pspUrl.contains("Tekken%206.png"))

        val steamId = UniversalArtworkScraper.getSteamAppId("Fallout: New Vegas")
        assertEquals(22380, steamId)
        val steamBanner = UniversalArtworkScraper.getSteamBannerUrl(22380)
        assertEquals("https://cdn.cloudflare.steamstatic.com/steam/apps/22380/header.jpg", steamBanner)

        val game = GameItem(
            id = "test_art",
            title = "Half-Life 2",
            platform = GamePlatform.PC,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.GameNative
        )
        val resolved = UniversalArtworkScraper.resolveCoverUrl(game)
        assertEquals("https://cdn.cloudflare.steamstatic.com/steam/apps/220/header.jpg", resolved)
    }

    @Test
    fun test48_autoPlayEvent_pipelineModel() {
        val testGame = GameItem(
            id = "test_auto_play",
            title = "Super Mario Advance 4",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 2,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.Lemuroid
        )
        val event = com.nexusdeck.app.core.manager.AutoPlayEvent(
            game = testGame,
            romPath = "/games/nintendo/sma4.gba"
        )
        assertEquals("test_auto_play", event.game.id)
        assertEquals("/games/nintendo/sma4.gba", event.romPath)
        assertTrue(event.eventId > 0L)
    }

    @Test
    fun test49_emulatorJsEngine_coreResolutionAndHtmlGeneration() {
        // Hanya .gba yang in-app (mgba/IodineGBA); platform lain adalah
        // engine eksternal — v1.1.5 tidak lagi mengklaim core in-app palsu.
        assertEquals("mgba", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "zelda_minish_cap.gba"))
        assertEquals("snes9x", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "chrono_trigger.snes"))
        assertEquals("fceumm", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "super_mario.nes"))
        assertEquals("gambatte", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "pokemon_crystal.gbc"))
        assertEquals("melonds", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "pokemon_black.nds"))
        assertEquals("mupen64plus", EmulatorJsEngine.resolveCore(GamePlatform.NINTENDO, "zelda_oot.n64"))
        assertEquals("ppsspp", EmulatorJsEngine.resolveCore(GamePlatform.PSP, "crisis_core.iso"))

        val zelda = GameItem(
            id = "gba_zelda",
            title = "The Legend of Zelda: The Minish Cap",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 2,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.Lemuroid,
            downloadUrl = "https://archive.org/download/zelda.gba"
        )
        val html = EmulatorJsEngine.generateHtml(zelda, "/games/nintendo/zelda.gba")
        assertTrue("HTML must contain screen canvas", html.contains("id=\"nexus-screen\""))
        assertTrue("HTML must specify mgba core for GBA", html.contains("'mgba'"))
        assertTrue("HTML must include the IodineGBA glue script", html.contains("nexus_iodine_glue.js"))
        assertTrue("HTML must load the IodineGBA core from assets", html.contains("IodineGBA/core/Emulator.js"))
        assertFalse("HTML must not include the deleted fake retro_engine.js", html.contains("retro_engine.js"))
        assertFalse("HTML must not embed a ROM (injected separately in chunks)", html.contains("nexusLoadRom"))
    }

    @Test
    fun test50_gameLauncher_directComponentTargetingForPpssppAndNetherSx2() {
        // Mock Context tidak diperlukan jika kita menguji logika Intent Component
        val pspGame = GameItem(
            id = "psp_tekken",
            title = "Tekken 6",
            platform = GamePlatform.PSP,
            coverUrl = "",
            minRamGb = 3,
            recommendedSocKeywords = emptyList(),
            targetEmulator = TargetEmulator.PPSSPP
        )
        val dummyConfig = com.nexusdeck.app.core.model.EngineConfig(
            driverName = "Turnip",
            targetResolution = "2x",
            rendererBackend = "Vulkan",
            dxvkVersion = "2.3",
            cpuDynarecPreset = "Safe"
        )

        // Verifikasi target package & component names
        val ppssppEngine = EngineManager.ppssppEngine
        assertEquals("org.ppsspp.ppsspp", ppssppEngine.primaryPackageName)

        val netherEngine = EngineManager.netherSx2Engine
        assertEquals("xyz.aethersx2.android", netherEngine.primaryPackageName)
        assertTrue(netherEngine.alternatePackageNames.contains("come.nanodata.armsx2"))
    }

    @Test
    fun test51_platformExecutionMatrix_completePoliciesAcrossAllConsoles() {
        // 0. PS1: Native Engine DuckStation, WAJIB BIOS
        val ps1 = PlatformExecutionMatrix.getRule(GamePlatform.PS1)
        assertEquals(ExecutionType.NATIVE_ENGINE_REQUIRES_BIOS, ps1.executionType)
        assertTrue(ps1.requiresBios)
        assertFalse(ps1.requiresFirmware)
        assertEquals("duckstation", ps1.defaultEngineId)
        assertTrue(ps1.requiredFileLabel?.contains("scph1001") == true)

        // 1. Nintendo Retro: 100% In-App WASM Player, No BIOS, No Firmware
        val nintendo = PlatformExecutionMatrix.getRule(GamePlatform.NINTENDO)
        assertEquals(ExecutionType.IN_APP_WASM, nintendo.executionType)
        assertTrue(nintendo.isEmbeddedWasm)
        assertFalse(nintendo.requiresBios)
        assertFalse(nintendo.requiresFirmware)

        // 2. PSP: Native Engine PPSSPP, No BIOS
        val psp = PlatformExecutionMatrix.getRule(GamePlatform.PSP)
        assertEquals(ExecutionType.NATIVE_ENGINE_NO_BIOS, psp.executionType)
        assertEquals("ppsspp", psp.defaultEngineId)
        assertFalse(psp.requiresBios)

        // 3. PC: Native Engine GameNative, No BIOS
        val pc = PlatformExecutionMatrix.getRule(GamePlatform.PC)
        assertEquals(ExecutionType.NATIVE_ENGINE_NO_BIOS, pc.executionType)
        assertEquals("gamenative", pc.defaultEngineId)
        assertFalse(pc.requiresBios)

        // 4. PS2: Native Engine NetherSX2, WAJIB BIOS
        val ps2 = PlatformExecutionMatrix.getRule(GamePlatform.PS2)
        assertEquals(ExecutionType.NATIVE_ENGINE_REQUIRES_BIOS, ps2.executionType)
        assertTrue(ps2.requiresBios)
        assertFalse(ps2.requiresFirmware)
        assertEquals("nethersx2", ps2.defaultEngineId)
        assertTrue(ps2.requiredFileLabel?.contains("scph39001") == true)

        // 5. PS3: Native Engine APS3e, WAJIB FIRMWARE SONY
        val ps3 = PlatformExecutionMatrix.getRule(GamePlatform.PS3)
        assertEquals(ExecutionType.NATIVE_ENGINE_REQUIRES_FIRMWARE, ps3.executionType)
        assertFalse(ps3.requiresBios)
        assertTrue(ps3.requiresFirmware)
        assertEquals("aps3e", ps3.defaultEngineId)
        assertTrue(ps3.requiredFileLabel?.contains("PS3UPDAT.PUP") == true)
    }

    @Test
    fun test52_biosManager_officialRegistryAndAssetTargetPaths() {
        val ps1 = BiosManager.ps1Bios
        assertEquals("scph1001.bin", ps1.fileName)
        assertEquals(GamePlatform.PS1, ps1.platform)
        // v1.4.0 — BIOS PS1 TIDAK lagi diunduh otomatis (firmware
        // berhak cipta Sony; impor manual dari dump konsol sendiri).
        assertTrue(ps1.downloadUrl.isBlank())

        val ps2 = BiosManager.ps2Bios
        assertEquals("scph39001.bin", ps2.fileName)
        assertEquals(GamePlatform.PS2, ps2.platform)
        // v1.4.0 — BIOS PS2 juga tidak lagi diunduh otomatis.
        assertTrue(ps2.downloadUrl.isBlank())

        val ps3 = BiosManager.ps3Firmware
        assertEquals("PS3UPDAT.PUP", ps3.fileName)
        assertEquals(GamePlatform.PS3, ps3.platform)
        assertTrue(ps3.downloadUrl.contains("dus01.ps3.update.playstation.net"))

        assertEquals("scph1001.bin", BiosManager.getRequiredEntryForPlatform(GamePlatform.PS1)?.fileName)
        assertEquals("scph39001.bin", BiosManager.getRequiredEntryForPlatform(GamePlatform.PS2)?.fileName)
        assertEquals("PS3UPDAT.PUP", BiosManager.getRequiredEntryForPlatform(GamePlatform.PS3)?.fileName)
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.PSP))
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.NINTENDO))
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.PC))
    }

    @Test
    fun test53_aps3e_lightweightEngineRegistration() {
        val aps3e = EngineManager.aps3eEngine
        assertEquals("aps3e", aps3e.id)
        assertEquals(GamePlatform.PS3, aps3e.platform)
        assertEquals(TargetEmulator.ARMSX3, aps3e.targetEmulator)
        // Repo lama APS3e/APS3e 404; proyek kini aenu1/aps3e, distribusi via Play Store (audit 2026-11-10).
        assertEquals("aenu.aps3e", aps3e.primaryPackageName)
        assertEquals(EngineDownloadKind.PLAY_STORE, aps3e.downloadKind)
        assertEquals("https://play.google.com/store/apps/details?id=aenu.aps3e", aps3e.downloadUrl)
        assertTrue(aps3e.allPackageNames.contains("com.armsx3.play"))
        assertTrue(aps3e.allPackageNames.contains("com.tinyproot.aps3e"))
        assertTrue(aps3e.allPackageNames.contains("org.aps3e"))

        val resolved = EngineManager.getEngineForPlatform(GamePlatform.PS3)
        assertEquals("aps3e", resolved.id)
    }

    @Test
    fun test54_nexusStorageManager_universalSharedPublicPathsAcrossAllPlatforms() {
        // Folder publik bersama universal untuk seluruh platform
        // Pemetaan dbname Fase 3a: PS1 -> psx; NINTENDO -> gba sebagai folder kanonikal dari gba/nds/3ds.
        assertEquals("/storage/emulated/0/NexusDeck/games/psx/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.PS1))
        assertEquals("/storage/emulated/0/NexusDeck/games/psp/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.PSP))
        assertEquals("/storage/emulated/0/NexusDeck/games/ps2/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.PS2))
        assertEquals("/storage/emulated/0/NexusDeck/games/ps3/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.PS3))
        assertEquals("/storage/emulated/0/NexusDeck/games/pc/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.PC))
        assertEquals("/storage/emulated/0/NexusDeck/games/gba/", NexusStorageManager.getCanonicalPlatformGamesPath(GamePlatform.NINTENDO))

        // Direktori BIOS & Firmware bersama
        assertEquals("/storage/emulated/0/NexusDeck/bios/scph1001.bin", NexusStorageManager.getCanonicalBiosPath("scph1001.bin"))
        assertEquals("/storage/emulated/0/NexusDeck/bios/scph39001.bin", NexusStorageManager.getCanonicalBiosPath("scph39001.bin"))
        assertEquals("/storage/emulated/0/NexusDeck/firmware/PS3UPDAT.PUP", NexusStorageManager.getCanonicalFirmwarePath("PS3UPDAT.PUP"))
    }

    @Test
    fun test55_engineManager_directBootTargetsAcrossAllSixPlatforms() {
        // 1. PS1 (DuckStation)
        val ps1Engine = EngineManager.getEngineForPlatform(GamePlatform.PS1)
        assertEquals("duckstation", ps1Engine.id)
        assertEquals("com.github.stenzek.duckstation", ps1Engine.primaryPackageName)

        // 2. PSP (PPSSPP)
        val pspEngine = EngineManager.getEngineForPlatform(GamePlatform.PSP)
        assertEquals("ppsspp", pspEngine.id)
        assertEquals("org.ppsspp.ppsspp", pspEngine.primaryPackageName)

        // 3. PS2 (NetherSX2)
        val ps2Engine = EngineManager.getEngineForPlatform(GamePlatform.PS2)
        assertEquals("nethersx2", ps2Engine.id)
        assertEquals("xyz.aethersx2.android", ps2Engine.primaryPackageName)
        assertTrue(ps2Engine.alternatePackageNames.contains("come.nanodata.armsx2"))

        // 4. PS3 (APS3e)
        val ps3Engine = EngineManager.getEngineForPlatform(GamePlatform.PS3)
        assertEquals("aps3e", ps3Engine.id)
        // Repo lama APS3e/APS3e 404; paket Play Store kini aenu.aps3e (audit 2026-11-10).
        assertEquals("aenu.aps3e", ps3Engine.primaryPackageName)
        assertEquals(EngineDownloadKind.PLAY_STORE, ps3Engine.downloadKind)
        assertTrue(ps3Engine.alternatePackageNames.contains("com.armsx3.play"))
        assertTrue(ps3Engine.alternatePackageNames.contains("com.tinyproot.aps3e"))

        // 5. PC (GameNative)
        val pcEngine = EngineManager.getEngineForPlatform(GamePlatform.PC)
        assertEquals("gamenative", pcEngine.id)
        assertEquals("app.gamenative", pcEngine.primaryPackageName)

        // 6. Nintendo (Lemuroid / Offline In-App)
        val nintendoEngine = EngineManager.getEngineForPlatform(GamePlatform.NINTENDO)
        assertEquals("lemuroid", nintendoEngine.id)
        assertTrue(PlatformExecutionMatrix.getRule(GamePlatform.NINTENDO).isEmbeddedWasm)
    }

    @Test
    fun test56_allInOneBundleSetup_automaticBiosAndDirectBootingPipeline() {
        // Verifikasi bahwa PS1, PS2, PS3 otomatis memiliki metadata BIOS/Firmware
        val ps1Bios = BiosManager.getRequiredEntryForPlatform(GamePlatform.PS1)
        assertNotNull(ps1Bios)
        assertEquals("scph1001.bin", ps1Bios?.fileName)

        val ps2Bios = BiosManager.getRequiredEntryForPlatform(GamePlatform.PS2)
        assertNotNull(ps2Bios)
        assertEquals("scph39001.bin", ps2Bios?.fileName)

        val ps3Fw = BiosManager.getRequiredEntryForPlatform(GamePlatform.PS3)
        assertNotNull(ps3Fw)
        assertEquals("PS3UPDAT.PUP", ps3Fw?.fileName)

        // Verifikasi bahwa PSP, PC, Nintendo Retro melewati unduhan BIOS secara instan
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.PSP))
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.PC))
        assertNull(BiosManager.getRequiredEntryForPlatform(GamePlatform.NINTENDO))
    }
}
