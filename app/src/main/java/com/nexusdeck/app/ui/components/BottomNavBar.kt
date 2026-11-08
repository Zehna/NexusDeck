package com.nexusdeck.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.ui.theme.NexusPalette

val GlobeWebIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GlobeWeb",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val path = PathBuilder().apply {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(11f, 19.93f)
            curveTo(7.05f, 19.44f, 4f, 16.08f, 4f, 12f)
            curveTo(4f, 11.38f, 4.08f, 10.79f, 4.21f, 10.21f)
            lineTo(9f, 15f)
            verticalLineTo(16f)
            curveTo(9f, 17.1f, 9.9f, 18f, 11f, 18f)
            verticalLineTo(19.93f)
            close()
            moveTo(17.9f, 17.39f)
            curveTo(17.64f, 16.58f, 16.9f, 16f, 16f, 16f)
            horizontalLineTo(15f)
            verticalLineTo(13f)
            curveTo(15f, 12.45f, 14.55f, 12f, 14f, 12f)
            horizontalLineTo(8f)
            verticalLineTo(10f)
            horizontalLineTo(10f)
            curveTo(10.55f, 10f, 11f, 9.55f, 11f, 9f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            curveTo(14.1f, 7f, 15f, 6.1f, 15f, 5f)
            verticalLineTo(4.59f)
            curveTo(17.93f, 5.78f, 20f, 8.65f, 20f, 12f)
            curveTo(20f, 14.08f, 19.2f, 15.97f, 17.9f, 17.39f)
            close()
        }.getNodes()
        addPath(path, fill = SolidColor(Color.White))
    }.build()
}

enum class NexusBottomTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    BROWSER("Web", GlobeWebIcon),
    LIBRARY("Library", Icons.Default.Star),
    FAVORITES("Favorites", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    selectedTab: NexusBottomTab,
    installedCount: Int,
    onSelectTab: (NexusBottomTab) -> Unit,
    onQuickResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF13121A).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, NexusPalette.CardBorder),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    tab = NexusBottomTab.HOME,
                    isSelected = selectedTab == NexusBottomTab.HOME,
                    badgeCount = 0,
                    onClick = { onSelectTab(NexusBottomTab.HOME) }
                )

                BottomNavItem(
                    tab = NexusBottomTab.BROWSER,
                    isSelected = selectedTab == NexusBottomTab.BROWSER,
                    badgeCount = 0,
                    onClick = { onSelectTab(NexusBottomTab.BROWSER) }
                )

                // Ruang untuk Floating Center Gamepad Button
                Spacer(modifier = Modifier.size(56.dp))

                BottomNavItem(
                    tab = NexusBottomTab.LIBRARY,
                    isSelected = selectedTab == NexusBottomTab.LIBRARY,
                    badgeCount = installedCount,
                    onClick = { onSelectTab(NexusBottomTab.LIBRARY) }
                )

                BottomNavItem(
                    tab = NexusBottomTab.SETTINGS,
                    isSelected = selectedTab == NexusBottomTab.SETTINGS,
                    badgeCount = 0,
                    onClick = { onSelectTab(NexusBottomTab.SETTINGS) }
                )
            }
        }

        // Floating Center Button (Gradasi Ungu #7C3AED -> #C084FC + Ikon Gamepad/Quick-Resume)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp)
                .size(60.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = NexusPalette.PrimaryViolet,
                    spotColor = NexusPalette.SecondaryPurple
                )
                .clip(CircleShape)
                .background(brush = NexusPalette.VerticalVioletGradient)
                .clickable(onClick = onQuickResumeClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Quick Resume Game",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "PLAY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: NexusBottomTab,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val activeColor = NexusPalette.SecondaryPurple
    val inactiveColor = NexusPalette.TextMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(22.dp)
                )

                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(NexusPalette.PrimaryViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = if (isSelected) Color.White else inactiveColor
            )
        }
    }
}
