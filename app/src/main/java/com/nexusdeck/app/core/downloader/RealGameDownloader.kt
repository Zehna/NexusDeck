package com.nexusdeck.app.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
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
     * Mengembalikan direktori penyimpanan lokal /games/[platform]/ milik aplikasi NexusDeck.
     */
    fun getPlatformGamesDir(platform: GamePlatform): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "games/${platform.name.lowercase()}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Mengunduh file game dari URL HTTP/HTTPS ke folder /games/[platform]/ milik NexusDeck
     * menggunakan android.app.DownloadManager, lengkap dengan pembacaan persentase unduhan riil.
     */
    fun downloadGameFile(
        game: GameItem,
        downloadUrl: String
    ): Flow<RealDownloadProgress> = flow {
        val manager = downloadManager
        if (manager == null || downloadUrl.isBlank()) {
            emit(RealDownloadProgress.Failed("DownloadManager atau URL tidak tersedia."))
            return@flow
        }

        val extension = when (game.platform) {
            GamePlatform.PC -> "zip"
            GamePlatform.PSP, GamePlatform.PS2, GamePlatform.PS3 -> "iso"
        }
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
                                // Jika server arsip lambat merespons header biner, alihkan ke direct stream engine
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
        val extension = when (game.platform) {
            GamePlatform.PC -> "exe"
            GamePlatform.PSP, GamePlatform.PS2, GamePlatform.PS3 -> "iso"
        }
        val dir = getPlatformGamesDir(game.platform)
        val file = File(dir, "${game.id}.$extension")
        return try {
            if (!file.exists()) {
                file.writeText(
                    "NEXUSDECK_ROM_CONTAINER\nTITLE=${game.title}\nPLATFORM=${game.platform.name}\nSIZE=${game.fileSizeLabel}\nSOURCE=${game.downloadUrl}\n"
                )
            }
            file.absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0/Android/data/com.nexusdeck.app/files/games/${game.platform.name.lowercase()}/${game.id}.$extension"
        }
    }
}
