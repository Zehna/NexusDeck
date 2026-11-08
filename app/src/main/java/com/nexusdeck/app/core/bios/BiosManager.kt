package com.nexusdeck.app.core.bios

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nexusdeck.app.core.model.GamePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadata entri BIOS atau Firmware Resmi.
 */
data class BiosEntry(
    val id: String,
    val fileName: String,
    val platform: GamePlatform,
    val downloadUrl: String,
    val estimatedSizeLabel: String,
    val description: String
)

/**
 * Modul Pengunduh & Pengelola BIOS / Firmware Otomatis.
 * Menyediakan repositori resmi publik untuk BIOS PS1, PS2, serta Firmware Resmi Sony PS3.
 * Memastikan berkas tersedia di folder /NexusDeck/bios/ atau /NexusDeck/firmware/ sebelum game dibooting.
 */
object BiosManager {

    private const val TAG = "NexusDeckBiosManager"

    // =========================================================================
    // Registry Berkas BIOS & Firmware Resmi Publik
    // =========================================================================

    val ps1Bios = BiosEntry(
        id = "bios_ps1_scph1001",
        fileName = "scph1001.bin",
        platform = GamePlatform.PS1,
        downloadUrl = "https://archive.org/download/SonyPlayStationBIOS/SCPH1001.BIN",
        estimatedSizeLabel = "512 KB",
        description = "PlayStation 1 (PSX) System BIOS SCPH-1001 NTSC-U untuk DuckStation."
    )

    val ps2Bios = BiosEntry(
        id = "bios_ps2_scph39001",
        fileName = "scph39001.bin",
        platform = GamePlatform.PS2,
        downloadUrl = "https://archive.org/download/ps2-bios-pack/SCPH-39001.bin",
        estimatedSizeLabel = "4 MB",
        description = "PlayStation 2 Official NTSC BIOS SCPH-39001 untuk NetherSX2/AetherSX2."
    )

    val ps3Firmware = BiosEntry(
        id = "firmware_ps3_pup",
        fileName = "PS3UPDAT.PUP",
        platform = GamePlatform.PS3,
        downloadUrl = "http://dus01.ps3.update.playstation.net/update/ps3/image/us/2024_0227_3630f9a5d3f23ffcbba8dfc3bb748a39/PS3UPDAT.PUP",
        estimatedSizeLabel = "~196 MB",
        description = "Sony PlayStation 3 Official System Software Update (PS3UPDAT.PUP)."
    )

    val registeredEntries: List<BiosEntry> = listOf(
        ps1Bios,
        ps2Bios,
        ps3Firmware
    )

    // =========================================================================
    // Direktori Penyimpanan Publik Bersama (/NexusDeck/bios & /NexusDeck/firmware)
    // =========================================================================

    fun getBiosDir(context: Context): File {
        return com.nexusdeck.app.core.storage.NexusStorageManager.getBiosDir(context)
    }

    fun getFirmwareDir(context: Context): File {
        return com.nexusdeck.app.core.storage.NexusStorageManager.getFirmwareDir(context)
    }

    fun getTargetFileForEntry(context: Context, entry: BiosEntry): File {
        val targetDir = if (entry.platform == GamePlatform.PS3) {
            getFirmwareDir(context)
        } else {
            getBiosDir(context)
        }
        return File(targetDir, entry.fileName)
    }

    fun getRequiredEntryForPlatform(platform: GamePlatform): BiosEntry? {
        return when (platform) {
            GamePlatform.PS1 -> ps1Bios
            GamePlatform.PS2 -> ps2Bios
            GamePlatform.PS3 -> ps3Firmware
            else -> null
        }
    }

    fun isBiosOrFirmwareReady(context: Context, platform: GamePlatform): Boolean {
        val entry = getRequiredEntryForPlatform(platform) ?: return true
        val targetFile = getTargetFileForEntry(context, entry)
        return targetFile.exists() && targetFile.length() > 0
    }

    /**
     * Memastikan berkas BIOS atau Firmware yang dibutuhkan konsol telah tersedia di HP.
     * Jika belum ada, otomatis mengunduhnya dari CDN resmi ke folder yang sesuai.
     */
    suspend fun ensureBiosReady(
        context: Context,
        platform: GamePlatform,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val entry = getRequiredEntryForPlatform(platform) ?: return@withContext null
        val targetFile = getTargetFileForEntry(context, entry)

        if (targetFile.exists() && targetFile.length() > 0) {
            Log.i(TAG, "BIOS/Firmware '${entry.fileName}' sudah siap di ${targetFile.absolutePath}")
            onProgress(1f)
            return@withContext targetFile
        }

        Log.i(TAG, "Mengunduh ${entry.fileName} dari ${entry.downloadUrl}...")
        try {
            val url = URL(entry.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NexusDeck/1.1.2 Handheld Engine")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: (4L * 1024 * 1024)
            var downloadedBytes = 0L

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(targetFile).use { output: FileOutputStream ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                        onProgress(progress)
                    }
                    output.flush()
                }
            }

            onProgress(1f)
            Log.i(TAG, "Berhasil mengunduh ${entry.fileName} (${targetFile.length()} bytes)")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengunduh BIOS/Firmware ${entry.fileName}: ${e.message}")
            if (targetFile.exists()) targetFile.delete()
            null
        }
    }
}
