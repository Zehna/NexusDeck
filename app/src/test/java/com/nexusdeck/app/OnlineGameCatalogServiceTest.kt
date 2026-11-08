package com.nexusdeck.app

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.repository.OnlineGameCatalogService
import com.nexusdeck.app.core.repository.sources.ArchiveOrgSource
import com.nexusdeck.app.core.repository.sources.FreewarePcSource
import com.nexusdeck.app.core.repository.sources.GameRepositorySource
import com.nexusdeck.app.core.repository.sources.MyrientMirrorSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineGameCatalogServiceTest {

    @Test
    fun testArchiveOrgSource_jsonParsingAndUrlGeneration() {
        val source = ArchiveOrgSource()

        val sampleJson = """
            {
              "response": {
                "numFound": 1,
                "start": 0,
                "docs": [
                  {
                    "identifier": "god-of-war-ghost-of-sparta-usa",
                    "title": "God of War: Ghost of Sparta (USA) (v1.01)",
                    "item_size": 1200000000,
                    "subject": ["PSP", "Action", "Sony"]
                  }
                ]
              }
            }
        """.trimIndent()

        val items = source.parseArchiveJsonResponse(sampleJson, GamePlatform.PSP)

        assertEquals(1, items.size)
        val game = items[0]
        assertEquals("God of War: Ghost of Sparta", game.title)
        assertEquals(GamePlatform.PSP, game.platform)
        assertEquals("1.12 GB", game.fileSizeLabel)
        assertEquals("https://archive.org/services/img/god-of-war-ghost-of-sparta-usa", game.coverUrl)
        assertEquals("https://archive.org/compress/god-of-war-ghost-of-sparta-usa", game.downloadUrl)
        assertTrue(game.isWebResult)
    }

    @Test
    fun testMyrientMirrorSource_nintendoAndPspFiltering() = runBlocking {
        val myrientSource = MyrientMirrorSource()

        val nintendoItems = myrientSource.getPopular(platform = GamePlatform.NINTENDO)
        assertTrue("Nintendo items should not be empty", nintendoItems.isNotEmpty())
        assertTrue("All items must be Nintendo platform", nintendoItems.all { it.platform == GamePlatform.NINTENDO })

        val pokemonSearch = myrientSource.search(query = "Pokemon", platform = GamePlatform.NINTENDO, page = 1)
        assertTrue(pokemonSearch.any { it.title.contains("Pokemon", ignoreCase = true) })
    }

    @Test
    fun testFreewarePcSource_onlyServesPcPlatform() = runBlocking {
        val freewareSource = FreewarePcSource()

        val ps2Results = freewareSource.search("Doom", GamePlatform.PS2, page = 1)
        assertTrue("Freeware PC source should return empty for PS2", ps2Results.isEmpty())

        val pcResults = freewareSource.search("Freedoom", GamePlatform.PC, page = 1)
        assertFalse("Freeware PC source should find Freedoom for PC", pcResults.isEmpty())
        assertEquals(GamePlatform.PC, pcResults.first().platform)
        assertTrue(pcResults.first().downloadUrl.startsWith("https://"))
    }

    @Test
    fun testCatalogService_automaticFailoverWhenSourceFails() = runBlocking {
        val failingSource = object : GameRepositorySource {
            override val sourceName: String = "Faulty Server"
            override val priority: Int = 1
            override fun supportsPlatform(platform: GamePlatform?): Boolean = true
            override suspend fun search(query: String, platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw RuntimeException("Simulated HTTP 503 Service Unavailable")
            }
            override suspend fun getPopular(platform: GamePlatform?, page: Int, limit: Int): List<GameItem> {
                throw RuntimeException("Simulated Network Timeout")
            }
        }

        val backupSource = MyrientMirrorSource()

        val serviceWithFailover = OnlineGameCatalogService(
            sources = listOf(failingSource, backupSource)
        )

        // When searching, even though source 1 threw an exception, source 2 (backupSource) should provide results
        val results = serviceWithFailover.searchOnlineGames(
            query = "Pokemon",
            platform = GamePlatform.NINTENDO,
            page = 1
        ).first()

        assertTrue("Failover must provide results even when primary source fails", results.isNotEmpty())
        assertTrue(results.any { it.title.contains("Pokemon", ignoreCase = true) })
    }
}
