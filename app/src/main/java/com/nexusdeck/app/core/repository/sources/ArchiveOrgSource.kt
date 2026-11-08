package com.nexusdeck.app.core.repository.sources

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class ArchiveOrgSource : GameRepositorySource {

    override val sourceName: String = "Internet Archive (Retro & Homebrew)"
    override val priority: Int = 1

    override fun supportsPlatform(platform: GamePlatform?): Boolean = true

    override suspend fun search(
        query: String,
        platform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> = withContext(Dispatchers.IO) {
        val platformClause = when (platform) {
            GamePlatform.PS1 -> "(title:(PS1) OR title:(PSX) OR subject:(PS1) OR subject:(PSX) OR subject:(\"PlayStation\"))"
            GamePlatform.PSP -> "(title:(PSP) OR subject:(PSP) OR description:(\"PlayStation Portable\"))"
            GamePlatform.NINTENDO -> "(subject:(Nintendo) OR subject:(GBA) OR subject:(\"Game Boy Advance\") OR subject:(SNES))"
            GamePlatform.PS2 -> "(title:(PS2) OR subject:(PS2) OR subject:(\"PlayStation 2\"))"
            GamePlatform.PC -> "(mediatype:(software) AND (subject:(Windows) OR subject:(PC) OR subject:(DOS)))"
            GamePlatform.PS3 -> "(title:(PS3) OR subject:(PS3) OR subject:(\"PlayStation 3\"))"
            null -> "(subject:(PSP) OR subject:(Nintendo) OR subject:(PS1) OR subject:(PS2) OR subject:(\"PlayStation 2\") OR subject:(Windows) OR subject:(PS3))"
        }

        val fullQuery = if (query.isNotBlank()) {
            "title:(\"$query\" OR $query*) AND $platformClause AND (mediatype:software OR mediatype:data)"
        } else {
            "$platformClause AND mediatype:software"
        }

        fetchArchiveApi(fullQuery, platform, page, limit)
    }

    override suspend fun getPopular(
        platform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> = withContext(Dispatchers.IO) {
        search(query = "", platform = platform, page = page, limit = limit)
    }

    private fun fetchArchiveApi(
        query: String,
        targetPlatform: GamePlatform?,
        page: Int,
        limit: Int
    ): List<GameItem> {
        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "https://archive.org/advancedsearch.php?q=$encodedQuery" +
                "&fl[]=identifier&fl[]=title&fl[]=item_size&fl[]=subject" +
                "&sort[]=downloads+desc&rows=$limit&page=$page&output=json"

            val url = URL(apiUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NexusDeck-Engine/3.0")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return emptyList()
            }

            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            parseArchiveJsonResponse(responseText, targetPlatform)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    fun parseArchiveJsonResponse(
        jsonString: String,
        requestedPlatform: GamePlatform?
    ): List<GameItem> {
        val items = mutableListOf<GameItem>()
        try {
            val root = JSONObject(jsonString)
            val responseObj = root.optJSONObject("response")
            val docs = responseObj?.optJSONArray("docs")

            if (docs != null && docs.length() > 0) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    val identifier = doc.optString("identifier").trim()
                    val rawTitle = doc.optString("title").trim()
                    if (identifier.isEmpty() || rawTitle.isEmpty()) continue

                    val cleanTitle = rawTitle
                        .replace(Regex("\\s*\\((USA|Europe|Japan|En,Fr,De,Es,It|ISO|ROM|v\\d+.*?)\\)", RegexOption.IGNORE_CASE), "")
                        .trim()
                        .ifEmpty { rawTitle }

                    val itemSizeBytes = doc.optLong("item_size", 0L)
                    val subjectBlob = doc.opt("subject")?.toString().orEmpty()
                    val resolvedPlatform = requestedPlatform ?: inferPlatformFromMetadata(
                        title = rawTitle,
                        subjects = subjectBlob,
                        sizeBytes = itemSizeBytes
                    )

                    val fileSizeLabel = formatFileSize(itemSizeBytes, resolvedPlatform)
                    val coverUrl = "https://archive.org/services/img/$identifier"
                    val directDownloadUrl = "https://archive.org/compress/$identifier"

                    items.add(
                        createGameItem(
                            id = "archive_${identifier.lowercase(Locale.US)}",
                            title = cleanTitle,
                            platform = resolvedPlatform,
                            coverUrl = coverUrl,
                            fileSizeLabel = fileSizeLabel,
                            downloadUrl = directDownloadUrl
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Android SDK stub or parser error, fall back below
        }

        if (items.isNotEmpty()) {
            return items
        }

        return parseArchiveJsonFallback(jsonString, requestedPlatform)
    }

    private fun parseArchiveJsonFallback(
        jsonString: String,
        requestedPlatform: GamePlatform?
    ): List<GameItem> {
        val items = mutableListOf<GameItem>()
        val docPattern = Regex("""\{[^{}]*"identifier"\s*:\s*"([^"]+)"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        val idPattern = Regex(""""identifier"\s*:\s*"([^"]+)"""")
        val titlePattern = Regex(""""title"\s*:\s*"([^"]+)"""")
        val sizePattern = Regex(""""item_size"\s*:\s*(\d+)""")
        val subjectPattern = Regex(""""subject"\s*:\s*(\[[^\]]*\]|"[^"]*")""")

        val docsSlice = if (jsonString.contains("\"docs\"")) {
            jsonString.substringAfter("\"docs\"").substringBeforeLast("]")
        } else {
            jsonString
        }

        for (match in docPattern.findAll(docsSlice)) {
            val block = match.value
            val identifier = idPattern.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val rawTitle = titlePattern.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (identifier.isEmpty() || rawTitle.isEmpty()) continue

            val cleanTitle = rawTitle
                .replace(Regex("\\s*\\((USA|Europe|Japan|En,Fr,De,Es,It|ISO|ROM|v\\d+.*?)\\)", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { rawTitle }

            val itemSizeBytes = sizePattern.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val subjectBlob = subjectPattern.find(block)?.groupValues?.get(1).orEmpty()
            val resolvedPlatform = requestedPlatform ?: inferPlatformFromMetadata(
                title = rawTitle,
                subjects = subjectBlob,
                sizeBytes = itemSizeBytes
            )

            val fileSizeLabel = formatFileSize(itemSizeBytes, resolvedPlatform)
            val coverUrl = "https://archive.org/services/img/$identifier"
            val directDownloadUrl = "https://archive.org/compress/$identifier"

            items.add(
                createGameItem(
                    id = "archive_${identifier.lowercase(Locale.US)}",
                    title = cleanTitle,
                    platform = resolvedPlatform,
                    coverUrl = coverUrl,
                    fileSizeLabel = fileSizeLabel,
                    downloadUrl = directDownloadUrl
                )
            )
        }

        return items
    }

    private fun inferPlatformFromMetadata(
        title: String,
        subjects: String,
        sizeBytes: Long
    ): GamePlatform {
        val combined = "$title $subjects".lowercase(Locale.US)
        return when {
            combined.contains("psp") || combined.contains("playstation portable") -> GamePlatform.PSP
            combined.contains("gba") || combined.contains("nintendo") || combined.contains("snes") || combined.contains("game boy") -> GamePlatform.NINTENDO
            combined.contains("ps3") || combined.contains("playstation 3") -> GamePlatform.PS3
            combined.contains("ps2") || combined.contains("playstation 2") -> GamePlatform.PS2
            combined.contains("windows") || combined.contains("pc") || combined.contains("dos") -> GamePlatform.PC
            sizeBytes > 8_000_000_000L -> GamePlatform.PS3
            sizeBytes > 1_600_000_000L -> GamePlatform.PS2
            sizeBytes < 64_000_000L -> GamePlatform.NINTENDO
            else -> GamePlatform.PSP
        }
    }

    private fun formatFileSize(sizeBytes: Long, platform: GamePlatform): String {
        if (sizeBytes <= 0L) {
            return when (platform) {
                GamePlatform.PS1 -> "540 MB"
                GamePlatform.NINTENDO -> "32 MB"
                GamePlatform.PSP -> "840 MB"
                GamePlatform.PS2 -> "2.65 GB"
                GamePlatform.PC -> "4.20 GB"
                GamePlatform.PS3 -> "9.80 GB"
            }
        }
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            val gb = mb / 1024.0
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.0f MB", mb.coerceAtLeast(8.0))
        }
    }

    private fun createGameItem(
        id: String,
        title: String,
        platform: GamePlatform,
        coverUrl: String,
        fileSizeLabel: String,
        downloadUrl: String
    ): GameItem {
        val (minRam, targetEmulator) = when (platform) {
            GamePlatform.PS1 -> 2 to TargetEmulator.DuckStation
            GamePlatform.NINTENDO -> 2 to TargetEmulator.Lemuroid
            GamePlatform.PSP -> 3 to TargetEmulator.PPSSPP
            GamePlatform.PS2 -> 4 to TargetEmulator.ARMSX2
            GamePlatform.PC -> 6 to TargetEmulator.GameNative
            GamePlatform.PS3 -> 8 to TargetEmulator.ARMSX3
        }
        return GameItem(
            id = id,
            title = title,
            platform = platform,
            coverUrl = coverUrl,
            minRamGb = minRam,
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8", "helio_g99"),
            targetEmulator = targetEmulator,
            fileSizeLabel = fileSizeLabel,
            downloadUrl = downloadUrl,
            isWebResult = true,
            sourceRepositoryName = sourceName
        )
    }
}
