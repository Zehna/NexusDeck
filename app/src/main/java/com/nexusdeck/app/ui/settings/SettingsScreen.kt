package com.nexusdeck.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusdeck.app.core.achievements.RetroAchievementsService
import com.nexusdeck.app.core.model.HardwareSpec
import com.nexusdeck.app.core.storage.NexusStorageManager
import com.nexusdeck.app.core.storage.StorageSpaceInfo
import com.nexusdeck.app.ui.theme.NexusPalette
import com.taras.nexusplay.app.mobile.feature.main.MainActivity

/**
 * Halaman Pengaturan Terpadu NexusDeck (Commercial-Grade OLED Console Style)
 * Mengelola:
 * 1. 📁 Custom Storage & MicroSD Manager via SAF (Storage Access Framework)
 * 2. 🏆 RetroAchievements Akun & Integrasi Trophy REST API
 * 3. ⚡ GPU Driver & Auto-Tuning Engine Diagnostics
 */
@Composable
fun SettingsScreen(
    hardwareSpec: HardwareSpec,
    installedCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Services
    val raService = remember(context) { RetroAchievementsService(context) }

    // State Penyimpanan
    var storageInfo by remember {
        mutableStateOf(NexusStorageManager.getAvailableStorageSpace(context))
    }
    var currentStorageLabel by remember {
        mutableStateOf(NexusStorageManager.resolveStorageDisplayName(NexusStorageManager.getPersistedCustomStorageTreeUri(context)))
    }
    var isMicroSdActive by remember {
        mutableStateOf(NexusStorageManager.isMicroSdStorage(NexusStorageManager.getPersistedCustomStorageTreeUri(context)))
    }

    // SAF ActivityResultContract Launcher untuk Folder/MicroSD
    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                NexusStorageManager.persistCustomStorageTreeUri(context, treeUri)

                storageInfo = NexusStorageManager.getAvailableStorageSpace(context)
                currentStorageLabel = NexusStorageManager.resolveStorageDisplayName(treeUri)
                isMicroSdActive = NexusStorageManager.isMicroSdStorage(treeUri)

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "Folder penyimpanan berhasil diubah ke: $currentStorageLabel", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengambil izin folder: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // State RetroAchievements
    var raCredentials by remember { mutableStateOf(raService.getCredentials()) }
    var usernameInput by remember(raCredentials) { mutableStateOf(raCredentials.username) }
    var apiKeyInput by remember(raCredentials) { mutableStateOf(raCredentials.webApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    // State GPU & Driver Toggles
    var turnipCustomDriver by remember {
        mutableStateOf(hardwareSpec.gpuModel.contains("adreno", ignoreCase = true))
    }
    var asyncShaderCache by remember { mutableStateOf(true) }
    var sustainedThermalMode by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 1. SECTION: PENYIMPANAN GAME & MICROSD MANAGER (SAF)
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush = if (isMicroSdActive) NexusPalette.CyanNeonGradient else NexusPalette.VerticalVioletGradient
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMicroSdActive) Icons.Default.SdCard else Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Penyimpanan Game & MicroSD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (isMicroSdActive) "Kartu MicroSD Eksternal Aktif (SAF)" else "Penyimpanan Internal Aplikasi",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMicroSdActive) NexusPalette.NeonCyan else NexusPalette.SecondaryPurple
                        )
                    }

                    // Badge Tipe Penyimpanan
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isMicroSdActive) NexusPalette.NeonCyan else NexusPalette.PrimaryViolet).copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            (if (isMicroSdActive) NexusPalette.NeonCyan else NexusPalette.PrimaryViolet).copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (isMicroSdActive) "MicroSD" else "Internal",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isMicroSdActive) NexusPalette.NeonCyan else NexusPalette.SecondaryPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detail Path Folder Saat Ini
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111019),
                    border = BorderStroke(1.dp, Color(0xFF232135)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = NexusPalette.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentStorageLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = NexusPalette.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bar Visual Kapasitas Memori (Used vs Free GB)
                val usedRatio = storageInfo.usedPercent / 100f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Kapasitas Memori",
                            style = MaterialTheme.typography.labelMedium,
                            color = NexusPalette.TextSecondary
                        )
                        Text(
                            text = "Sisa ${storageInfo.freeStorageFormatted} dari ${storageInfo.totalStorageFormatted}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    // Progress Bar OLED Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF262438))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(usedRatio)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(NexusPalette.PrimaryViolet, NexusPalette.SecondaryPurple, NexusPalette.NeonCyan)
                                    )
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${storageInfo.usedPercent.toInt()}% Terpakai (${storageInfo.usedStorageFormatted})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusPalette.TextMuted
                        )

                        // Status Ambang Batas Aman (2.5 GB)
                        val isSafe = storageInfo.freeBytes >= NexusStorageManager.MIN_FREE_STORAGE_THRESHOLD_BYTES
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSafe) Color(0xFF22C55E) else Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isSafe) "Status Aman (> 2.5 GB)" else "Peringatan Memori Kritis (< 2.5 GB)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSafe) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Aksi: Pilih Folder SAF & Reset Default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            safFolderLauncher.launch(null)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusPalette.PrimaryViolet
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "📁 Pilih Folder / MicroSD",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    if (NexusStorageManager.isCustomStorageConfigured(context)) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                NexusStorageManager.resetToDefaultStorage(context)
                                storageInfo = NexusStorageManager.getAvailableStorageSpace(context)
                                currentStorageLabel = NexusStorageManager.resolveStorageDisplayName(null)
                                isMicroSdActive = false
                                Toast.makeText(context, "Penyimpanan dikembalikan ke default.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NexusPalette.CardBorder)
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexusPalette.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusPalette.PrimaryViolet
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Buka Pustaka Game (NexusPlay)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Titik masuk sementara Fase 2 — pustaka dan pemutar game masih layar NexusPlay",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 2. SECTION: RETROACHIEVEMENTS INTEGRATION (REST API & TROPHIES)
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🏆", fontSize = 18.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RetroAchievements",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (raCredentials.isLoggedIn) "Akun Terhubung: @${raCredentials.username}" else "Integrasi Trofi Game Klasik",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (raCredentials.isLoggedIn) Color(0xFFF59E0B) else NexusPalette.TextSecondary
                        )
                    }

                    // Badge Status Koneksi
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (raCredentials.isLoggedIn) Color(0xFF22C55E) else NexusPalette.TextMuted).copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            (if (raCredentials.isLoggedIn) Color(0xFF22C55E) else NexusPalette.TextMuted).copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (raCredentials.isLoggedIn) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (raCredentials.isLoggedIn) Color(0xFF22C55E) else NexusPalette.TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Form: Username
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Username RetroAchievements") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NexusPalette.TextSecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusPalette.PrimaryViolet,
                        unfocusedBorderColor = NexusPalette.CardBorder,
                        focusedLabelColor = NexusPalette.SecondaryPurple,
                        unfocusedLabelColor = NexusPalette.TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input Form: Web API Key
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Web API Key") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NexusPalette.TextSecondary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle API Key Visibility",
                                tint = NexusPalette.TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusPalette.PrimaryViolet,
                        unfocusedBorderColor = NexusPalette.CardBorder,
                        focusedLabelColor = NexusPalette.SecondaryPurple,
                        unfocusedLabelColor = NexusPalette.TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Dapatkan Web API Key gratis di menu Settings > Keys akun retroachievements.org Anda.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tombol Simpan / Putuskan Akun
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (usernameInput.isNotBlank() && apiKeyInput.isNotBlank()) {
                                raService.saveCredentials(usernameInput, apiKeyInput)
                                raCredentials = raService.getCredentials()
                                Toast.makeText(context, "Akun RetroAchievements berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Username dan Web API Key wajib diisi.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (raCredentials.isLoggedIn) "✓ Perbarui Akun" else "Simpan & Hubungkan",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    if (raCredentials.isLoggedIn) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                raService.logout()
                                raCredentials = raService.getCredentials()
                                usernameInput = ""
                                apiKeyInput = ""
                                Toast.makeText(context, "Akun RetroAchievements telah diputus.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Putuskan",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. SECTION: GPU, DRIVER & AUTO-TUNING DIAGNOSTICS
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NexusPalette.SurfaceCard,
            border = BorderStroke(1.dp, NexusPalette.CardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Driver & Auto-Tuning Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Optimasi Vulkan & Pipeline Grafis",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detail SoC & Hardware
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111019),
                    border = BorderStroke(1.dp, Color(0xFF232135)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SettingInfoRow("Chipset / SoC", hardwareSpec.socModel)
                        SettingInfoRow("GPU Renderer", hardwareSpec.gpuModel)
                        SettingInfoRow("System RAM", "${hardwareSpec.totalRamGb} GB LPDDR")
                        SettingInfoRow("Vulkan API", hardwareSpec.vulkanVersion)
                        SettingInfoRow("ROM Terpasang", "$installedCount Game")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Driver Turnip Custom
                SettingToggleRow(
                    title = "Mesa Turnip v24.2.0 Custom Vulkan",
                    subtitle = "Injeksi driver kustom dengan Adreno direct-submission",
                    checked = turnipCustomDriver,
                    onCheckedChange = { turnipCustomDriver = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle DXVK Async Shader Pipeline
                SettingToggleRow(
                    title = "DXVK 2.3.1 Async Shader Pipeline",
                    subtitle = "Hilangkan kompilasi shader stutter pada emulasi berat",
                    checked = asyncShaderCache,
                    onCheckedChange = { asyncShaderCache = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle Smart Thermal Frame Pacing
                SettingToggleRow(
                    title = "Smart Thermal Frame Pacing",
                    subtitle = "Pertahankan clock rate stabil agar perangkat tidak overheat",
                    checked = sustainedThermalMode,
                    onCheckedChange = { sustainedThermalMode = it }
                )
            }
        }

        // =========================================================================
        // 4. SECTION: TENTANG ARSITEKTUR NEXUSDECK
        // =========================================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF12111A),
            border = BorderStroke(1.dp, Color(0xFF232135))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NexusDeck Universal Gaming Orchestrator",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Commercial-Grade Architecture v1.0.0 • SAF Storage Engine • ROM Compression Advisor (.chd/.cso) • RetroAchievements REST API • Multi-Source Resilient Scraper",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusPalette.TextMuted
                )
            }
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NexusPalette.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NexusPalette.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexusPalette.PrimaryViolet,
                uncheckedThumbColor = NexusPalette.TextSecondary,
                uncheckedTrackColor = Color(0xFF262438)
            )
        )
    }
}
