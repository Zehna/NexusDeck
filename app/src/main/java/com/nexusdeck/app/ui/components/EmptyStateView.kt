package com.nexusdeck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.ui.theme.NexusPalette

/**
 * Tampilan status kosong saat tidak ada game di pustaka lokal.
 * v1.4.0 — katalog unduhan ROM dihapus: tombolnya mengarah ke tab
 * Library (tambah folder game milik pengguna), bukan lagi ke Web.
 */
@Composable
fun EmptyStateView(
    searchQuery: String,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(NexusPalette.SurfaceCardElevated)
                .border(1.dp, NexusPalette.CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = NexusPalette.PrimaryViolet,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (searchQuery.isNotBlank())
                "Tidak ada game untuk \"$searchQuery\""
            else
                "Belum Ada Game di Pustaka",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 17.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tambahkan folder game milikmu di tab Library — NexusDeck " +
                "memindai file game yang sudah ada di HP-mu, tanpa unduhan.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = NexusPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenLibrary()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NexusPalette.PrimaryViolet,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Buka Tab Library",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
