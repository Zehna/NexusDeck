package com.nexusdeck.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Modifier Shimmer Skeleton Loading dengan animasi kilau lembut dari dark surface ke violet highlight.
 */
fun Modifier.shimmerPlaceholder(
    visible: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier = composed {
    if (!visible) return@composed this

    val shimmerColors = listOf(
        Color(0xFF1A1826),
        Color(0xFF26223B),
        Color(0xFF383152),
        Color(0xFF26223B),
        Color(0xFF1A1826)
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -800f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 400f, translateAnim.value - 400f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Kartu poster dummy shimmer skeleton saat aplikasi sedang memuat game dari web.
 */
@Composable
fun ShimmerPosterCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(16.dp))
    )
}
