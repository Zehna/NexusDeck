package com.nexusdeck.app.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

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

    /**
     * Mengunduh paket/ROM game dari URL HTTP/HTTPS menggunakan android.app.DownloadManager
     * langsung ke direktori download internal aplikasi dan memancarkan persentase progres riil.
     */
    fun downloadGameFile(
        game: GameItem,
        downloadUrl: String
    ): Flow<RealDownloadProgress> = flow {
        val manager = downloadManager
        if (manager == null) {
            emit(RealDownloadProgress.Failed("DownloadManager tidak tersedia di perangkat ini."))
            return@flow
        }

        val extension = when (game.platform) {
            GamePlatform.PC -> "zip"
            GamePlatform.PS2 -> "iso"
            GamePlatform.PS3 -> "iso"
        }
        val fileName = "${game.id}.$extension"
        val targetDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "roms/${game.platform.name.lowercase()}"
        )
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val destinationFile = File(targetDir, fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = try {
            DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("NexusDeck: ${game.title}")
                .setDescription("Mengunduh paket game ${game.platform.displayName}...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "roms/${game.platform.name.lowercase()}/$fileName"
                )
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
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_RUNNING -> {
                            val progress = if (totalBytes > 0L) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.05f
                            }
                            emit(
                                RealDownloadProgress.InProgress(
                                    downloadId = downloadId,
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            emit(
                                RealDownloadProgress.InProgress(
                                    downloadId = downloadId,
                                    progress = 1.0f,
                                    downloadedBytes = totalBytes.coerceAtLeast(downloadedBytes),
                                    totalBytes = totalBytes.coerceAtLeast(downloadedBytes)
                                )
                            )
                            emit(
                                RealDownloadProgress.Completed(
                                    downloadId = downloadId,
                                    localFilePath = destinationFile.absolutePath
                                )
                            )
                            isActive = false
                        }

                        DownloadManager.STATUS_FAILED -> {
                            emit(
                                RealDownloadProgress.Failed(
                                    "Unduhan jaringan gagal untuk ${game.title}."
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

    fun cancelDownload(downloadId: Long) {
        downloadManager?.remove(downloadId)
    }
}
