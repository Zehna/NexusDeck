package com.nexusdeck.app.core.artwork

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.ui.components.platformBadgeColor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Universal Box Art Scraper & Steelbook Renderer.
 * Mengikis thumbnail resmi dari Libretro Thumbnails GitHub CDN dan Steam CDN,
 * serta menyediakan komponen kartu Steelbook logam gelap presisi jika box art offline.
 */
object UniversalArtworkScraper {

    private const val LIBRETRO_CDN_BASE =
        "https://raw.githubusercontent.com/libretro-thumbnails"

    /**
     * Membersihkan string judul dari tag region, rilis, ekstensi file, dan karakter noise.
     */
    fun cleanTitle(rawTitle: String): String {
        var clean = rawTitle.trim()

        // Hapus ekstensi berkas (.iso, .cso, .chd, .zip, .7z, .gba, .nds, .pkg, dll.)
        val extensions = listOf(
            ".iso", ".cso", ".chd", ".zip", ".7z", ".bin", ".cue",
            ".gba", ".gbc", ".nes", ".snes", ".smc", ".nds", ".n64",
            ".pkg", ".rar", ".gz", ".tar"
        )
        for (ext in extensions) {
            if (clean.endsWith(ext, ignoreCase = true)) {
                clean = clean.substring(0, clean.length - ext.length)
            }
        }

        // Hapus tag dalam kurung bulat (USA), (Europe), (v1.0), (En,Ja), dll.
        clean = clean.replace(Regex("\\s*\\([^)]*\\)"), "")

        // Hapus tag dalam kurung siku [Europe], [v1.01], [!], dll.
        clean = clean.replace(Regex("\\s*\\[[^]]*\\]"), "")

        // Ubah underscore dan strip berlebih menjadi spasi tunggal
        clean = clean.replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return clean
    }

    /**
     * Memetakan platform game ke nama folder repositori Libretro Thumbnails resmi.
     */
    fun getLibretroSystemFolder(platform: GamePlatform): String {
        return when (platform) {
            GamePlatform.PSP -> "Sony_-_PlayStation_Portable"
            GamePlatform.PS2 -> "Sony_-_PlayStation_2"
            GamePlatform.PS3 -> "Sony_-_PlayStation_3"
            GamePlatform.NINTENDO -> "Nintendo_-_Game_Boy_Advance"
            GamePlatform.PC -> "DOS"
        }
    }

    /**
     * Menghasilkan URL langsung ke CDN Libretro Named_Boxarts untuk judul game yang dibersihkan.
     */
    fun getLibretroThumbnailUrl(platform: GamePlatform, cleanTitle: String): String {
        val folder = getLibretroSystemFolder(platform)
        // Libretro thumbnails menggunakan nama file PNG yang di-URL-encode dengan spasi '%20'
        val sanitized = cleanTitle
            .replace("&", "_")
            .replace("/", "_")
            .replace(":", "_")
            .trim()
        val encoded = try {
            URLEncoder.encode(sanitized, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        } catch (_: Exception) {
            sanitized.replace(" ", "%20")
        }
        return "$LIBRETRO_CDN_BASE/$folder/master/Named_Boxarts/$encoded.png"
    }

    /**
     * Database pemetaan App ID Steam untuk game PC populer.
     */
    private val steamAppMap = mapOf(
        "fallout new vegas" to 22380,
        "half life 2" to 220,
        "half-life 2" to 220,
        "grand theft auto san andreas" to 12120,
        "grand theft auto iv" to 12210,
        "gta iv" to 12210,
        "gta san andreas" to 12120,
        "portal 2" to 620,
        "portal" to 400,
        "left 4 dead 2" to 550,
        "the elder scrolls v skyrim" to 489830,
        "skyrim" to 489830,
        "doom" to 379720,
        "the witcher 3" to 292030,
        "bioshock" to 7670
    )

    fun getSteamAppId(cleanTitle: String): Int? {
        val normalized = cleanTitle.lowercase(Locale.ROOT)
            .replace(":", "")
            .replace("'", "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return steamAppMap.entries.firstOrNull { normalized.contains(it.key) }?.value
    }

    fun getSteamBannerUrl(appId: Int): String {
        return "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
    }

    /**
     * Menyelesaikan URL box art paling otentik untuk sebuah GameItem.
     * Jika URL saat ini bukan foto stock Unsplash, gunakan URL tersebut.
     * Jika PC, coba cari banner resmi Steam.
     * Sebagai fallback, arahkan ke Libretro CDN box art.
     */
    fun resolveCoverUrl(game: GameItem): String {
        val current = game.coverUrl.trim()
        if (current.isNotBlank() && !current.contains("unsplash.com", ignoreCase = true)) {
            return current
        }

        val cleaned = cleanTitle(game.title)
        if (game.platform == GamePlatform.PC) {
            val steamId = getSteamAppId(cleaned)
            if (steamId != null) {
                return getSteamBannerUrl(steamId)
            }
        }

        return getLibretroThumbnailUrl(game.platform, cleaned)
    }
}

/**
 * Komponen kartu Steelbook kolektor bergaya logam gelap brushed metal
 * dengan pita warna aksen resmi konsol, logo embossed, dan tekstur metalik.
 */
@Composable
fun ConsoleSteelbookCard(
    title: String,
    platform: GamePlatform,
    modifier: Modifier = Modifier
) {
    val accent = platformBadgeColor(platform)
    val cleanedTitle = UniversalArtworkScraper.cleanTitle(title)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF262833), // Brushed steel highlight
                        Color(0xFF181A22), // Deep titanium
                        Color(0xFF0F1015), // Dark gunmetal
                        Color(0xFF090A0D)  // Deep obsidian shadow
                    )
                )
            )
            .padding(12.dp)
    ) {
        // Angled Brushed Metallic Sheen Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Pita Brand Konsol (Console Ribbon Badge di sudut atas)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accent.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.85f))
            ) {
                Text(
                    text = platform.displayName.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        fontSize = 9.sp
                    ),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Simbol Disc / Cartridge Watermark di Tengah
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0x15FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x33000000),
                border = BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {}
        }

        // Teks Judul Bergaya Embossed Steel di Bagian Bawah
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = cleanedTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFFF1F5F9),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "STEELBOOK EDITION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    fontSize = 8.sp
                ),
                color = Color(0xFF94A3B8)
            )
        }
    }
}
