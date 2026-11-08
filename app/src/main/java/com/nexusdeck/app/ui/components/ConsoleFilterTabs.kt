package com.nexusdeck.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform

data class PlatformTabItem(
    val platform: GamePlatform?,
    val title: String,
    val shortCode: String,
    val count: Int
)

@Composable
fun ConsoleFilterTabs(
    games: List<GameItem>,
    selectedPlatform: GamePlatform?,
    onSelectPlatform: (GamePlatform?) -> Unit,
    modifier: Modifier = Modifier
) {
    val pcCount = games.count { it.platform == GamePlatform.PC }
    val ps2Count = games.count { it.platform == GamePlatform.PS2 }
    val ps3Count = games.count { it.platform == GamePlatform.PS3 }

    val tabs = listOf(
        PlatformTabItem(
            platform = null,
            title = "Semua",
            shortCode = "ALL",
            count = games.size
        ),
        PlatformTabItem(
            platform = GamePlatform.PC,
            title = "PC Games",
            shortCode = "PC",
            count = pcCount
        ),
        PlatformTabItem(
            platform = GamePlatform.PS2,
            title = "PlayStation 2",
            shortCode = "PS2",
            count = ps2Count
        ),
        PlatformTabItem(
            platform = GamePlatform.PS3,
            title = "PlayStation 3",
            shortCode = "PS3",
            count = ps3Count
        )
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedPlatform == tab.platform
                ConsoleTabPill(
                    tab = tab,
                    isSelected = isSelected,
                    onClick = { onSelectPlatform(tab.platform) }
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Petunjuk Tombol Stik Y / Triangle untuk Ganti Tab
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF131C2E),
            border = BorderStroke(1.dp, Color(0xFF283754)),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onSelectPlatform(nextPlatformTab(selectedPlatform))
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEAB308).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFFEAB308))
                ) {
                    Text(
                        text = "Y / △",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFFFACC15),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ganti Tab",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun ConsoleTabPill(
    tab: PlatformTabItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF131C2E)
    val contentColor = if (isSelected) Color(0xFF061018) else Color(0xFFE2E8F0)
    val borderColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF23304A)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                color = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) Color(0xFF061018).copy(alpha = 0.2f) else Color(0xFF090D16)
            ) {
                Text(
                    text = "${tab.shortCode} (${tab.count})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isSelected) Color(0xFF061018) else Color(0xFF00E5FF),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun nextPlatformTab(current: GamePlatform?): GamePlatform? {
    return when (current) {
        null -> GamePlatform.PC
        GamePlatform.PC -> GamePlatform.PS2
        GamePlatform.PS2 -> GamePlatform.PS3
        GamePlatform.PS3 -> null
    }
}
