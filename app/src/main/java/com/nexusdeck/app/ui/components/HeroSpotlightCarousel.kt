package com.nexusdeck.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GameState
import com.nexusdeck.app.ui.theme.NexusPalette
import kotlinx.coroutines.delay

/**
 * Hero Spotlight Carousel (Banner Unggulan Lebar Bergaya Netflix / Steam Big Picture)
 * Menampilkan backdrop beresolusi tinggi, logo platform, judul tebal, specs,
 * dan tombol cepat [▶ Mainkan Sekarang] / [Download] dengan sentuhan taktil (haptic).
 */
@Composable
fun HeroSpotlightCarousel(
    featuredGames: List<GameItem>,
    getGameState: (String) -> GameState,
    onGameClick: (GameItem) -> Unit,
    onActionClick: (GameItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (featuredGames.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { featuredGames.size })
    val haptic = LocalHapticFeedback.current

    // Auto-scroll halus berkala setiap 7 detik jika pengguna tidak sedang melakukan gesture
    LaunchedEffect(pagerState) {
        while (true) {
            delay(7000L)
            if (!pagerState.isScrollInProgress && featuredGames.isNotEmpty()) {
                val next = (pagerState.currentPage + 1) % featuredGames.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val game = featuredGames[page]
            val gameState = getGameState(game.id)
            val isInstalled = gameState is GameState.Installed
            val platformColor = platformBadgeColor(game.platform)

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 1.025f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "carousel_bounce_scale"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(205.dp)
                    .scale(scale)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = platformColor.copy(alpha = 0.35f),
                        spotColor = NexusPalette.PrimaryViolet.copy(alpha = 0.45f)
                    )
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    platformColor.copy(alpha = 0.8f),
                                    NexusPalette.PrimaryViolet.copy(alpha = 0.35f)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onGameClick(game)
                    },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NexusPalette.SurfaceCard)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Backdrop Artwork
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(game.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic Dark Gradient Overlay (Vignette)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x330B0B0E),
                                        Color(0x990B0B0E),
                                        Color(0xF50B0B0E)
                                    ),
                                    startY = 40f
                                )
                            )
                    )

                    // Top Badges (Platform & Featured Tag)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC0B0B0E),
                            border = BorderStroke(1.dp, platformColor)
                        ) {
                            Text(
                                text = game.platform.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = platformColor,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x99221F32),
                            border = BorderStroke(1.dp, NexusPalette.SecondaryPurple.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "SPOTLIGHT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Bottom Content (Title, Meta Info & Quick Play Button)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${game.fileSizeLabel} • ${game.targetEmulator.displayName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NexusPalette.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )

                            // Quick Action Button
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActionClick(game)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isInstalled)
                                        NexusPalette.PrimaryViolet
                                    else
                                        Color(0xFF22C55E),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInstalled) Icons.Default.PlayArrow else Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isInstalled) "Mainkan" else "Download",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dot Page Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(featuredGames.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (isSelected) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                NexusPalette.PrimaryViolet
                            else
                                NexusPalette.CardBorder
                        )
                )
            }
        }
    }
}
