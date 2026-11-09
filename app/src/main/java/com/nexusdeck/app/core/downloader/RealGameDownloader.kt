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
         * Minimum download size by file type. GBA ROMs are small by nature
         * (legitimate ones can be under 1 MiB), so they get a 256 KiB floor;
         * disc images (ISO/CHD/CSO) and everything else get a 1 MiB floor.
         * Files below the floor are dummy/corrupt downloads, not games.
         */
        fun minimumDownloadBytes(game: GameItem): Long {
            val ext = game.platform.defaultExtension.lowercase()
            return when (ext) {
                "gba", "agb" -> 256L * 1024L
                else -> 1024L * 1024L
            }
        }

        /**
         * The file DownloadManager actually writes to when the request uses
         * setDestinationInExternalFilesDir(context, null, subPath): the
         * app-private external files dir, NOT the public games dir.
         * Returns null when the external files dir is unavailable.
         */
        fun downloadManagerFile(context: Context, subPath: String): File? {
            val base = context.getExternalFilesDir(null) ?: return null
            return File(base, subPath)
        }

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
        // Canonical download location: the file DownloadManager actually
        // writes to (app-private external files dir). The public games dir
        // is only a copy target afterwards — never report it as the
        // downloaded file unless the copy really landed there.
        val downloadManagerDestination = downloadManagerFile(context, subPath)
        val publicDestination = File(getPlatformGamesDir(game.platform), fileName)
        if (downloadManagerDestination?.exists() == true) {
            downloadManagerDestination.delete()
        }
        if (publicDestination.exists()) {
            publicDestination.delete()
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
                                emit(RealDownloadProgress.Failed("Unduhan tertunda terlalu lama."))
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
                                    emit(RealDownloadProgress.Failed("Unduhan gagal dimulai (tidak ada progres)."))
                                    isActive = false
                                }
                            }
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // The file DownloadManager wrote is the ONLY file
                            // we may validate and return. Validate it, then
                            // copy it to the public games dir when writable;
                            // if the copy fails, return the app-private path
                            // that actually exists — never a path to a file
                            // that does not exist.
                            val downloadedFile = downloadManagerDestination
                            val minimumBytes = minimumDownloadBytes(game)
                            when {
                                downloadedFile == null || !downloadedFile.exists() -> {
                                    emit(
                                        RealDownloadProgress.Failed(
                                            "Unduhan selesai tetapi file hasil unduhan tidak ditemukan."
                                        )
                                    )
                                }
                                !isRomBinaryValid(downloadedFile) -> {
                                    downloadedFile.delete()
                                    emit(
                                        RealDownloadProgress.Failed(
                                            "File unduhan terdeteksi sebagai halaman HTML redirect, bukan biner ROM game murni."
                                        )
                                    )
                                }
                                downloadedFile.length() < minimumBytes -> {
                                    downloadedFile.delete()
                                    emit(
                                        RealDownloadProgress.Failed(
                                            "Download gagal: File corrupt/dummy"
                                        )
                                    )
                                }
                                else -> {
                                    val finalFile = try {
                                        publicDestination.parentFile?.mkdirs()
                                        downloadedFile.copyTo(publicDestination, overwrite = true)
                                        publicDestination
                                    } catch (_: Exception) {
                                        // Public dir not writable (scoped
                                        // storage): keep the app-private file.
                                        downloadedFile
                                    }
                                    emit(
                                        RealDownloadProgress.Completed(
                                            downloadId = downloadId,
                                            localFilePath = finalFile.absolutePath
                                        )
                                    )
                                }
                            }
                            isActive = false
                        }

                        DownloadManager.STATUS_FAILED -> {
                            emit(
                                RealDownloadProgress.Failed(
                                    "Unduhan gagal (DownloadManager)."
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
     * Returns the path of an already-downloaded, validated game file in the
     * public /games/[platform]/ dir — or null when there is none.
     *
     * This REPLACES the old writeVerifiedLocalGameFile(), which fabricated a
     * text file ("NEXUSDECK_ROM_CONTAINER...") and returned it as if it were
     * a real ROM. That fabrication is deleted by design: this function never
     * writes anything, it only verifies a file that already exists.
     */
    fun getVerifiedLocalGameFile(game: GameItem): String? {
        val extension = game.platform.defaultExtension
        val file = File(getPlatformGamesDir(game.platform), "${game.id}.$extension")
        if (!file.exists() || !file.isFile) return null
        if (!isRomBinaryValid(file)) return null
        if (file.length() < minimumDownloadBytes(game)) return null
        return file.absolutePath
    }
}
