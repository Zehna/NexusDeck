package com.nexusdeck.app.ui.bios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.core.bios.BiosManager
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.ui.theme.NexusPalette

/**
 * Layar Panduan BIOS & Firmware (Fase 1) — TEKS SAJA.
 *
 * Layar ini tidak memilih, mengunduh, atau memasok file BIOS apa pun:
 * BIOS PlayStation adalah firmware milik Sony dan hanya boleh berasal
 * dari dump konsol milik pengguna sendiri. Layar ini menjelaskan file
 * apa yang dicari tiap emulator, di mana NexusDeck memeriksanya, dan
 * bagaimana cara mengimpornya lewat wizard emulator masing-masing.
 */
@Composable
fun BiosGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ps1Ready = BiosManager.isBiosOrFirmwareReady(context, GamePlatform.PS1)
    val ps2Ready = BiosManager.isBiosOrFirmwareReady(context, GamePlatform.PS2)
    val ps3Ready = BiosManager.isBiosOrFirmwareReady(context, GamePlatform.PS3)
    val biosDir = BiosManager.getBiosDir(context).absolutePath
    val firmwareDir = BiosManager.getFirmwareDir(context).absolutePath

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusPalette.DeepDarkBlack)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Panduan BIOS & Firmware",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BIOS PlayStation adalah firmware milik Sony. NexusDeck tidak " +
                            "menyediakan, mengunduh, atau menautkan unduhan BIOS. Cara yang " +
                            "sah: dump BIOS dari konsol PlayStation milikmu sendiri " +
                            "(untuk PS2: FreeMcBoot/FreeDVDBoot + biosdrain; ikuti panduan " +
                            "dumping resmi PCSX2). DuckStation akan meminta BIOS bila " +
                            "digunakan sebagai emulator luar; PS2 tetap meminta BIOS-nya " +
                            "sendiri.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NexusPalette.SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Di aplikasi gabungan, game PS1 tidak butuh BIOS " +
                            "(PCSX-ReARMed memakai HLE BIOS bawaan). Pemeriksaan " +
                            "scph1001.bin di bawah ini hanya berlaku bila kamu memainkan " +
                            "PS1 lewat DuckStation (aplikasi luar).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                BiosSystemSection(
                    title = "PlayStation 1 — DuckStation",
                    isReady = ps1Ready,
                    readyLabel = "BIOS PS1 siap",
                    missingLabel = "BIOS PS1 belum ada",
                    filesLabel = "File yang dikenali (masing-masing tepat 512 KB):",
                    files = listOf(
                        "scph1001.bin", "scph5501.bin", "scph5500.bin",
                        "scph5502.bin", "scph1002.bin", "scph7502.bin"
                    ),
                    locationLabel = "NexusDeck memeriksa file scph1001.bin di:",
                    location = biosDir,
                    steps = listOf(
                        "Buka DuckStation dan jalankan setup wizard pertama kali.",
                        "Di langkah Import BIOS, pilih folder yang berisi file BIOS " +
                            "(DuckStation mendeteksi region otomatis).",
                        "Atau nanti: Settings → BIOS → Browse → pilih folder BIOS → Refresh List."
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                BiosSystemSection(
                    title = "PlayStation 2 — AetherSX2 / NetherSX2 / ARMSX2",
                    isReady = ps2Ready,
                    readyLabel = "BIOS PS2 siap",
                    missingLabel = "BIOS PS2 belum ada",
                    filesLabel = "File yang dikenali (sekitar 4 MB):",
                    files = listOf(
                        "scph39001.bin", "scph70012.bin", "scph50001.bin",
                        "scph55012.bin", "scph75004.bin",
                        "SCPH-10000 / 30000 / 50000 / 70000 / 90000"
                    ),
                    locationLabel = "NexusDeck memeriksa file scph39001.bin di:",
                    location = biosDir,
                    steps = listOf(
                        "Buka AetherSX2 / NetherSX2 / ARMSX2 dan jalankan setup wizard.",
                        "Di layar import BIOS, ketuk + lalu pilih file .bin hasil dump " +
                            "(bukan arsip .zip).",
                        "Pilih tag region yang sesuai, lalu Next.",
                        "Setelah itu: Settings → Game List → Game Directories untuk " +
                            "menunjuk folder game."
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                BiosSystemSection(
                    title = "PlayStation 3 — aPS3e / ARMSX3",
                    isReady = ps3Ready,
                    readyLabel = "Firmware PS3 siap",
                    missingLabel = "Firmware PS3 belum ada",
                    filesLabel = "Yang dibutuhkan (bukan BIOS):",
                    files = listOf("PS3UPDAT.PUP — firmware resmi Sony, sekitar 196 MB"),
                    locationLabel = "NexusDeck memeriksa file PS3UPDAT.PUP di:",
                    location = firmwareDir,
                    steps = listOf(
                        "aPS3e: menu titik enam → install firmware → pilih file PS3UPDAT.PUP.",
                        "ARMSX3: langkah 2 dari 3 pada setup (decrypt/precompile, " +
                            "beberapa menit, sekali saja).",
                        "NexusDeck mengunduh firmware ini otomatis dari server update " +
                            "resmi Sony saat game PS3 pertama diluncurkan — Sony " +
                            "mendistribusikannya gratis."
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                BiosSystemSection(
                    title = "PSP, GBA, NDS, 3DS — tanpa BIOS",
                    isReady = true,
                    readyLabel = "Tidak butuh BIOS",
                    missingLabel = "Tidak butuh BIOS",
                    filesLabel = "Emulator yang langsung jalan:",
                    files = listOf(
                        "PPSSPP (PSP)", "melonDS (NDS)",
                        "Azahar (3DS)", "Lemuroid (GBA)"
                    ),
                    locationLabel = "",
                    location = "",
                    steps = listOf(
                        "Tidak ada file BIOS yang perlu disiapkan untuk sistem-sistem ini."
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusPalette.PrimaryViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Kembali ke Pustaka")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BiosSystemSection(
    title: String,
    isReady: Boolean,
    readyLabel: String,
    missingLabel: String,
    filesLabel: String,
    files: List<String>,
    locationLabel: String,
    location: String,
    steps: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(isReady = isReady, readyLabel = readyLabel, missingLabel = missingLabel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = filesLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        files.forEach { file ->
            Text(
                text = "• $file",
                style = MaterialTheme.typography.bodyMedium,
                color = NexusPalette.TextSecondary,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }

        if (locationLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = NexusPalette.TextSecondary
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = NexusPalette.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusChip(
    isReady: Boolean,
    readyLabel: String,
    missingLabel: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isReady) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isReady) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = if (isReady) readyLabel else missingLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isReady) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        }
    }
}
