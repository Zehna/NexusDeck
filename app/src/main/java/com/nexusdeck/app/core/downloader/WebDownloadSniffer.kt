package com.nexusdeck.app.core.downloader

import android.webkit.WebResourceResponse
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator
import com.nexusdeck.app.core.optimizer.CompressionAdvice
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.Locale

/**
 * Data model untuk hasil sniffing URL unduhan file game dari WebView.
 */
data class SniffedDownload(
    val url: String,
    val fileName: String,
    val fileSizeLabel: String,
    val platform: GamePlatform,
    val targetEmulator: TargetEmulator,
    val userAgent: String = "",
    val cookies: String = "",
    val contentLength: Long = 0L,
    val mimeType: String = ""
) {
    val compressionAdvice: CompressionAdvice
        get() = CompressionAdvisor.analyzeGameFormat(platform, fileSizeLabel, fileName)

    fun toGameItem(uniqueId: String? = null): GameItem {
        val cleanTitle = cleanTitleFromFilename(fileName)
        val id = uniqueId ?: "web_${platform.name.lowercase(Locale.US)}_${System.currentTimeMillis()}"
        return GameItem(
            id = id,
            title = cleanTitle,
            platform = platform,
            coverUrl = defaultCoverForPlatform(platform),
            minRamGb = when (platform) {
                GamePlatform.PS1 -> 2
                GamePlatform.NINTENDO -> 2
                GamePlatform.PSP -> 3
                GamePlatform.PS2 -> 4
                GamePlatform.PC -> 6
                GamePlatform.PS3 -> 8
            },
            recommendedSocKeywords = listOf("snapdragon_8", "snapdragon_7", "dimensity_8"),
            targetEmulator = targetEmulator,
            fileSizeLabel = fileSizeLabel,
            downloadUrl = url,
            isWebResult = true,
            sourceRepositoryName = "Web Explorer (${extractHost(url)})"
        )
    }

    companion object {
        fun cleanTitleFromFilename(fileName: String): String {
            var withoutExt = fileName
                .replace(Regex("""(?i)\.7z\.\d+$"""), "")
                .replace(Regex("""(?i)\.part\d+\.rar$"""), "")
                .replace(Regex("""(?i)\.tar\.gz$"""), "")

            if (withoutExt == fileName) {
                withoutExt = fileName.substringBeforeLast(".")
            }

            var title = withoutExt
                .replace("_", " ")
                .replace(Regex("""\s*\(.*?\)"""), "")
                .replace(Regex("""\s*\[.*?\]"""), "")
                .trim()

            title = title.replace(Regex("""(?i)\s+(?:PlayStation\s*\d+|PS[123]|PSP|PC(?:\s+Windows)?|Nintendo|GameCube|GBA|NDS|SNES)$"""), "")
                .trim()

            return title.ifEmpty { withoutExt }
        }

        fun defaultCoverForPlatform(platform: GamePlatform): String {
            return ""
        }

        fun extractHost(url: String): String {
            return try {
                val uri = java.net.URI(url)
                uri.host ?: "Web Link"
            } catch (_: Exception) {
                "Web Link"
            }
        }
    }
}

/**
 * Basic AdBlocker Engine untuk WebView.
 * Memblokir URL iklan, tracker, redirect judi/popup yang mengganggu saat menjelajahi web ROM.
 */
object WebAdBlocker {
    val adHostKeywords = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google", "popads.net", "adnxs.com", "popcash.net",
        "adsterra.com", "propellerads.com", "exoclick.com", "clickadu.com",
        "monetag.com", "hilltopads.com", "trafficjunky", "ad-maven",
        "bidvertiser", "outbrain.com", "taboola.com", "criteo.com",
        "yandex.ru/ads", "adtrue.com", "zeroredirect", "adcolony",
        "unityads", "vungle", "inmobi", "applovin", "ironsource",
        "bet365", "1xbet", "parimatch", "dafabet", "casino",
        "/ads.js", "/ad.js", "/popunder", "/banner/", "pagead",
        "adsystem", "adnxs", "adserver"
    )

    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return adHostKeywords.any { lower.contains(it) }
    }

    fun createEmptyResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}

/**
 * Sniffer & Interceptor untuk mendeteksi link unduhan game (.iso, .cso, .zip, dll.) pada WebView.
 */
object WebDownloadSniffer {

    val GAME_EXTENSIONS = setOf(
        "iso", "cso", "chd", "zip", "7z", "pkg", "bin",
        "rar", "gba", "nds", "rvz", "wbfs", "gcm", "nes",
        "sfc", "smc", "n64", "z64", "001", "gz", "tgz"
    )

    /**
     * Memeriksa apakah suatu URL, content-disposition, atau mimetype merujuk pada file game yang didukung.
     */
    fun isGameDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null
    ): Boolean {
        val cleanUrl = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        val extFromUrl = cleanUrl.substringAfterLast('.', "")
        if (extFromUrl in GAME_EXTENSIONS) {
            return true
        }

        // Multi-part archive patterns (.7z.001, .part1.rar, .tar.gz)
        if (cleanUrl.contains(".7z.001") ||
            cleanUrl.contains(".tar.gz") ||
            cleanUrl.contains(".tgz") ||
            Regex("""\.part\d+\.rar""", RegexOption.IGNORE_CASE).containsMatchIn(cleanUrl)
        ) {
            return true
        }

        // Dynamic URL query string patterns (e.g. download.php?id=123&file=Persona4.iso)
        if (url.contains('?')) {
            val query = url.substringAfter('?').lowercase(Locale.US)
            val decodedQuery = runCatching { URLDecoder.decode(query, "UTF-8") }.getOrDefault(query)
            if (GAME_EXTENSIONS.any { ext ->
                decodedQuery.contains(".$ext") ||
                Regex("""(?:=|\b)[a-z0-9_-]+\.$ext(?:\b|&|$)""").containsMatchIn(decodedQuery)
            } || decodedQuery.contains(".7z.001") ||
                decodedQuery.contains(".tar.gz") ||
                Regex("""\.part\d+\.rar""", RegexOption.IGNORE_CASE).containsMatchIn(decodedQuery)
            ) {
                return true
            }
        }

        if (!contentDisposition.isNullOrBlank()) {
            val lowerCd = contentDisposition.lowercase(Locale.US)
            if (GAME_EXTENSIONS.any { lowerCd.contains(".$it") } ||
                lowerCd.contains(".7z.001") ||
                lowerCd.contains(".tar.gz") ||
                Regex("""\.part\d+\.rar""", RegexOption.IGNORE_CASE).containsMatchIn(lowerCd)
            ) {
                return true
            }
        }

        if (!mimeType.isNullOrBlank()) {
            val lowerMime = mimeType.lowercase(Locale.US)
            if (lowerMime.contains("x-iso9660-image") ||
                lowerMime.contains("x-7z-compressed") ||
                lowerMime.contains("x-cd-image") ||
                lowerMime.contains("application/zip") ||
                lowerMime.contains("application/x-rar-compressed")
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Mengekstrak nama file nyata dari Content-Disposition, URL query parameter, atau URL path.
     */
    fun extractFileName(url: String, contentDisposition: String? = null): String {
        if (!contentDisposition.isNullOrBlank()) {
            val cd = contentDisposition
            val filenameStarRegex = Regex("""filename\*\s*=\s*UTF-8''([^;\s]+)""", RegexOption.IGNORE_CASE)
            val starMatch = filenameStarRegex.find(cd)
            if (starMatch != null) {
                val decoded = runCatching { URLDecoder.decode(starMatch.groupValues[1], "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded.trim('"', '\'')
            }

            val filenameRegex = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            val match = filenameRegex.find(cd)
            if (match != null) {
                return match.groupValues[1].trim()
            }

            val plainRegex = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
            val plainMatch = plainRegex.find(cd)
            if (plainMatch != null) {
                return plainMatch.groupValues[1].trim('"', '\'')
            }
        }

        // Check dynamic query parameters for filename (e.g. ?file=Persona4.iso or ?rom=Tekken5.iso)
        if (url.contains('?')) {
            val queryString = url.substringAfter('?')
            val queryParamRegex = Regex("""(?i)(?:file|filename|name|f|rom)\s*=\s*([^&]+)""")
            val paramMatch = queryParamRegex.find(queryString)
            if (paramMatch != null) {
                val rawVal = paramMatch.groupValues[1]
                val decoded = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
                if (decoded.isNotBlank() && decoded.contains('.')) {
                    return decoded.trim('"', '\'')
                }
            }
        }

        val pathPart = url.substringBefore('?').substringBefore('#')
        val rawName = pathPart.substringAfterLast('/')
        val decoded = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
        if (decoded.isNotBlank() && decoded.contains(".")) {
            return decoded.trim()
        }

        return "game_${System.currentTimeMillis()}.iso"
    }

    /**
     * Memperkirakan platform game berdasarkan nama file, URL, dan ukuran file.
     */
    fun inferPlatform(url: String, fileName: String, sizeBytes: Long = 0L): GamePlatform {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        when (ext) {
            "cso" -> return GamePlatform.PSP
            "gba", "nds", "nes", "sfc", "smc", "n64", "z64", "rvz", "wbfs", "gcm" -> return GamePlatform.NINTENDO
            "pkg" -> return GamePlatform.PS3
        }

        val normalized = "$fileName $url".lowercase(Locale.US).replace('_', ' ').replace('-', ' ')
        val words = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

        return when {
            words.contains("ps1") || words.contains("psx") || words.contains("duckstation") -> GamePlatform.PS1
            words.contains("psp") || words.contains("ppsspp") || (words.contains("playstation") && words.contains("portable")) -> GamePlatform.PSP
            words.contains("ps3") || (words.contains("playstation") && words.contains("3")) || words.contains("rpcs3") -> GamePlatform.PS3
            words.contains("ps2") || (words.contains("playstation") && words.contains("2")) || words.contains("pcsx2") -> GamePlatform.PS2
            words.contains("gba") || words.contains("nds") || words.contains("nintendo") ||
                (words.contains("game") && words.contains("boy")) || words.contains("snes") ||
                words.contains("gamecube") || words.contains("dolphin") -> GamePlatform.NINTENDO
            words.contains("pc") || words.contains("windows") || words.contains("dosbox") ||
                words.contains("x86") || words.contains("win64") || words.contains("win32") -> GamePlatform.PC
            sizeBytes > 8_000_000_000L -> GamePlatform.PS3
            sizeBytes > 1_600_000_000L -> GamePlatform.PS2
            sizeBytes in 1..80_000_000L -> GamePlatform.NINTENDO
            else -> GamePlatform.PSP
        }
    }

    /**
     * Memformat ukuran file menjadi string yang ramah pengguna (contoh: 1.25 GB, 850 MB).
     */
    fun formatFileSize(bytes: Long, platform: GamePlatform): String {
        if (bytes <= 0L) {
            return when (platform) {
                GamePlatform.PS1 -> "~450 MB"
                GamePlatform.NINTENDO -> "~32 MB"
                GamePlatform.PSP -> "~850 MB"
                GamePlatform.PS2 -> "~2.40 GB"
                GamePlatform.PC -> "~3.50 GB"
                GamePlatform.PS3 -> "~8.50 GB"
            }
        }
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            val gb = mb / 1024.0
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.0f MB", mb.coerceAtLeast(1.0))
        }
    }

    /**
     * Memproses data download dari WebView dan menghasilkan SniffedDownload jika valid.
     */
    fun sniffDownload(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = 0L,
        cookies: String? = null
    ): SniffedDownload? {
        if (!isGameDownload(url, contentDisposition, mimeType)) {
            return null
        }

        val fileName = extractFileName(url, contentDisposition)
        val platform = inferPlatform(url, fileName, contentLength)
        val targetEmulator = when (platform) {
            GamePlatform.PS1 -> TargetEmulator.DuckStation
            GamePlatform.NINTENDO -> TargetEmulator.Lemuroid
            GamePlatform.PSP -> TargetEmulator.PPSSPP
            GamePlatform.PS2 -> TargetEmulator.ARMSX2
            GamePlatform.PC -> TargetEmulator.GameNative
            GamePlatform.PS3 -> TargetEmulator.ARMSX3
        }
        val fileSizeLabel = formatFileSize(contentLength, platform)

        return SniffedDownload(
            url = url,
            fileName = fileName,
            fileSizeLabel = fileSizeLabel,
            platform = platform,
            targetEmulator = targetEmulator,
            userAgent = userAgent.orEmpty(),
            cookies = cookies.orEmpty(),
            contentLength = contentLength,
            mimeType = mimeType.orEmpty()
        )
    }
}
