package com.nexusdeck.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nexusdeck.app.core.model.GamePlatform

fun platformBadgeColor(platform: GamePlatform): Color {
    return when (platform) {
        GamePlatform.PSP -> Color(0xFF06B6D4) // Cyan Neon Glow
        GamePlatform.PS2 -> Color(0xFF3B82F6) // Deep Royal Blue PlayStation
        GamePlatform.NINTENDO -> Color(0xFFEF4444) // Neon Crimson Red (Nintendo)
        GamePlatform.PC -> Color(0xFF10B981) // Emerald Green PC
        GamePlatform.PS3 -> Color(0xFF8B5CF6) // Violet Purple PS3
    }
}

@Composable
fun GameCoverArt(
    coverUrl: String,
    title: String,
    platform: GamePlatform,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = platformBadgeColor(platform)

    Box(modifier = modifier) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$title Box Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                OfflinePosterFallback(
                    title = title,
                    platform = platform,
                    accent = accent
                )
            },
            error = {
                OfflinePosterFallback(
                    title = title,
                    platform = platform,
                    accent = accent
                )
            }
        )

        // Vignette gradasi gelap halus di bagian bawah poster agar judul & badge kontras
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x440B0B0E),
                            Color.Transparent,
                            Color(0xCC0B0B0E),
                            Color(0xF50B0B0E)
                        )
                    )
                )
        )
    }
}

@Composable
private fun OfflinePosterFallback(
    title: String,
    platform: GamePlatform,
    accent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.35f),
                        Color(0xFF1A1826),
                        Color(0xFF0B0B0E)
                    )
                )
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xCC0B0B0E),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.7f))
            ) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFE4E4E7),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
