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
 * Modul Pengelola BIOS / Firmware.
 *
 * BIOS PS1/PS2 adalah firmware berhak cipta Sony — NexusDeck TIDAK
 * menyediakan, mengunduh, atau menautkan unduhan BIOS. Pengguna
 * mengimpor BIOS miliknya sendiri (dump dari konsol sendiri) lewat
 * wizard emulator masing-masing; lihat layar Panduan BIOS di tab
 * Library. Satu-satunya berkas yang diunduh otomatis adalah firmware
 * PS3 (PS3UPDAT.PUP), karena Sony mendistribusikannya gratis lewat
 * server update resminya.
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
        // Tidak ada sumber unduhan: BIOS PS1 adalah firmware berhak
        // cipta Sony. Impor manual lewat wizard DuckStation.
        downloadUrl = "",
        estimatedSizeLabel = "512 KB",
        description = "PlayStation 1 (PSX) System BIOS SCPH-1001 NTSC-U untuk DuckStation. Impor manual lewat wizard DuckStation."
    )

    val ps2Bios = BiosEntry(
        id = "bios_ps2_scph39001",
        fileName = "scph39001.bin",
        platform = GamePlatform.PS2,
        // Tidak ada sumber unduhan: BIOS PS2 adalah firmware berhak
        // cipta Sony. Impor manual lewat wizard AetherSX2/NetherSX2.
        downloadUrl = "",
        estimatedSizeLabel = "4 MB",
        description = "PlayStation 2 Official NTSC BIOS SCPH-39001 untuk NetherSX2/AetherSX2. Impor manual lewat wizard emulator."
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
     *
     * BIOS PS1/PS2 TIDAK diunduh: firmware berhak cipta Sony itu harus
     * diimpor pengguna sendiri (dump dari konsol sendiri) lewat wizard
     * emulator masing-masing. Jika belum ada, fungsi ini mengembalikan
     * null dan game tetap diluncurkan — emulator akan menampilkan
     * permintaan BIOS-nya sendiri. Hanya firmware PS3 (PS3UPDAT.PUP)
     * yang diunduh, karena Sony mendistribusikannya gratis lewat server
     * update resminya.
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

        if (entry.platform != GamePlatform.PS3) {
            Log.i(TAG, "BIOS ${entry.fileName} belum ada — impor manual lewat wizard emulator (lihat Panduan BIOS).")
            return@withContext null
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
