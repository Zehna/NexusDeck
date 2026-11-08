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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.ui.theme.NexusPalette

enum class NexusBottomTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    BROWSER("Web", Icons.Default.Language),
    LIBRARY("Library", Icons.Default.Star),
    FAVORITES("Favorites", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Frosted Glassmorphism Floating Bottom Navigation Bar
 * Menggunakan latar semi-transparan 0xEB0D0C13, border gradien tipis,
 * efek pendaran halus (subtle glow) pada tab aktif, serta respons getaran mikro (haptic touch).
 */
@Composable
fun BottomNavBar(
    selectedTab: NexusBottomTab,
    installedCount: Int,
    onSelectTab: (NexusBottomTab) -> Unit,
    onQuickResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating Frosted Glass Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xEB0D0C13), // Semi-transparan Frosted OLED Glass
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x337C3AED),
                        Color(0x11C084FC),
                        Color(0x337C3AED)
                    )
                )
            ),
            shadowElevation = 18.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    tab = NexusBottomTab.HOME,
                    isSelected = selectedTab == NexusBottomTab.HOME,
                    badgeCount = 0,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectTab(NexusBottomTab.HOME)
                    }
                )

                BottomNavItem(
                    tab = NexusBottomTab.BROWSER,
                    isSelected = selectedTab == NexusBottomTab.BROWSER,
                    badgeCount = 0,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectTab(NexusBottomTab.BROWSER)
                    }
                )

                // Ruang untuk Floating Center Gamepad Button
                Spacer(modifier = Modifier.size(54.dp))

                BottomNavItem(
                    tab = NexusBottomTab.LIBRARY,
                    isSelected = selectedTab == NexusBottomTab.LIBRARY,
                    badgeCount = installedCount,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectTab(NexusBottomTab.LIBRARY)
                    }
                )

                BottomNavItem(
                    tab = NexusBottomTab.SETTINGS,
                    isSelected = selectedTab == NexusBottomTab.SETTINGS,
                    badgeCount = 0,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectTab(NexusBottomTab.SETTINGS)
                    }
                )
            }
        }

        // Floating Center Button (Gradasi Ungu Neon #7C3AED -> #C084FC + Ikon Play)
        val centerInteraction = remember { MutableInteractionSource() }
        val isCenterPressed by centerInteraction.collectIsPressedAsState()
        val centerScale by animateFloatAsState(
            targetValue = if (isCenterPressed) 1.08f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "center_btn_scale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .size(62.dp)
                .scale(centerScale)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = NexusPalette.PrimaryViolet,
                    spotColor = NexusPalette.SecondaryPurple
                )
                .clip(CircleShape)
                .background(brush = NexusPalette.VerticalVioletGradient)
                .border(1.5.dp, Color(0x66FFFFFF), CircleShape)
                .clickable(
                    interactionSource = centerInteraction,
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickResumeClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Quick Play / Resume",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "nav_item_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0x227C3AED) else Color.Transparent
            )
            .border(
                border = if (isSelected)
                    BorderStroke(1.dp, Color(0x44C084FC))
                else
                    BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
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
                    modifier = Modifier.size(21.dp)
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

            Spacer(modifier = Modifier.height(2.dp))

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
