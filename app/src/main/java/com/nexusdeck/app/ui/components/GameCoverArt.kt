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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nexusdeck.app.core.model.GamePlatform

@Composable
fun GameCoverArt(
    coverUrl: String,
    title: String,
    platform: GamePlatform,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val platformAccent = when (platform) {
        GamePlatform.PC -> Color(0xFF00E5FF)
        GamePlatform.PS2 -> Color(0xFF3B82F6)
        GamePlatform.PS3 -> Color(0xFFA855F7)
    }

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
                OfflinePlatformFallback(
                    platform = platform,
                    platformAccent = platformAccent
                )
            },
            error = {
                OfflinePlatformFallback(
                    platform = platform,
                    platformAccent = platformAccent
                )
            }
        )

        // Scrim gelap di bagian atas & bawah agar badge platform tetap terbaca jelas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99090D16),
                            Color(0x22090D16),
                            Color(0xCC090D16)
                        )
                    )
                )
        )
    }
}

@Composable
private fun OfflinePlatformFallback(
    platform: GamePlatform,
    platformAccent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        platformAccent.copy(alpha = 0.35f),
                        Color(0xFF0B1120)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xAA090D16),
                border = BorderStroke(1.5.dp, platformAccent.copy(alpha = 0.75f))
            ) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = platformAccent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OFFLINE ARTWORK",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                ),
                color = Color(0xFF94A3B8)
            )
        }
    }
}
