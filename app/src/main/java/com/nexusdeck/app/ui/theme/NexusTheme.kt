package com.nexusdeck.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object NexusPalette {
    val DeepDarkBlack = Color(0xFF0B0B0E)
    val BackgroundDeep = DeepDarkBlack
    val ElevatedBackground = Color(0xFF13121A)
    val SurfaceCard = Color(0xFF1A1826)
    val SurfaceCardElevated = Color(0xFF221F32)
    val CardBorder = Color(0xFF262438)
    val BorderCard = CardBorder
    val PrimaryViolet = Color(0xFF7C3AED)
    val SecondaryPurple = Color(0xFFC084FC)
    val AccentVioletLight = SecondaryPurple
    val NeonCyan = Color(0xFF06B6D4)
    val DeepVioletSurface = Color(0xFF2E1065)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF71717A)

    val VioletGradient = Brush.horizontalGradient(
        colors = listOf(PrimaryViolet, SecondaryPurple)
    )

    val HorizontalVioletGradient = VioletGradient

    val VerticalVioletGradient = Brush.linearGradient(
        colors = listOf(PrimaryViolet, SecondaryPurple)
    )

    val CyanNeonGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF06B6D4), Color(0xFF38BDF8))
    )
}

val NexusFullRoidDarkColorScheme = darkColorScheme(
    primary = NexusPalette.PrimaryViolet,
    onPrimary = Color.White,
    secondary = NexusPalette.SecondaryPurple,
    onSecondary = Color(0xFF13121A),
    background = NexusPalette.DeepDarkBlack,
    surface = NexusPalette.SurfaceCard,
    surfaceVariant = NexusPalette.ElevatedBackground,
    onBackground = NexusPalette.TextPrimary,
    onSurface = NexusPalette.TextPrimary,
    outline = NexusPalette.CardBorder
)

val NexusTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp
    )
)
