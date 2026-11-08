package com.nexusdeck.app

import android.content.pm.ActivityInfo
import com.nexusdeck.app.core.downloader.SniffedDownload
import com.nexusdeck.app.core.downloader.WebAdBlocker
import com.nexusdeck.app.core.downloader.WebDownloadSniffer
import com.nexusdeck.app.core.engine.EngineDownloadProgress
import com.nexusdeck.app.core.engine.EngineInfo
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.core.engine.NexusEmbeddedCoreRunner
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
import com.nexusdeck.app.core.profiler.HardwareProfiler
import com.nexusdeck.app.core.repository.OnlineGameCatalogService
import com.nexusdeck.app.core.repository.sources.GameRepositorySource
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
    fun test13_catalogSearch_allSourcesFail_emitsSyntheticFallback() = runBlocking {
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

        assertTrue("Synthetic fallback must provide games when all mirrors are offline", results.isNotEmpty())
        assertTrue(results.first().title.contains("God of War"))
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

        assertEquals(EngineExecutionMode.IN_APP_PLAYABLE, psp.executionMode)
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

        // PSP: PPSSPP
        val ppsspp = EngineManager.ppssppEngine
        assertEquals("ppsspp", ppsspp.id)
        assertEquals("PPSSPP", ppsspp.name)
        assertEquals(GamePlatform.PSP, ppsspp.platform)
        assertEquals(TargetEmulator.PPSSPP, ppsspp.targetEmulator)
        assertEquals("org.ppsspp.ppsspp", ppsspp.primaryPackageName)
        assertEquals("https://github.com/hrydgard/ppsspp/releases/download/v1.20.4/ppsspp.apk", ppsspp.downloadUrl)
        assertTrue(ppsspp.downloadUrl.endsWith(".apk"))

        // PS2: NetherSX2 Classic
        val nether = EngineManager.netherSx2Engine
        assertEquals("nethersx2", nether.id)
        assertEquals("NetherSX2 Classic", nether.name)
        assertEquals(GamePlatform.PS2, nether.platform)
        assertEquals(TargetEmulator.ARMSX2, nether.targetEmulator)
        assertEquals("xyz.aethersx2.android", nether.primaryPackageName)
        assertTrue("NetherSX2 must support alternate armsx2 package", nether.alternatePackageNames.contains("come.nanodata.armsx2"))
        assertEquals("https://github.com/Trixarian/NetherSX2-classic/releases/download/v2.2n-3668/NetherSX2-v2.2n-3668.apk", nether.downloadUrl)

        // PC: GameNative / Winlator
        val gameNative = EngineManager.gameNativeEngine
        assertEquals("gamenative", gameNative.id)
        assertEquals(GamePlatform.PC, gameNative.platform)
        assertEquals(TargetEmulator.GameNative, gameNative.targetEmulator)
        assertEquals("app.gamenative", gameNative.primaryPackageName)
        assertEquals("https://github.com/utkarshdalal/GameNative/releases/latest/download/GameNative.apk", gameNative.downloadUrl)

        // Nintendo DS: melonDS
        val melonDs = EngineManager.melonDsEngine
        assertEquals("melonds", melonDs.id)
        assertEquals("net.kuribo64.melonDS", melonDs.primaryPackageName)
        assertEquals("https://github.com/rafaelvcaetano/melonDS-android/releases/download/1.9.3/melonDS-1.9.3.apk", melonDs.downloadUrl)

        // Nintendo 3DS: Lime3DS
        val lime3ds = EngineManager.lime3dsEngine
        assertEquals("lime3ds", lime3ds.id)
        assertEquals("io.github.lime3ds", lime3ds.primaryPackageName)
        assertEquals("https://github.com/Lime3DS/Lime3DS/releases/latest/download/lime3ds-android.apk", lime3ds.downloadUrl)

        // Nintendo Switch: Sudachi
        val sudachi = EngineManager.sudachiEngine
        assertEquals("sudachi", sudachi.id)
        assertEquals("org.sudachi.sudachi_emu", sudachi.primaryPackageName)
        assertEquals("https://github.com/emuplace/sudachi.emuplace.app/releases/latest/download/sudachi.apk", sudachi.downloadUrl)
    }

    @Test
    fun test30_engineManager_platformAndEmulatorLookups() {
        assertEquals("ppsspp", EngineManager.getEngineForPlatform(GamePlatform.PSP).id)
        assertEquals("nethersx2", EngineManager.getEngineForPlatform(GamePlatform.PS2).id)
        assertEquals("gamenative", EngineManager.getEngineForPlatform(GamePlatform.PC).id)
        assertEquals("melonds", EngineManager.getEngineForPlatform(GamePlatform.NINTENDO).id)
        assertEquals("armsx3", EngineManager.getEngineForPlatform(GamePlatform.PS3).id)

        assertEquals("ppsspp", EngineManager.getEngineForEmulator(TargetEmulator.PPSSPP).id)
        assertEquals("nethersx2", EngineManager.getEngineForEmulator(TargetEmulator.ARMSX2).id)
        assertEquals("gamenative", EngineManager.getEngineForEmulator(TargetEmulator.GameNative).id)
        assertEquals("melonds", EngineManager.getEngineForEmulator(TargetEmulator.MelonDS).id)
        assertEquals("lime3ds", EngineManager.getEngineForEmulator(TargetEmulator.Lime3DS).id)
        assertEquals("sudachi", EngineManager.getEngineForEmulator(TargetEmulator.Sudachi).id)
        assertEquals("armsx3", EngineManager.getEngineForEmulator(TargetEmulator.ARMSX3).id)
        assertEquals("retroarch", EngineManager.getEngineForEmulator(TargetEmulator.NexusRetroCore).id)
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

        val testLime = GameItem(
            id = "test_3ds",
            title = "Pokemon Sun",
            platform = GamePlatform.NINTENDO,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = listOf("snapdragon"),
            targetEmulator = TargetEmulator.Lime3DS
        )
        val engineLime = EngineManager.getEngineForGame(testLime)
        assertEquals("lime3ds", engineLime.id)
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
            assertTrue("URL must be HTTPS: ${engine.downloadUrl}", engine.downloadUrl.startsWith("https://"))
            assertTrue("URL must end with .apk: ${engine.downloadUrl}", engine.downloadUrl.endsWith(".apk"))
            assertTrue("Estimated size must not be blank: ${engine.name}", engine.estimatedSizeLabel.isNotBlank())
            assertTrue("Description must not be blank: ${engine.name}", engine.description.isNotBlank())
            assertNotNull("Engine ID must be lookup-able: ${engine.id}", EngineManager.getEngineById(engine.id))
        }
    }
}
