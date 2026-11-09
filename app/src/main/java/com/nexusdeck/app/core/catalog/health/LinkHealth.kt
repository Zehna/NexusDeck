package com.nexusdeck.app.core.catalog.health

import com.nexusdeck.app.core.downloader.LinkCheckResult
import com.nexusdeck.app.core.downloader.LinkVerifier.LinkVerdict
import com.nexusdeck.app.core.model.GameItem

/**
 * Status kesehatan satu tautan unduhan di katalog (v1.1.9 — katalog yang
 * menyeleksi sendiri).
 *
 * - NOT_CHECKED: belum pernah diperiksa; tampil normal seperti biasa.
 * - ALIVE: bukti keras file biner memang ada di URL itu.
 * - UNKNOWN: tidak ada bukti ke arah mana pun (429/5xx/timeout/403).
 *   UNKNOWN tidak pernah menyembunyikan apa pun dan tidak pernah mengubah
 *   status ALIVE/DEAD yang sudah tersimpan.
 * - DEAD: bukti keras file tidak ada (404/410, halaman HTML terkonfirmasi,
 *   atau file yang dilaporkan server terlalu kecil untuk jadi game).
 *   DEAD disembunyikan dari grid default, tapi tidak pernah dihapus.
 */
enum class LinkStatus {
    NOT_CHECKED,
    ALIVE,
    UNKNOWN,
    DEAD
}

/**
 * Satu entri kesehatan untuk satu game, dikunci oleh [GameItem.id].
 *
 * @param lastAliveAtEpochMs terakhir kali game ini terbukti ada — tidak
 * pernah dihapus oleh UNKNOWN/DEAD, supaya maintainer bisa membedakan
 * "mati kemarin" dari "mati sejak Maret" (kasus Myrient).
 * @param consecutiveFailures jumlah putusan keras (DEAD) berturut-turut.
 * Game baru disembunyikan bila nilainya mencapai [CONFIRMATIONS_REQUIRED].
 */
data class LinkHealth(
    val gameId: String,
    val status: LinkStatus,
    val httpStatus: Int?,
    val checkedAtEpochMs: Long,
    val lastAliveAtEpochMs: Long?,
    val consecutiveFailures: Int,
    val finalUrl: String?,
    val reason: String?
)

/**
 * Snapshot kesehatan seluruh katalog — format yang sama dipakai oleh cache
 * perangkat dan oleh snapshot CI (`assets/catalog-health.json`).
 */
data class CatalogHealthSnapshot(
    val version: Int = 1,
    val generatedAtEpochMs: Long = 0L,
    val generatedBy: String = "device",
    val entries: Map<String, LinkHealth> = emptyMap()
) {
    /** Waktu pemeriksaan terbaru di seluruh entri, atau null bila kosong. */
    val lastCheckedAtEpochMs: Long?
        get() = entries.values.maxOfOrNull { it.checkedAtEpochMs }
}

/** Pembungkus tampilan — [GameItem] sendiri tidak berubah. */
data class CatalogGame(
    val game: GameItem,
    val health: LinkHealth
)

/** Entri ALIVE/DEAD dianggap basi setelah 24 jam. */
const val HEALTH_TTL_ALIVE_MS = 24L * 60L * 60L * 1000L

/** Entri UNKNOWN dianggap basi setelah 1 jam (lebih sering diperiksa ulang). */
const val HEALTH_TTL_UNKNOWN_MS = 60L * 60L * 1000L

/** Putusan keras berturut-turut yang dibutuhkan sebelum sebuah game disembunyikan. */
const val CONFIRMATIONS_REQUIRED = 2

/**
 * Apakah entri ini sudah basi dan layak diperiksa ulang. NOT_CHECKED selalu
 * basi; ALIVE/DEAD basi setelah 24 jam; UNKNOWN basi setelah 1 jam.
 */
fun LinkHealth?.isStale(nowMs: Long): Boolean {
    if (this == null) return true
    if (status == LinkStatus.NOT_CHECKED) return true
    val ageMs = nowMs - checkedAtEpochMs
    return when (status) {
        LinkStatus.ALIVE, LinkStatus.DEAD -> ageMs > HEALTH_TTL_ALIVE_MS
        LinkStatus.UNKNOWN -> ageMs > HEALTH_TTL_UNKNOWN_MS
        LinkStatus.NOT_CHECKED -> true
    }
}

/**
 * Menggabungkan satu putusan baru ke entri yang sudah ada — fungsi murni
 * yang mengimplementasikan aturan hysteresis (v1.1.9):
 *
 * - Alive → ALIVE, `consecutiveFailures` direset, `lastAliveAt` diperbarui.
 * - Dead (keras) → `consecutiveFailures` + 1; status menjadi DEAD hanya
 *   bila jumlahnya mencapai [CONFIRMATIONS_REQUIRED]. Satu kegagalan saja
 *   TIDAK PERNAH menyembunyikan game.
 * - Unknown → status, `consecutiveFailures`, dan `lastAliveAt` tidak
 *   berubah; hanya `checkedAt`/`httpStatus`/`reason` yang diperbarui.
 */
fun mergeHealth(
    existing: LinkHealth?,
    gameId: String,
    verdict: LinkVerdict,
    nowMs: Long
): LinkHealth {
    val base = existing ?: LinkHealth(
        gameId = gameId,
        status = LinkStatus.NOT_CHECKED,
        httpStatus = null,
        checkedAtEpochMs = 0L,
        lastAliveAtEpochMs = null,
        consecutiveFailures = 0,
        finalUrl = null,
        reason = null
    )
    return when (verdict) {
        is LinkVerdict.Alive -> base.copy(
            status = LinkStatus.ALIVE,
            httpStatus = verdict.httpStatus,
            checkedAtEpochMs = nowMs,
            lastAliveAtEpochMs = nowMs,
            consecutiveFailures = 0,
            finalUrl = verdict.finalUrl,
            reason = null
        )
        is LinkVerdict.Dead -> {
            val failures = base.consecutiveFailures + 1
            base.copy(
                status = if (failures >= CONFIRMATIONS_REQUIRED) {
                    LinkStatus.DEAD
                } else {
                    base.status
                },
                httpStatus = verdict.httpStatus,
                checkedAtEpochMs = nowMs,
                consecutiveFailures = failures,
                finalUrl = verdict.finalUrl,
                reason = verdict.reason
            )
        }
        is LinkVerdict.Unknown -> base.copy(
            httpStatus = verdict.httpStatus,
            checkedAtEpochMs = nowMs,
            reason = verdict.reason
        )
    }
}

/**
 * Filter grid default: game DEAD disembunyikan, kecuali bila
 * `showUnavailable` aktif. Game yang belum diperiksa atau UNKNOWN selalu
 * tampil — ketiadaan bukti bukan bukti ketiadaan.
 */
fun filterVisibleGames(
    games: List<GameItem>,
    snapshot: CatalogHealthSnapshot?,
    showUnavailable: Boolean
): List<GameItem> {
    if (showUnavailable) return games
    if (snapshot == null) return games
    return games.filter { game ->
        val health = snapshot.entries[game.id]
        health == null || health.status != LinkStatus.DEAD
    }
}

/** Teks relatif berbahasa Indonesia untuk "Terakhir diperiksa …". */
fun formatLastChecked(checkedAtEpochMs: Long?, nowMs: Long): String {
    if (checkedAtEpochMs == null || checkedAtEpochMs <= 0L) {
        return "belum pernah diperiksa"
    }
    val diffMs = nowMs - checkedAtEpochMs
    if (diffMs < 0L) return "baru saja"
    val minutes = diffMs / 60_000L
    if (minutes < 1L) return "baru saja"
    if (minutes < 60L) return "$minutes mnt lalu"
    val hours = minutes / 60L
    if (hours < 24L) return "$hours jam lalu"
    val days = hours / 24L
    return "$days hari lalu"
}

/**
 * Memetakan hasil pre-flight gerbang unduhan ([LinkCheckResult]) ke putusan
 * katalog. Hanya sinyal keras yang jadi Dead: 404/410, atau alasan yang
 * menyebut halaman web (HTML terkonfirmasi oleh probe LinkVerifier).
 * Semua kegagalan lain — termasuk 503 dan tidak terjangkau — adalah
 * Unknown, supaya satu kegagalan sesaat tidak pernah menyembunyikan game.
 */
fun LinkCheckResult.toVerdict(): LinkVerdict {
    if (ok) {
        return LinkVerdict.Alive(
            httpStatus = httpStatus,
            contentLength = if (contentLength >= 0L) contentLength else null,
            finalUrl = finalUrl
        )
    }
    if (httpStatus == 404 || httpStatus == 410) {
        return LinkVerdict.Dead(httpStatus, "http-$httpStatus", finalUrl)
    }
    if (reason.contains("halaman web", ignoreCase = true)) {
        return LinkVerdict.Dead(httpStatus, "html-landing", finalUrl)
    }
    if (httpStatus > 0) {
        return LinkVerdict.Unknown(httpStatus, "http-$httpStatus", null)
    }
    return LinkVerdict.Unknown(null, "tidak-terjangkau", null)
}
