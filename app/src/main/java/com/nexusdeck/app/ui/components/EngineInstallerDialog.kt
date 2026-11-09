package com.nexusdeck.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nexusdeck.app.core.engine.EngineDownloadProgress
import com.nexusdeck.app.core.engine.EngineInfo
import com.nexusdeck.app.core.engine.EngineManager
import com.nexusdeck.app.ui.theme.NexusPalette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

const val FALLBACK_BROWSER_BUTTON_LABEL = "Unduh Manual dari Browser"

fun formatFriendlyErrorMessage(rawError: String?): String {
    if (rawError.isNullOrBlank()) {
        return "Gagal mengunduh APK engine. Periksa koneksi internet Anda atau unduh manual."
    }
    return when {
        rawError.contains("404") ->
            "File APK rilis belum tersedia di server (HTTP 404). Silakan unduh manual langsung dari browser."
        rawError.contains("timeout", ignoreCase = true) ||
                rawError.contains("timed out", ignoreCase = true) ||
                rawError.contains("koneksi", ignoreCase = true) ||
                rawError.contains("connect", ignoreCase = true) ->
            "Koneksi internet terputus saat mengunduh APK engine. Silakan coba lagi atau gunakan browser."
        else ->
            "Gagal mengunduh APK engine ($rawError). Silakan coba lagi atau unduh manual dari browser."
    }
}

/**
 * Dialog Instalasi Engine Cerdas (Smart In-App Engine Installer Dialog)
 * Muncul saat engine emulator belum terpasang di HP. Menyediakan unduhan APK
 * latar belakang dari GitHub Releases dan pemicu instalasi 1-tap via FileProvider.
 */
@Composable
fun EngineInstallerDialog(
    engineInfo: EngineInfo,
    gameTitle: String,
    onDismiss: () -> Unit,
    onLaunchAfterInstall: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Memeriksa secara reaktif apakah aplikasi sudah berhasil terpasang di sistem
    var isActuallyInstalled by remember {
        mutableStateOf(EngineManager.isEngineInstalled(context, engineInfo))
    }

    Dialog(onDismissRequest = {
        if (!isDownloading) onDismiss()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NexusPalette.BorderCard, RoundedCornerShape(20.dp)),
            color = NexusPalette.SurfaceCard,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            brush = if (isActuallyInstalled)
                                NexusPalette.CyanNeonGradient
                            else
                                NexusPalette.HorizontalVioletGradient
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isActuallyInstalled -> Icons.Default.CheckCircle
                            isDownloading -> Icons.Default.Download
                            errorMessage != null -> Icons.Default.ErrorOutline
                            else -> Icons.Default.SportsEsports
                        },
                        contentDescription = "Engine Status",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = when {
                        isActuallyInstalled -> "Engine Siap Dimainkan!"
                        isDownloading -> "Mengunduh ${engineInfo.name}..."
                        downloadedApkFile != null -> "Menunggu Instalasi"
                        else -> "Engine ${engineInfo.name} Diperlukan"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 19.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle / Body text
                Text(
                    text = when {
                        isActuallyInstalled ->
                            "Engine ${engineInfo.name} telah aktif terpasang di HP Anda. Siap meluncurkan $gameTitle secara langsung!"
                        isDownloading ->
                            "Mengunduh file APK resmi dari GitHub Releases. Mohon tunggu sejenak..."
                        downloadedApkFile != null ->
                            "Paket APK ${engineInfo.name} berhasil diunduh. Silakan konfirmasi pemasangan pada layar pop-up Android Anda."
                        errorMessage != null ->
                            formatFriendlyErrorMessage(errorMessage)
                        else ->
                            "Engine ${engineInfo.name} diperlukan untuk memutar game \"$gameTitle\" (Ukuran: ${engineInfo.estimatedSizeLabel}). Unduh dan pasang sekarang?"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = NexusPalette.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Info Engine (Platform & Ukuran)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = NexusPalette.BackgroundDeep.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.BorderCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Platform",
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                            Text(
                                text = engineInfo.platform.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NexusPalette.NeonCyan
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Package",
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                            Text(
                                text = engineInfo.primaryPackageName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = NexusPalette.TextSecondary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Estimasi Ukuran",
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                            Text(
                                text = engineInfo.estimatedSizeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NexusPalette.AccentVioletLight
                                )
                            )
                        }
                    }
                }

                // Progress Bar saat mengunduh
                AnimatedVisibility(
                    visible = isDownloading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NexusPalette.PrimaryViolet,
                            trackColor = NexusPalette.BorderCard
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NexusPalette.NeonCyan
                                )
                            )
                            val downloadedMb = downloadedBytes.toDouble() / (1024.0 * 1024.0)
                            val totalMb = totalBytes.toDouble() / (1024.0 * 1024.0)
                            Text(
                                text = String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                if (isActuallyInstalled) {
                    Button(
                        onClick = {
                            onDismiss()
                            onLaunchAfterInstall?.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusPalette.PrimaryViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("▶ Luncurkan Game Sekarang", fontWeight = FontWeight.Bold)
                    }
                } else if (downloadedApkFile != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isActuallyInstalled = EngineManager.isEngineInstalled(context, engineInfo)
                                if (isActuallyInstalled) {
                                    onDismiss()
                                    onLaunchAfterInstall?.invoke()
                                } else {
                                    // Panggil ulang prompt installer jika user menutupnya tidak sengaja
                                    downloadedApkFile?.let { apk ->
                                        try {
                                            val intent = EngineManager.buildInstallIntent(context, apk)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Gagal membuka installer: ${e.localizedMessage ?: "FileProvider error"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusPalette.PrimaryViolet,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚡ Cek Status & Pasang APK", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.BorderCard),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusPalette.TextSecondary)
                        ) {
                            Text("Tutup")
                        }
                    }
                } else if (!isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.BorderCard),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusPalette.TextSecondary)
                        ) {
                            Text("Batal")
                        }

                        Button(
                            onClick = {
                                isDownloading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    EngineManager.downloadEngineApk(context, engineInfo)
                                        .collectLatest { progress ->
                                            when (progress) {
                                                is EngineDownloadProgress.Downloading -> {
                                                    downloadProgress = progress.progress
                                                    downloadedBytes = progress.downloadedBytes
                                                    totalBytes = progress.totalBytes
                                                }
                                                is EngineDownloadProgress.Completed -> {
                                                    isDownloading = false
                                                    downloadedApkFile = progress.apkFile
                                                    // Otomatis buka prompt instalasi Android via FileProvider
                                                    try {
                                                        val intent = EngineManager.buildInstallIntent(context, progress.apkFile)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "Buka APK dari folder unduhan: ${progress.apkFile.name}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                                is EngineDownloadProgress.Failed -> {
                                                    isDownloading = false
                                                    errorMessage = progress.error
                                                }
                                            }
                                        }
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusPalette.PrimaryViolet,
                                contentColor = Color.White
                            )
                        ) {
                            Text("⚡ Pasang Otomatis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Unduh via browser eksternal sebagai fallback
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(engineInfo.downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka browser.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = NexusPalette.NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = FALLBACK_BROWSER_BUTTON_LABEL,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = NexusPalette.NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
