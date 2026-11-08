package com.nexusdeck.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.ui.theme.NexusPalette

enum class CategoryPillFilter(
    val label: String,
    val platform: GamePlatform?,
    val isFavoritesOnly: Boolean = false,
    val featuredOnly: Boolean = false
) {
    ALL("All", null, false, false),
    FAVORITES("Favorit ⭐", null, true, false),
    CATALOG("Catalog", null, false, true),
    PSP("PSP", GamePlatform.PSP, false, false),
    NINTENDO("Nintendo", GamePlatform.NINTENDO, false, false),
    PS2("PlayStation 2", GamePlatform.PS2, false, false),
    PC("PC Games", GamePlatform.PC, false, false),
    PS3("PlayStation 3", GamePlatform.PS3, false, false)
}

@Composable
fun ConsoleFilterTabs(
    selectedFilter: CategoryPillFilter,
    onSelectFilter: (CategoryPillFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryPillFilter.entries.forEach { pill ->
            val isSelected = selectedFilter == pill

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(brush = NexusPalette.VioletGradient)
                        .clickable { onSelectFilter(pill) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pill.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = NexusPalette.SurfaceCard,
                    border = BorderStroke(1.dp, NexusPalette.CardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectFilter(pill) }
                ) {
                    Text(
                        text = pill.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusPalette.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

fun nextCategoryFilter(current: CategoryPillFilter): CategoryPillFilter {
    val entries = CategoryPillFilter.entries
    val nextIdx = (entries.indexOf(current) + 1) % entries.size
    return entries[nextIdx]
}
