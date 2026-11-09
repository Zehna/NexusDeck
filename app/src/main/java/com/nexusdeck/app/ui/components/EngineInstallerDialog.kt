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
import com.nexusdeck.app.core.engine.EngineDownloadKind
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
        rawError.contains("Play Store") ->
            rawError
        rawError.startsWith("Tautan") || rawError.startsWith("File") || rawError.startsWith("Server") ->
            rawError
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
 * Muncul saat engine emulator belum terpasang di HP.
 *
 * v1.2.0 — tiga jalur pemasangan sesuai jenis engine (bukan lagi "selalu unduh
 * APK dari GitHub"):
 * - GITHUB_APK: unduh APK langsung dengan pre-flight LinkVerifier. Selama
 *   resolusi URL + pemeriksaan berlangsung, dialog menampilkan status
 *   "Memeriksa…" dengan progres tak tentu — bukan lagi 0% dan 0,0 MB yang
 *   diam seperti di video laporan user.
 * - PLAY_STORE: tombol "Pasang via Play Store" membuka aplikasi Play Store
 *   (fallback halaman web). Tombol browser membuka halaman Play Store yang
 *   sudah terverifikasi — bukan lagi URL GitHub 404.
 * - UNAVAILABLE: tidak ada tombol pemasangan sama sekali; dialog menjelaskan
 *   dengan jujur kenapa engine belum bisa dipasang.
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

    var isResolving by remember { mutableStateOf(false) }
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

    val isUnavailable = engineInfo.downloadKind == EngineDownloadKind.UNAVAILABLE
    val isPlayStore = engineInfo.downloadKind == EngineDownloadKind.PLAY_STORE
    val isBusy = isResolving || isDownloading

    Dialog(onDismissRequest = {
        // Boleh ditutup selama pemeriksaan (coroutine ikut dibatalkan);
        // jangan tutup saat byte sedang mengalir.
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
                            isBusy -> Icons.Default.Download
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
                        isResolving -> "Memeriksa ${engineInfo.name}..."
                        isDownloading -> "Mengunduh ${engineInfo.name}..."
                        downloadedApkFile != null -> "Menunggu Instalasi"
                        isUnavailable -> "Engine ${engineInfo.name} Belum Tersedia"
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
                        isResolving ->
                            "Memeriksa ketersediaan engine di server. Sebentar..."
                        isDownloading ->
                            "Mengunduh file APK resmi dari GitHub Releases. Mohon tunggu sejenak..."
                        downloadedApkFile != null ->
                            "Paket APK ${engineInfo.name} berhasil diunduh. Silakan konfirmasi pemasangan pada layar pop-up Android Anda."
                        errorMessage != null ->
                            formatFriendlyErrorMessage(errorMessage)
                        isUnavailable ->
                            engineInfo.unavailableReason
                                ?: "Engine ${engineInfo.name} belum tersedia untuk diunduh saat ini."
                        isPlayStore ->
                            "Engine ${engineInfo.name} tersedia di Google Play Store. Pasang dari sana — tanpa unduh file APK manual."
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
                                text = when {
                                    isUnavailable -> "Status"
                                    isPlayStore -> "Sumber"
                                    else -> "Estimasi Ukuran"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                            Text(
                                text = when {
                                    isUnavailable -> "Tidak tersedia"
                                    isPlayStore -> "Google Play Store"
                                    else -> engineInfo.estimatedSizeLabel
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NexusPalette.AccentVioletLight
                                )
                            )
                        }
                    }
                }

                // Progress Bar saat memeriksa / mengunduh
                AnimatedVisibility(
                    visible = isBusy,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isResolving) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = NexusPalette.PrimaryViolet,
                                trackColor = NexusPalette.BorderCard
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Memeriksa ketersediaan engine...",
                                style = MaterialTheme.typography.labelSmall.copy(color = NexusPalette.TextMuted)
                            )
                        } else {
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
                } else if (isUnavailable) {
                    // Tidak ada jalur pemasangan — jangan tawarkan tombol unduhan palsu.
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.BorderCard),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusPalette.TextSecondary)
                    ) {
                        Text("Tutup")
                    }
                } else if (isPlayStore) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val opened = EngineManager.openPlayStore(context, engineInfo)
                                if (!opened) {
                                    Toast.makeText(
                                        context,
                                        "Tidak dapat membuka Play Store. Coba lewat browser.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusPalette.PrimaryViolet,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pasang via Play Store", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.BorderCard),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusPalette.TextSecondary)
                        ) {
                            Text("Batal")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buka halaman Play Store di browser eksternal sebagai fallback
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
                            text = "Buka di Google Play (Browser)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = NexusPalette.NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (!isBusy) {
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
                                isResolving = true
                                isDownloading = false
                                downloadProgress = 0f
                                downloadedBytes = 0L
                                totalBytes = 0L
                                errorMessage = null
                                coroutineScope.launch {
                                    EngineManager.downloadEngineApk(context, engineInfo)
                                        .collectLatest { progress ->
                                            when (progress) {
                                                is EngineDownloadProgress.Resolving -> {
                                                    isResolving = true
                                                    isDownloading = false
                                                }
                                                is EngineDownloadProgress.Downloading -> {
                                                    isResolving = false
                                                    isDownloading = true
                                                    downloadProgress = progress.progress
                                                    downloadedBytes = progress.downloadedBytes
                                                    totalBytes = progress.totalBytes
                                                }
                                                is EngineDownloadProgress.Completed -> {
                                                    isResolving = false
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
                                                    isResolving = false
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

                    // Unduh via browser eksternal sebagai fallback — URL ini sudah
                    // terverifikasi hidup (v1.2.0), bukan lagi URL GitHub 404.
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
