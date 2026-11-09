package com.nexusdeck.app.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
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
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

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
         * v1.1.6 — batas toleransi tanpa progres.
         *
         * v1.1.5 menyerah setelah 10–12 tick @150 ms (~1,5–1,8 detik) saat
         * DownloadManager belum melaporkan totalBytes. Itu terlalu agresif:
         * server lambat / koneksi seluler bisa butuh lebih lama hanya untuk
         * mengirim header, dan pesan yang muncul ("Unduhan gagal dimulai
         * (tidak ada progres)") menyembunyikan penyebab aslinya.
         * 200 tick @150 ms = 30 detik. Tautan yang benar-benar mati tidak
         * lagi mencapai titik ini — LinkVerifier sudah menolaknya sebelum
         * enqueue — jadi batas ini hanya melindungi dari stall, bukan
         * menutupi tautan mati.
         */
        const val NO_PROGRESS_TICK_LIMIT = 200

        /** Nama file sementara di folder Downloads (lihat [downloadManagerTempFile]). */
        fun tempFileName(game: GameItem): String = "nexusdeck-${game.id}.tmp"

        /**
         * v1.1.6 — lokasi file sementara DownloadManager: folder Downloads
         * standar di dalam external files dir aplikasi
         * (…/Android/data/com.nexusdeck.app/files/Download/), yang diizinkan
         * sistem tanpa izin storage tambahan. v1.1.5 memakai subfolder
         * "games/<platform>/" di files dir — itu juga app-private dan
         * bukan penyebab "tidak ada progres" (penyebabnya server 503),
         * tapi folder Downloads standar lebih mudah diaudit dan tidak
         * dicampur dengan file game final. File final tetap dipindah ke
         * folder publik /storage/emulated/0/NexusDeck/games/<platform>/
         * setelah lolos verifikasi biner.
         */
        fun downloadManagerTempFile(context: Context, game: GameItem): File? {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            return File(base, tempFileName(game))
        }

        /**
         * True bila file ZIP hasil unduhan harus diekstrak untuk mengambil
         * ROM di dalamnya. Hanya berlaku untuk ROM yang dikemas dalam ZIP
         * (mis. entri Myrient No-Intro/Redump). Game PC (ekstensi "zip")
         * TIDAK diekstrak: ZIP-nya sendiri adalah paket game-nya (mis.
         * Freedoom berisi .wad, OpenTTD berisi .exe/data) — mencari entri
         * ".zip" di dalamnya selalu gagal dan memunculkan error palsu
         * "File ZIP tidak berisi ROM .zip yang valid" padahal unduhan
         * 42 MB-nya sudah sukses.
         */
        fun shouldExtractZip(game: GameItem): Boolean {
            return game.platform != GamePlatform.PC
        }

        /** True bila 4 byte pertama file adalah magic ZIP (PK\x03\x04 / PK\x05\x06). */
        fun isZipFile(file: File): Boolean {
            if (!file.exists() || !file.isFile || file.length() < 4L) return false
            return try {
                val header = ByteArray(4)
                file.inputStream().use { it.read(header) }
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    (header[2] == 0x03.toByte() || header[2] == 0x05.toByte()) &&
                    (header[3] == 0x04.toByte() || header[3] == 0x06.toByte())
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Mengekstrak entri ROM dari sebuah ZIP ke [destDir].
         * Mengembalikan file hasil ekstrak, atau null bila ZIP tidak berisi
         * entri dengan ekstensi [wantedExtension].
         *
         * Pengaman: tolak entri direktori, tolak path traversal (zip-slip —
         * canonical path hasil ekstrak harus berada di dalam [destDir]),
         * dan tolak entri raksasa (> 16 GB, indikasi zip bomb). Entri yang
         * lebih kecil dari [minBytes] dilewati.
         */
        fun extractRomFromZip(
            zipFile: File,
            wantedExtension: String,
            destDir: File,
            minBytes: Long
        ): File? {
            if (!isZipFile(zipFile)) return null
            val wantedSuffix = ".${wantedExtension.lowercase()}"
            val destCanonical = destDir.canonicalFile
            destDir.mkdirs()
            var result: File? = null
            try {
                ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory &&
                            entry.name.lowercase().endsWith(wantedSuffix)
                        ) {
                            val entrySize = entry.size
                            if ((entrySize < 0L || entrySize >= minBytes) && entrySize <= 16_000_000_000L) {
                                val outFile = File(destDir, "nexusdeck-extracted$wantedSuffix")
                                val outCanonical = outFile.canonicalFile
                                if (outCanonical.parentFile == destCanonical || outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                                    FileOutputStream(outFile).use { out ->
                                        zip.copyTo(out)
                                    }
                                    if (outFile.exists() && outFile.length() >= minBytes) {
                                        result = outFile
                                        break
                                    } else {
                                        outFile.delete()
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (_: Exception) {
                result?.delete()
                return null
            }
            return result
        }

        /** Memetakan DownloadManager.COLUMN_REASON ke pesan berbahasa Indonesia. */
        fun downloadFailureMessage(reason: Int): String = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "Ruang penyimpanan tidak cukup untuk mengunduh game ini."
            DownloadManager.ERROR_CANNOT_RESUME ->
                "Unduhan tidak dapat dilanjutkan (server tidak mendukung resume). Coba lagi."
            DownloadManager.ERROR_FILE_ERROR ->
                "DownloadManager gagal menulis file unduhan ke penyimpanan."
            DownloadManager.ERROR_HTTP_DATA_ERROR ->
                "Server memutus transfer data (HTTP data error). Tautan mungkin mati."
            DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                "Tautan mengarah ke terlalu banyak redirect."
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                "Server mengembalikan kode HTTP yang tidak ditangani DownloadManager."
            DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                "Penyimpanan eksternal tidak ditemukan."
            else -> "Unduhan gagal (DownloadManager)."
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
     *
     * v1.1.6:
     * - Pre-flight LinkVerifier: tautan mati/HTML ditolak sebelum enqueue.
     * - Tujuan sementara: folder Downloads standar di external files dir
     *   (Environment.DIRECTORY_DOWNLOADS), lalu file dipindah ke folder
     *   publik setelah lolos verifikasi.
     * - File ZIP diekstrak dulu (ROM di dalamnya yang dipakai).
     * - Batas tanpa-progres 30 detik (sebelumnya ~1,5 detik) dan alasan
     *   kegagalan DownloadManager yang sebenarnya ditampilkan.
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

        // v1.1.6 — verifikasi otomatis SEBELUM enqueue (LinkVerifier):
        // tautan mati (HTTP 503), halaman HTML, atau direktori ditolak di
        // sini dalam hitungan detik dengan alasan spesifik, bukan setelah
        // user menunggu file ratusan MB yang tidak akan pernah datang.
        // Tanpa ini, server yang tidak pernah mengirim byte membuat
        // DownloadManager stuck dengan totalBytes = -1 dan user hanya
        // mendapat pesan samar "tidak ada progres".
        val linkCheck = LinkVerifier.verify(downloadUrl, minimumDownloadBytes(game))
        if (!linkCheck.ok) {
            emit(RealDownloadProgress.Failed(linkCheck.reason))
            return@flow
        }

        val extension = game.platform.defaultExtension
        val fileName = "${game.id}.$extension"
        // v1.1.6 — tujuan sementara: folder Downloads standar di dalam
        // external files dir aplikasi (diizinkan sistem tanpa izin storage
        // tambahan). File final dipindah ke folder publik hanya setelah
        // lolos verifikasi biner — jangan pernah laporkan path publik
        // sebagai file hasil unduhan sebelum pemindahan itu benar terjadi.
        val downloadManagerDestination = downloadManagerTempFile(context, game)
        val publicDestination = File(getPlatformGamesDir(game.platform), fileName)
        if (downloadManagerDestination?.exists() == true) {
            downloadManagerDestination.delete()
        }
        if (publicDestination.exists()) {
            publicDestination.delete()
        }

        val request = try {
            DownloadManager.Request(Uri.parse(linkCheck.finalUrl))
                .setTitle("NexusDeck: ${game.title}")
                .setDescription("Mengunduh ${game.title} (${game.fileSizeLabel}) ke /games/${game.platform.name.lowercase()}/")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    tempFileName(game)
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
                            if (pendingTicks > NO_PROGRESS_TICK_LIMIT) {
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
                                if (pendingTicks > NO_PROGRESS_TICK_LIMIT) {
                                    manager.remove(downloadId)
                                    emit(RealDownloadProgress.Failed("Unduhan gagal dimulai (tidak ada progres)."))
                                    isActive = false
                                }
                            }
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // The file DownloadManager wrote is the ONLY file
                            // we may validate and return. If it is a ZIP
                            // holding a ROM (mis. entri Myrient No-Intro/
                            // Redump), ekstrak dulu ROM di dalamnya; tanpa
                            // unzip, file .zip akan tersimpan sebagai
                            // .gba/.iso dan emulator tidak akan bisa
                            // memuatnya. Game PC dikecualikan (lihat
                            // shouldExtractZip): ZIP-nya sendiri adalah
                            // paket game-nya. Validate the extracted (or
                            // kept) file, then MOVE it to the public games
                            // dir when writable; if the move fails, return
                            // the app-private path that actually exists —
                            // never a path to a file that does not exist.
                            var downloadedFile = downloadManagerDestination
                            val minimumBytes = minimumDownloadBytes(game)
                            if (downloadedFile != null && downloadedFile.exists() && shouldExtractZip(game) && isZipFile(downloadedFile)) {
                                val extracted = extractRomFromZip(
                                    downloadedFile,
                                    extension,
                                    downloadedFile.parentFile ?: context.cacheDir,
                                    minimumBytes
                                )
                                downloadedFile.delete()
                                downloadedFile = extracted
                                if (downloadedFile == null) {
                                    emit(
                                        RealDownloadProgress.Failed(
                                            "File ZIP tidak berisi ROM .$extension yang valid."
                                        )
                                    )
                                    isActive = false
                                    return@flow
                                }
                            }
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
                                        val moved = downloadedFile.renameTo(publicDestination)
                                        if (!moved) {
                                            downloadedFile.copyTo(publicDestination, overwrite = true)
                                            downloadedFile.delete()
                                        }
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
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                            emit(
                                RealDownloadProgress.Failed(
                                    downloadFailureMessage(reason)
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
