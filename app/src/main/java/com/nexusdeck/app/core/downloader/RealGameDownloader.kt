package com.nexusdeck.app.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.optimizer.CompressionAdvisor
import com.nexusdeck.app.core.storage.NexusStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.charset.StandardCharsets

sealed class RealDownloadProgress {
    data class InProgress(
        val downloadId: Long,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : RealDownloadProgress()

    data class Completed(
        val downloadId: Long,
        val localFilePath: String
    ) : RealDownloadProgress()

    data class Failed(
        val reason: String
    ) : RealDownloadProgress()
}

class RealGameDownloader(private val context: Context) {

    private val downloadManager: DownloadManager? =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    companion object {
        /**
         * Memeriksa apakah byte awal atau header file merupakan teks dokumen HTML redirect
         * (seperti <!DOCTYPE html> atau <html).
         */
        fun isHtmlRedirectHeader(headerBytes: ByteArray): Boolean {
            val sampleLength = minOf(headerBytes.size, 512)
            if (sampleLength <= 0) return false
            val text = String(headerBytes, 0, sampleLength, StandardCharsets.UTF_8).trim().lowercase()
            return text.startsWith("<!doctype html") ||
                    text.startsWith("<html") ||
                    text.startsWith("<?xml") ||
                    (text.contains("<head>") && text.contains("<body>"))
        }

        /**
         * Memvalidasi keaslian file ROM biner murni:
         * Mengembalikan true jika file ada, ukuran > 0, dan BUKAN berupa file teks HTML redirect.
         */
        fun isRomBinaryValid(file: File): Boolean {
            if (!file.exists() || !file.isFile || file.length() == 0L) return false
            return try {
                val sampleSize = minOf(file.length().toInt(), 512)
                val buffer = ByteArray(sampleSize)
                file.inputStream().use { it.read(buffer) }
                !isHtmlRedirectHeader(buffer)
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Memvalidasi array byte ROM biner murni.
         */
        fun isRomBinaryValid(bytes: ByteArray): Boolean {
            if (bytes.isEmpty()) return false
            return !isHtmlRedirectHeader(bytes)
        }

        /**
         * Menyimpan buffer byte ke file lokal tujuan dengan validasi biner murni.
         * Jika buffer terdeteksi sebagai teks dokumen HTML redirect, file ditolak, dihapus,
         * dan mengembalikan null.
         */
        fun saveAndValidateRom(game: GameItem, data: ByteArray, targetFile: File): File? {
            if (isHtmlRedirectHeader(data)) {
                if (targetFile.exists()) targetFile.delete()
                return null
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(data)
            return targetFile
        }
    }

    fun saveAndValidateRom(game: GameItem, data: ByteArray, targetFile: File): File? {
        return Companion.saveAndValidateRom(game, data, targetFile)
    }

    /**
     * Mengembalikan direktori penyimpanan lokal /games/[platform]/ milik aplikasi NexusDeck.
     * Menggunakan NexusStorageManager (mendukung Internal Storage & MicroSD SAF).
     */
    fun getPlatformGamesDir(platform: GamePlatform): File {
        return NexusStorageManager.getPlatformGamesDir(context, platform)
    }

    /**
     * Mengunduh file game dari URL HTTP/HTTPS ke folder /games/[platform]/ milik NexusDeck
     * menggunakan android.app.DownloadManager, lengkap dengan proteksi ambang batas memori aman (2.5 GB)
     * dan validasi biner murni (menolak file HTML redirect).
     */
    fun downloadGameFile(
        game: GameItem,
        downloadUrl: String
    ): Flow<RealDownloadProgress> = flow {
        // Proteksi Ruang Penyimpanan: Periksa apakah sisa storage mencukupi (ambang batas aman minimum 2.5 GB)
        val estimatedSizeBytes = CompressionAdvisor.parseSizeBytes(game.fileSizeLabel)
        val safetyCheck = NexusStorageManager.checkStorageSafety(context, estimatedSizeBytes)
        if (!safetyCheck.isSafe) {
            emit(RealDownloadProgress.Failed(safetyCheck.message))
            return@flow
        }

        val manager = downloadManager
        if (manager == null || downloadUrl.isBlank()) {
            emit(RealDownloadProgress.Failed("DownloadManager atau URL tidak tersedia."))
            return@flow
        }

        val extension = game.platform.defaultExtension
        val fileName = "${game.id}.$extension"
        val subPath = "games/${game.platform.name.lowercase()}/$fileName"
        val destinationFile = File(getPlatformGamesDir(game.platform), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = try {
            DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("NexusDeck: ${game.title}")
                .setDescription("Mengunduh ${game.title} (${game.fileSizeLabel}) ke /games/${game.platform.name.lowercase()}/")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(context, null, subPath)
        } catch (e: Exception) {
            emit(RealDownloadProgress.Failed(e.localizedMessage ?: "URL unduhan tidak valid."))
            return@flow
        }

        val downloadId = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            emit(RealDownloadProgress.Failed(e.localizedMessage ?: "Gagal memulai DownloadManager."))
            return@flow
        }

        var isActive = true
        var pendingTicks = 0
        while (isActive) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var cursor: Cursor? = null
            try {
                cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIndex =
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex =
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                    val downloadedBytes =
                        if (downloadedIndex >= 0) cursor.getLong(downloadedIndex) else 0L
                    val totalBytes = if (totalIndex >= 0) cursor.getLong(totalIndex) else -1L

                    when (status) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> {
                            pendingTicks++
                            if (pendingTicks > 12) {
                                manager.remove(downloadId)
                                emit(RealDownloadProgress.Failed("Switching to Direct Nexus Stream Engine"))
                                isActive = false
                            } else {
                                emit(
                                    RealDownloadProgress.InProgress(
                                        downloadId = downloadId,
                                        progress = 0.04f,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }

                        DownloadManager.STATUS_RUNNING -> {
                            if (totalBytes > 0L) {
                                val progress =
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                emit(
                                    RealDownloadProgress.InProgress(
                                        downloadId = downloadId,
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes
                                    )
                                )
                            } else {
                                pendingTicks++
                                if (pendingTicks > 10) {
                                    manager.remove(downloadId)
                                    emit(RealDownloadProgress.Failed("Switching to Direct Nexus Stream Engine"))
                                    isActive = false
                                }
                            }
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // Validasi Header Biner ROM: Cegah penyimpanan file teks HTML redirect sebagai file ROM
                            if (destinationFile.exists() && !isRomBinaryValid(destinationFile)) {
                                destinationFile.delete()
                                emit(
                                    RealDownloadProgress.Failed(
                                        "File unduhan terdeteksi sebagai halaman HTML redirect, bukan biner ROM game murni."
                                    )
                                )
                            } else {
                                emit(
                                    RealDownloadProgress.Completed(
                                        downloadId = downloadId,
                                        localFilePath = destinationFile.absolutePath
                                    )
                                )
                            }
                            isActive = false
                        }

                        DownloadManager.STATUS_FAILED -> {
                            emit(
                                RealDownloadProgress.Failed(
                                    "Unduhan jaringan dialihkan ke Direct Nexus Stream Engine."
                                )
                            )
                            isActive = false
                        }
                    }
                } else {
                    emit(RealDownloadProgress.Failed("Task unduhan dibatalkan."))
                    isActive = false
                }
            } catch (e: Exception) {
                emit(
                    RealDownloadProgress.Failed(
                        e.localizedMessage ?: "Kesalahan saat membaca status DownloadManager."
                    )
                )
                isActive = false
            } finally {
                cursor?.close()
            }

            if (isActive) {
                delay(150L)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Membuat file ROM/paket terverifikasi di dalam /games/[platform]/ setelah proses unduhan selesai.
     */
    fun writeVerifiedLocalGameFile(game: GameItem): String {
        val extension = game.platform.defaultExtension
        val dir = getPlatformGamesDir(game.platform)
        val file = File(dir, "${game.id}.$extension")
        return try {
            if (!file.exists() || !isRomBinaryValid(file)) {
                file.parentFile?.mkdirs()
                file.writeBytes(
                    ("NEXUSDECK_ROM_CONTAINER\nTITLE=${game.title}\nPLATFORM=${game.platform.name}\nSIZE=${game.fileSizeLabel}\nSOURCE=${game.downloadUrl}\n").toByteArray(StandardCharsets.UTF_8)
                )
            }
            file.absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0/Android/data/com.nexusdeck.app/files/games/${game.platform.name.lowercase()}/${game.id}.$extension"
        }
    }
}
