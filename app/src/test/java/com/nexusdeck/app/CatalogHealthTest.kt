package com.nexusdeck.app

import com.nexusdeck.app.core.catalog.health.CatalogGame
import com.nexusdeck.app.core.catalog.health.CatalogHealthSnapshot
import com.nexusdeck.app.core.catalog.health.LinkHealth
import com.nexusdeck.app.core.catalog.health.LinkStatus
import com.nexusdeck.app.core.catalog.health.filterVisibleGames
import com.nexusdeck.app.core.catalog.health.formatLastChecked
import com.nexusdeck.app.core.catalog.health.isStale
import com.nexusdeck.app.core.catalog.health.mergeHealth
import com.nexusdeck.app.core.catalog.health.toVerdict
import com.nexusdeck.app.core.downloader.LinkCheckResult
import com.nexusdeck.app.core.downloader.LinkVerifier.LinkVerdict
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for v1.1.9 self-selecting catalog (LinkHealth): the hysteresis
 * merge rule, TTLs, the default-hide filter, and the relative-time text.
 * All tests here are pure/offline — no Android Context, no network.
 */
class CatalogHealthTest {

    private val nowMs = 1_792_000_000_000L // fixed "now" for stable TTL math

    private fun game(id: String) = GameItem(
        id = id,
        title = "Game $id",
        platform = GamePlatform.PS1,
        coverUrl = "",
        minRamGb = 2,
        recommendedSocKeywords = listOf("snapdragon_8"),
        targetEmulator = TargetEmulator.DuckStation,
        downloadUrl = "https://example.com/files/$id.chd"
    )

    private fun health(
        gameId: String,
        status: LinkStatus,
        checkedAt: Long,
        failures: Int = 0,
        lastAliveAt: Long? = null
    ) = LinkHealth(
        gameId = gameId,
        status = status,
        httpStatus = 200,
        checkedAtEpochMs = checkedAt,
        lastAliveAtEpochMs = lastAliveAt,
        consecutiveFailures = failures,
        finalUrl = null,
        reason = null
    )

    private val alive = LinkVerdict.Alive(200, 24_121_671L, "https://example.com/files/a.chd")
    private val dead404 = LinkVerdict.Dead(404, "http-404", "https://example.com/files/a.chd")
    private val deadHtml = LinkVerdict.Dead(200, "html-landing", "https://example.com/files/a.chd")
    private val unknown503 = LinkVerdict.Unknown(503, "http-503", 120L)
    private val unknownTimeout = LinkVerdict.Unknown(-1, "tidak-terjangkau", null)

    // ---------- mergeHealth: hysteresis ----------

    @Test
    fun testMerge_firstAlive_becomesAlive() {
        val merged = mergeHealth(null, "a", alive, nowMs)
        assertEquals(LinkStatus.ALIVE, merged.status)
        assertEquals(0, merged.consecutiveFailures)
        assertEquals(nowMs, merged.lastAliveAtEpochMs)
        assertEquals(nowMs, merged.checkedAtEpochMs)
    }

    @Test
    fun testMerge_firstHardFailure_doesNotHideYet() {
        // Satu 404 pada game yang belum pernah diperiksa: kegagalan
        // tercatat, tapi status tetap NOT_CHECKED — belum disembunyikan.
        val merged = mergeHealth(null, "a", dead404, nowMs)
        assertEquals(LinkStatus.NOT_CHECKED, merged.status)
        assertEquals(1, merged.consecutiveFailures)
    }

    @Test
    fun testMerge_secondHardFailure_hides() {
        val first = mergeHealth(null, "a", dead404, nowMs)
        val second = mergeHealth(first, "a", dead404, nowMs + 60_000L)
        assertEquals(LinkStatus.DEAD, second.status)
        assertEquals(2, second.consecutiveFailures)
        assertEquals("http-404", second.reason)
    }

    @Test
    fun testMerge_aliveResetsFailures() {
        val failed = mergeHealth(null, "a", dead404, nowMs)
        val revived = mergeHealth(failed, "a", alive, nowMs + 60_000L)
        assertEquals(LinkStatus.ALIVE, revived.status)
        assertEquals(0, revived.consecutiveFailures)
    }

    @Test
    fun testMerge_unknownNeverChangesAlive() {
        val base = health("a", LinkStatus.ALIVE, nowMs - 1_000L, lastAliveAt = nowMs - 1_000L)
        val merged = mergeHealth(base, "a", unknown503, nowMs)
        assertEquals(LinkStatus.ALIVE, merged.status)
        assertEquals(0, merged.consecutiveFailures)
        assertEquals(nowMs - 1_000L, merged.lastAliveAtEpochMs)
        assertEquals(nowMs, merged.checkedAtEpochMs)
        assertEquals("http-503", merged.reason)
    }

    @Test
    fun testMerge_unknownNeverChangesDead() {
        val base = health("a", LinkStatus.DEAD, nowMs - 1_000L, failures = 2)
        val merged = mergeHealth(base, "a", unknownTimeout, nowMs)
        assertEquals(LinkStatus.DEAD, merged.status)
        assertEquals(2, merged.consecutiveFailures)
    }

    @Test
    fun testMerge_htmlLandingCountsAsHardFailure() {
        val first = mergeHealth(null, "a", deadHtml, nowMs)
        assertEquals(1, first.consecutiveFailures)
        val second = mergeHealth(first, "a", deadHtml, nowMs + 60_000L)
        assertEquals(LinkStatus.DEAD, second.status)
    }

    @Test
    fun testMerge_deadThenAlive_restores() {
        val dead = health("a", LinkStatus.DEAD, nowMs - 1_000L, failures = 2)
        val revived = mergeHealth(dead, "a", alive, nowMs)
        assertEquals(LinkStatus.ALIVE, revived.status)
        assertEquals(0, revived.consecutiveFailures)
        assertEquals(nowMs, revived.lastAliveAtEpochMs)
    }

    // ---------- isStale: TTL ----------

    @Test
    fun testIsStale_notCheckedAlwaysStale() {
        assertTrue(null.isStale(nowMs))
        assertTrue(health("a", LinkStatus.NOT_CHECKED, 0L).isStale(nowMs))
    }

    @Test
    fun testIsStale_alive24h() {
        assertFalse(health("a", LinkStatus.ALIVE, nowMs - 60_000L).isStale(nowMs))
        assertFalse(health("a", LinkStatus.ALIVE, nowMs - 23L * 3_600_000L).isStale(nowMs))
        assertTrue(health("a", LinkStatus.ALIVE, nowMs - 25L * 3_600_000L).isStale(nowMs))
    }

    @Test
    fun testIsStale_dead24h() {
        assertFalse(health("a", LinkStatus.DEAD, nowMs - 60_000L).isStale(nowMs))
        assertTrue(health("a", LinkStatus.DEAD, nowMs - 25L * 3_600_000L).isStale(nowMs))
    }

    @Test
    fun testIsStale_unknown1h() {
        assertFalse(health("a", LinkStatus.UNKNOWN, nowMs - 30L * 60_000L).isStale(nowMs))
        assertTrue(health("a", LinkStatus.UNKNOWN, nowMs - 2L * 3_600_000L).isStale(nowMs))
    }

    // ---------- filterVisibleGames ----------

    @Test
    fun testFilter_hidesDeadByDefault() {
        val games = listOf(game("alive"), game("dead"), game("unknown"), game("new"))
        val snapshot = CatalogHealthSnapshot(
            entries = mapOf(
                "alive" to health("alive", LinkStatus.ALIVE, nowMs),
                "dead" to health("dead", LinkStatus.DEAD, nowMs, failures = 2),
                "unknown" to health("unknown", LinkStatus.UNKNOWN, nowMs)
            )
        )
        val visible = filterVisibleGames(games, snapshot, showUnavailable = false)
        assertEquals(listOf("alive", "unknown", "new"), visible.map { it.id })
    }

    @Test
    fun testFilter_showUnavailableShowsAll() {
        val games = listOf(game("alive"), game("dead"))
        val snapshot = CatalogHealthSnapshot(
            entries = mapOf("dead" to health("dead", LinkStatus.DEAD, nowMs, failures = 2))
        )
        val visible = filterVisibleGames(games, snapshot, showUnavailable = true)
        assertEquals(listOf("alive", "dead"), visible.map { it.id })
    }

    @Test
    fun testFilter_noSnapshotShowsAll() {
        val games = listOf(game("a"), game("b"))
        assertEquals(games, filterVisibleGames(games, null, showUnavailable = false))
    }

    // ---------- formatLastChecked ----------

    @Test
    fun testFormatLastChecked() {
        assertEquals("belum pernah diperiksa", formatLastChecked(null, nowMs))
        assertEquals("belum pernah diperiksa", formatLastChecked(0L, nowMs))
        assertEquals("baru saja", formatLastChecked(nowMs, nowMs))
        assertEquals("baru saja", formatLastChecked(nowMs - 30_000L, nowMs))
        assertEquals("5 mnt lalu", formatLastChecked(nowMs - 5L * 60_000L, nowMs))
        assertEquals("2 jam lalu", formatLastChecked(nowMs - 2L * 3_600_000L, nowMs))
        assertEquals("3 hari lalu", formatLastChecked(nowMs - 3L * 86_400_000L, nowMs))
    }

    // ---------- LinkCheckResult.toVerdict (download gate -> snapshot) ----------

    @Test
    fun testToVerdict_okIsAlive() {
        val result = LinkCheckResult(
            ok = true,
            httpStatus = 200,
            contentType = "application/octet-stream",
            contentLength = 24_121_671L,
            finalUrl = "https://example.com/files/a.chd",
            reason = "Tautan terverifikasi."
        )
        val verdict = result.toVerdict()
        assertTrue(verdict is LinkVerdict.Alive)
        assertEquals(24_121_671L, (verdict as LinkVerdict.Alive).contentLength)
    }

    @Test
    fun testToVerdict_404isDead() {
        val result = LinkCheckResult(
            ok = false,
            httpStatus = 404,
            contentType = "text/html",
            contentLength = 1024L,
            finalUrl = "https://example.com/files/a.chd",
            reason = "Tautan mati (HTTP 404)."
        )
        val verdict = result.toVerdict()
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("http-404", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testToVerdict_htmlReasonIsDead() {
        val result = LinkCheckResult(
            ok = false,
            httpStatus = 200,
            contentType = "text/html",
            contentLength = 2048L,
            finalUrl = "https://example.com/files/a.zip",
            reason = "Server mengembalikan halaman web, bukan file game biner."
        )
        val verdict = result.toVerdict()
        assertTrue(verdict is LinkVerdict.Dead)
        assertEquals("html-landing", (verdict as LinkVerdict.Dead).reason)
    }

    @Test
    fun testToVerdict_503isUnknown() {
        val result = LinkCheckResult(
            ok = false,
            httpStatus = 503,
            contentType = "text/html",
            contentLength = 1024L,
            finalUrl = "https://example.com/files/a.chd",
            reason = "Tautan mati (HTTP 503)."
        )
        assertTrue(result.toVerdict() is LinkVerdict.Unknown)
    }

    @Test
    fun testToVerdict_unreachableIsUnknown() {
        val result = LinkCheckResult(
            ok = false,
            httpStatus = -1,
            contentType = null,
            contentLength = -1L,
            finalUrl = "https://example.com/files/a.chd",
            reason = "Tidak dapat menjangkau server unduhan."
        )
        val verdict = result.toVerdict()
        assertTrue(verdict is LinkVerdict.Unknown)
        assertNull((verdict as LinkVerdict.Unknown).httpStatus)
    }

    // ---------- CatalogGame wrapper compiles with a health entry ----------

    @Test
    fun testCatalogGame_wrapper() {
        val wrapper = CatalogGame(
            game = game("a"),
            health = health("a", LinkStatus.ALIVE, nowMs)
        )
        assertEquals("a", wrapper.game.id)
        assertEquals(LinkStatus.ALIVE, wrapper.health.status)
    }
}
