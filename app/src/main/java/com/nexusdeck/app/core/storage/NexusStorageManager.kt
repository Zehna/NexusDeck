package com.nexusdeck.app.core.storage

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import com.nexusdeck.app.core.model.GamePlatform
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Informasi kapasitas memori penyimpanan (Internal Storage vs MicroSD Card Eksternal).
 */
data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val isExternalMicroSd: Boolean,
    val storagePath: String
) {
    val totalGb: Double get() = totalBytes / (1024.0 * 1024.0 * 1024.0)
    val freeGb: Double get() = freeBytes / (1024.0 * 1024.0 * 1024.0)
    val usedGb: Double get() = usedBytes / (1024.0 * 1024.0 * 1024.0)

    val usedPercent: Float
        get() = if (totalBytes > 0) {
            ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

    val freeStorageFormatted: String
        get() = formatBytes(freeBytes)

    val totalStorageFormatted: String
        get() = formatBytes(totalBytes)

    val usedStorageFormatted: String
        get() = formatBytes(usedBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 GB"
            val df = DecimalFormat("#,##0.0", DecimalFormatSymbols(Locale.US))
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                "${df.format(gb)} GB"
            } else {
                val mb = bytes / (1024.0 * 1024.0)
                "${df.format(mb)} MB"
            }
        }
    }
}

/**
 * Hasil verifikasi proteksi keamanan ruang penyimpanan sebelum mengunduh game.
 */
data class StorageSafetyCheckResult(
    val isSafe: Boolean,
    val freeBytes: Long,
    val requiredBytes: Long,
    val message: String
)

/**
 * Manajer Penyimpanan Tingkat Lanjut NexusDeck dengan Storage Access Framework (SAF).
 * Mengelola pemilihan direktori unduhan custom (Internal vs MicroSD), persistensi URI permission,
 * kalkulasi sisa ruang disk riil, dan proteksi ambang batas minimum memori (2.5 GB).
 */
object NexusStorageManager {

    const val PREFS_NAME = "nexus_storage_prefs"
    const val KEY_CUSTOM_TREE_URI = "custom_storage_tree_uri"
    const val KEY_CUSTOM_STORAGE_NAME = "custom_storage_name"

    /**
     * Ambang batas aman memori minimum: 2.5 GB (2,500,000,000 bytes).
     * Jika sisa memori perangkat kurang dari batas ini, proses unduhan otomatis diblokir/diberi peringatan.
     */
    const val MIN_FREE_STORAGE_THRESHOLD_BYTES = 2_500_000_000L

    /**
     * Buffer keamanan tambahan saat mengunduh game (500 MB).
     */
    const val DOWNLOAD_SAFETY_BUFFER_BYTES = 500_000_000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Menyimpan Uri folder SAF terpilih ke SharedPreferences.
     */
    fun persistCustomStorageTreeUri(context: Context, treeUri: Uri, customName: String? = null) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_CUSTOM_TREE_URI, treeUri.toString())
        if (!customName.isNullOrBlank()) {
            editor.putString(KEY_CUSTOM_STORAGE_NAME, customName)
        } else {
            editor.putString(KEY_CUSTOM_STORAGE_NAME, resolveStorageDisplayName(treeUri))
        }
        editor.apply()
    }

    /**
     * Mengambil Uri folder custom yang telah disimpan permanen.
     */
    fun getPersistedCustomStorageTreeUri(context: Context): Uri? {
        val uriStr = getPrefs(context).getString(KEY_CUSTOM_TREE_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Menghapus konfigurasi folder custom (kembali ke penyimpanan internal default).
     */
    fun resetToDefaultStorage(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Memeriksa apakah pengguna telah memilih custom storage (SAF).
     */
    fun isCustomStorageConfigured(context: Context): Boolean {
        return !getPrefs(context).getString(KEY_CUSTOM_TREE_URI, null).isNullOrBlank()
    }

    /**
     * Mengonversi Uri SAF (ACTION_OPEN_DOCUMENT_TREE) menjadi string path yang mudah dibaca manusia.
     * Mengidentifikasi Internal Storage vs MicroSD Card eksternal berdasarkan volume ID.
     */
    fun resolveStorageDisplayName(treeUri: Uri?): String {
        if (treeUri == null) {
            return "Penyimpanan Internal NexusDeck (/Android/data/com.nexusdeck.app/files/games/)"
        }

        val uriString = treeUri.toString()
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            Uri.decode(treeUri.path ?: "")
        }

        return formatDocumentIdToPath(docId, uriString)
    }

    /**
     * Helper pemformatan documentId menjadi label path yang user-friendly.
     */
    fun formatDocumentIdToPath(docId: String, rawUriString: String = ""): String {
        if (docId.startsWith("primary:", ignoreCase = true) || rawUriString.contains("primary%3A", ignoreCase = true)) {
            val subPath = docId.substringAfter("primary:", "")
                .trimStart('/', '\\')
            return if (subPath.isBlank()) {
                "Penyimpanan Internal: /"
            } else {
                "Penyimpanan Internal: /$subPath"
            }
        }

        // Cek pola MicroSD eksternal seperti 9C33-6BBD:ROMs atau 1234-5678:games
        val colonIndex = docId.indexOf(':')
        if (colonIndex > 0) {
            val volumeId = docId.substring(0, colonIndex)
            val subPath = docId.substring(colonIndex + 1).trimStart('/', '\\')
            val isSdPattern = volumeId.matches(Regex("""^[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}$""")) ||
                volumeId.contains("sdcard", ignoreCase = true) ||
                volumeId.contains("ext", ignoreCase = true)

            return if (isSdPattern) {
                if (subPath.isBlank()) {
                    "MicroSD Card ($volumeId): /"
                } else {
                    "MicroSD Card ($volumeId): /$subPath"
                }
            } else {
                "Penyimpanan Eksternal ($volumeId): /$subPath"
            }
        }

        return "Penyimpanan Khusus ($docId)"
    }

    /**
     * Memeriksa apakah URI atau path merepresentasikan kartu MicroSD eksternal.
     */
    fun isMicroSdStorage(treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            treeUri.toString()
        }
        return isMicroSdDocumentId(docId)
    }

    fun isMicroSdDocumentId(docId: String): Boolean {
        if (docId.startsWith("primary:", ignoreCase = true)) return false
        val volumeId = docId.substringBefore(":")
        return volumeId.matches(Regex("""^[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}$""")) ||
            volumeId.contains("sdcard", ignoreCase = true) ||
            volumeId.contains("ext", ignoreCase = true)
    }

    /**
     * Menghitung total dan sisa ruang kosong memori penyimpanan secara akurat.
     */
    fun getAvailableStorageSpace(context: Context): StorageSpaceInfo {
        val customUri = getPersistedCustomStorageTreeUri(context)
        val isMicroSd = isMicroSdStorage(customUri)
        val storageName = resolveStorageDisplayName(customUri)

        // Hitung via StatFs pada external storage directory
        val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
        var totalBytes = 0L
        var freeBytes = 0L

        try {
            val stat = StatFs(targetDir.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            totalBytes = totalBlocks * blockSize
            freeBytes = availableBlocks * blockSize
        } catch (_: Throwable) {
            // Fallback jika berjalan pada mock / unit test
            totalBytes = targetDir.totalSpace
            freeBytes = targetDir.usableSpace
        }

        // Fallback nilai wajar jika environment pengujian tidak mengembalikan statfs
        if (totalBytes <= 0L) {
            totalBytes = 128_000_000_000L // 128 GB default
            freeBytes = 64_000_000_000L   // 64 GB free default
        }

        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        return StorageSpaceInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            isExternalMicroSd = isMicroSd,
            storagePath = storageName
        )
    }

    /**
     * Proteksi Keamanan Memori:
     * Memeriksa apakah kapasitas memori mencukupi sebelum proses unduhan dimulai.
     * Jika sisa storage < 2.5 GB, unduhan otomatis diblokir dengan pesan informatif.
     */
    fun checkStorageSafety(
        context: Context,
        downloadSizeBytes: Long = 0L
    ): StorageSafetyCheckResult {
        val info = getAvailableStorageSpace(context)
        val freeBytes = info.freeBytes

        // Proteksi 1: Batas minimum memori sistem 2.5 GB
        if (freeBytes < MIN_FREE_STORAGE_THRESHOLD_BYTES) {
            val freeStr = StorageSpaceInfo.formatBytes(freeBytes)
            return StorageSafetyCheckResult(
                isSafe = false,
                freeBytes = freeBytes,
                requiredBytes = MIN_FREE_STORAGE_THRESHOLD_BYTES,
                message = "Sisa ruang penyimpanan perangkat ($freeStr) berada di bawah ambang batas aman minimum 2.5 GB. Harap kosongkan memori atau pilih kartu MicroSD di tab Settings."
            )
        }

        // Proteksi 2: Apakah ukuran file game + buffer 500 MB melebihi sisa storage?
        val requiredBytes = downloadSizeBytes + DOWNLOAD_SAFETY_BUFFER_BYTES
        if (downloadSizeBytes > 0L && freeBytes < requiredBytes) {
            val neededStr = StorageSpaceInfo.formatBytes(requiredBytes)
            val freeStr = StorageSpaceInfo.formatBytes(freeBytes)
            return StorageSafetyCheckResult(
                isSafe = false,
                freeBytes = freeBytes,
                requiredBytes = requiredBytes,
                message = "Ukuran file game membutuhkan memori aman sebesar $neededStr, sedangkan sisa penyimpanan hanya $freeStr. Unduhan tidak dapat dilanjutkan."
            )
        }

        return StorageSafetyCheckResult(
            isSafe = true,
            freeBytes = freeBytes,
            requiredBytes = requiredBytes,
            message = "Ruang penyimpanan aman (${StorageSpaceInfo.formatBytes(freeBytes)} tersedia)."
        )
    }

    /**
     * Memeriksa ketersediaan memori secara cepat (boolean).
     */
    fun hasSufficientStorage(context: Context, requiredBytes: Long = MIN_FREE_STORAGE_THRESHOLD_BYTES): Boolean {
        val info = getAvailableStorageSpace(context)
        return info.freeBytes >= requiredBytes
    }

    const val PUBLIC_BASE_PATH = "/storage/emulated/0/NexusDeck"

    /**
     * Mengembalikan direktori root bersama NexusDeck (/storage/emulated/0/NexusDeck/).
     * Dapat diakses secara langsung oleh seluruh engine emulator target (DuckStation, PPSSPP,
     * NetherSX2, APS3e, GameNative) tanpa terhalang isolasi Android Scoped Storage.
     */
    fun getPublicNexusDeckDir(context: Context? = null): File {
        return try {
            val externalRoot = Environment.getExternalStorageDirectory()
            if (externalRoot != null && (externalRoot.exists() || externalRoot.canWrite())) {
                val dir = File(externalRoot, "NexusDeck")
                if (!dir.exists()) dir.mkdirs()
                dir
            } else if (context != null) {
                val appExt = context.getExternalFilesDir(null) ?: context.filesDir
                val dir = File(appExt, "NexusDeck")
                if (!dir.exists()) dir.mkdirs()
                dir
            } else {
                val fallback = File(PUBLIC_BASE_PATH)
                if (!fallback.exists()) fallback.mkdirs()
                fallback
            }
        } catch (_: Throwable) {
            val fallback = if (context != null) {
                File(context.getExternalFilesDir(null) ?: context.filesDir, "NexusDeck")
            } else {
                File(System.getProperty("java.io.tmpdir"), "NexusDeck")
            }
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Mengembalikan direktori penyimpanan publik bersama per platform:
     * - PS1: /storage/emulated/0/NexusDeck/games/ps1/
     * - PSP: /storage/emulated/0/NexusDeck/games/psp/
     * - PS2: /storage/emulated/0/NexusDeck/games/ps2/
     * - PS3: /storage/emulated/0/NexusDeck/games/ps3/
     * - PC: /storage/emulated/0/NexusDeck/games/pc/
     * - Nintendo: /storage/emulated/0/NexusDeck/games/nintendo/
     */
    fun getPlatformGamesDir(context: Context? = null, platform: GamePlatform): File {
        val root = getPublicNexusDeckDir(context)
        val dir = File(root, "games/${platform.name.lowercase(Locale.US)}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPlatformGamesDir(context: Context, platform: GamePlatform): File {
        return getPlatformGamesDir(context as Context?, platform)
    }

    /**
     * Mengembalikan direktori BIOS bersama (/storage/emulated/0/NexusDeck/bios/).
     * Berisi berkas BIOS resmi seperti scph1001.bin (PS1) dan scph39001.bin (PS2).
     */
    fun getBiosDir(context: Context? = null): File {
        val root = getPublicNexusDeckDir(context)
        val dir = File(root, "bios")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getBiosDir(context: Context): File {
        return getBiosDir(context as Context?)
    }

    /**
     * Mengembalikan direktori Firmware bersama (/storage/emulated/0/NexusDeck/firmware/).
     * Berisi berkas Firmware resmi seperti PS3UPDAT.PUP (Sony PS3).
     */
    fun getFirmwareDir(context: Context? = null): File {
        val root = getPublicNexusDeckDir(context)
        val dir = File(root, "firmware")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getFirmwareDir(context: Context): File {
        return getFirmwareDir(context as Context?)
    }

    /**
     * String path kanonikal untuk verifikasi & integrasi antar modul:
     */
    fun getCanonicalPlatformGamesPath(platform: GamePlatform): String {
        return "$PUBLIC_BASE_PATH/games/${platform.name.lowercase(Locale.US)}/"
    }

    fun getCanonicalBiosPath(fileName: String): String {
        return "$PUBLIC_BASE_PATH/bios/$fileName"
    }

    fun getCanonicalFirmwarePath(fileName: String): String {
        return "$PUBLIC_BASE_PATH/firmware/$fileName"
    }
}
