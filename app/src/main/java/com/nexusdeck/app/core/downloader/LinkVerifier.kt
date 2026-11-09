package com.nexusdeck.app.core.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hasil pemeriksaan satu tautan unduhan.
 *
 * @param ok true hanya bila tautan menunjuk ke sebuah file biner yang bisa
 * diunduh (bukan halaman web, bukan direktori, bukan tautan mati).
 * @param httpStatus kode HTTP terakhir setelah redirect, atau -1 bila server
 * tidak dapat dijangkau sama sekali.
 * @param contentType Content-Type yang dilaporkan server (bisa null).
 * @param contentLength Content-Length yang dilaporkan server, atau -1 bila
 * server tidak menyatakannya.
 * @param finalUrl URL akhir setelah mengikuti redirect.
 * @param reason penjelasan berbahasa Indonesia yang siap ditampilkan ke user.
 */
data class LinkCheckResult(
    val ok: Boolean,
    val httpStatus: Int,
    val contentType: String?,
    val contentLength: Long,
    val finalUrl: String,
    val reason: String
)

/**
 * LinkVerifier — verifikasi otomatis tautan unduhan SEBELUM DownloadManager
 * di-enqueue.
 *
 * Latar belakang (v1.1.5): katalog berisi URL placeholder yang tidak pernah
 * terverifikasi. Dua kegagalan yang dilaporkan user pada 2026-11-09:
 * - archive.org mengembalikan HTTP 503 untuk castlevania_sotn.chd — server
 * tidak pernah mengirim byte, DownloadManager stuck dengan totalBytes = -1,
 * lalu kode lama menyerah dengan pesan samar "Unduhan gagal dimulai
 * (tidak ada progres)".
 * - myrient.erista.me mengembalikan halaman HTML untuk sebuah URL .zip —
 * unduhan "selesai", tapi isinya halaman web, baru ketahuan saat validasi
 * biner pasca-unduh.
 *
 * Verifier ini menutup kedua lubang itu: tautan mati / HTML ditolak dalam
 * hitungan detik dengan alasan yang spesifik, sebelum DownloadManager
 * di-enqueue dan sebelum user menunggu file ratusan MB yang tidak akan
 * pernah datang.
 *
 * Yang TIDAK dilakukan verifier ini: mencari, menebak, atau "memperbaiki"
 * URL ke tautan ROM komersial lain. Sebuah tautan yang gagal verifikasi
 * tetap gagal — aplikasi hanya mengunduh ROM milik user / homebrew dari
 * tautan langsung yang lolos pemeriksaan ini, atau memakai file lokal yang
 * sudah ada (lihat RealGameDownloader.getVerifiedLocalGameFile).
 */
object LinkVerifier {

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000
    const val MAX_REDIRECTS = 5

    /** Jumlah byte awal yang diambil untuk memeriksa magic HTML vs biner. */
    const val PROBE_BYTES = 512

    private val BINARY_CONTENT_TYPES = setOf(
        "application/octet-stream",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-iso9660-image",
        "application/x-cd-image",
        "application/x-chd",
        "application/x-cso"
    )

    /**
     * Keputusan murni dari header HTTP — tanpa jaringan, sehingga bisa
     * diuji unit. Aturan:
     * - URL kosong atau menunjuk ke direktori (berakhiran "/") → tolak.
     * - Status bukan 200/206 → tolak dengan kode HTTP-nya.
     * - Content-Type text/* (termasuk text/html) → tolak: itu halaman web.
     * - Content-Length diketahui dan di bawah batas minimum → tolak.
     * - Content-Type biner yang dikenal, atau tidak dinyatakan server tapi
     * ukuran mencukupi → terima.
     */
    fun evaluateHeaders(
        url: String,
        httpStatus: Int,
        contentType: String?,
        contentLength: Long,
        minBytes: Long
    ): LinkCheckResult {
        val finalUrl = url
        fun fail(status: Int, reason: String) = LinkCheckResult(
            ok = false,
            httpStatus = status,
            contentType = contentType,
            contentLength = contentLength,
            finalUrl = finalUrl,
            reason = reason
        )
        fun pass(reason: String) = LinkCheckResult(
            ok = true,
            httpStatus = httpStatus,
            contentType = contentType,
            contentLength = contentLength,
            finalUrl = finalUrl,
            reason = reason
        )

        if (url.isBlank()) {
            return fail(httpStatus, "URL unduhan kosong.")
        }
        val path = try {
            URL(url).path
        } catch (_: Exception) {
            return fail(httpStatus, "URL unduhan tidak valid: $url")
        }
        if (path.endsWith("/")) {
            return fail(
                httpStatus,
                "Tautan mengarah ke halaman daftar direktori, bukan file game. " +
                    "Tautan unduhan harus menunjuk langsung ke satu file biner."
            )
        }
        if (httpStatus == -1) {
            return fail(httpStatus, "Tidak dapat menjangkau server unduhan. Periksa koneksi internet.")
        }
        if (httpStatus != HttpURLConnection.HTTP_OK &&
            httpStatus != HttpURLConnection.HTTP_PARTIAL
        ) {
            return fail(httpStatus, "Tautan mati (HTTP $httpStatus). Server tidak menyediakan file ini.")
        }
        val type = contentType?.trim()?.lowercase()?.substringBefore(";")?.trim()
        if (type != null && (type == "text/html" || type.startsWith("text/"))) {
            return fail(
                httpStatus,
                "Tautan mengarah ke halaman web (Content-Type: $type), bukan file game biner."
            )
        }
        if (contentLength >= 0L && contentLength < minBytes) {
            return fail(
                httpStatus,
                "Ukuran file yang dilaporkan server ($contentLength byte) di bawah batas minimum " +
                    "($minBytes byte). File kemungkinan corrupt/dummy."
            )
        }
        if (type != null && type !in BINARY_CONTENT_TYPES && !type.startsWith("application/")) {
            return fail(
                httpStatus,
                "Tipe konten tidak dikenali sebagai file game (Content-Type: $type)."
            )
        }
        return pass("Tautan terverifikasi: HTTP $httpStatus, ${type ?: "tipe tidak dinyatakan"}.")
    }

    /**
     * Verifikasi satu URL lewat jaringan: HEAD (fallback ke GET Range bila
     * server menolak HEAD), ikuti redirect, lalu baca 512 byte pertama
     * untuk memastikan isinya bukan halaman HTML.
     */
    suspend fun verify(url: String, minBytes: Long): LinkCheckResult =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) {
                return@withContext evaluateHeaders(url, -1, null, -1L, minBytes)
            }
            try {
                var currentUrl = url
                var status = -1
                var contentType: String? = null
                var contentLength = -1L
                var redirects = 0
                var connection: HttpURLConnection? = null
                try {
                    // Putaran 1: HEAD, ikuti redirect secara manual agar
                    // URL akhir tercatat.
                    while (redirects <= MAX_REDIRECTS) {
                        connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "HEAD"
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            instanceFollowRedirects = false
                            setRequestProperty("User-Agent", "NexusDeck-LinkVerifier/1.1.6")
                        }
                        status = connection.responseCode
                        if (status in 301..308) {
                            val location = connection.getHeaderField("Location")
                            connection.disconnect()
                            connection = null
                            if (location.isNullOrBlank()) break
                            currentUrl = URL(URL(currentUrl), location).toString()
                            redirects++
                            continue
                        }
                        break
                    }
                    if (connection == null) {
                        return@withContext evaluateHeaders(url, status, null, -1L, minBytes)
                    }
                    // Server yang menolak HEAD (405/501): coba GET Range.
                    if (status == HttpURLConnection.HTTP_BAD_METHOD ||
                        status == HttpURLConnection.HTTP_NOT_IMPLEMENTED
                    ) {
                        connection.disconnect()
                        connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            instanceFollowRedirects = true
                            setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
                            setRequestProperty("User-Agent", "NexusDeck-LinkVerifier/1.1.6")
                        }
                        status = connection.responseCode
                    }
                    contentType = connection.contentType
                    contentLength = connection.contentLengthLong
                } finally {
                    connection?.disconnect()
                }

                val headerResult = evaluateHeaders(currentUrl, status, contentType, contentLength, minBytes)
                if (!headerResult.ok) return@withContext headerResult

                // Putaran 2: probe 512 byte pertama — halaman error yang
                // lolos sebagai 200 dengan Content-Type generik tertangkap
                // di sini lewat magic HTML.
                val sample = probeFirstBytes(currentUrl)
                if (sample != null && RealGameDownloader.isHtmlRedirectHeader(sample)) {
                    return@withContext headerResult.copy(
                        ok = false,
                        reason = "Server mengembalikan halaman web, bukan file game biner."
                    )
                }
                return@withContext headerResult
            } catch (e: IOException) {
                return@withContext LinkCheckResult(
                    ok = false,
                    httpStatus = -1,
                    contentType = null,
                    contentLength = -1L,
                    finalUrl = url,
                    reason = "Tidak dapat menjangkau server unduhan (${e.message ?: "kesalahan jaringan"})."
                )
            } catch (e: Exception) {
                return@withContext LinkCheckResult(
                    ok = false,
                    httpStatus = -1,
                    contentType = null,
                    contentLength = -1L,
                    finalUrl = url,
                    reason = "Verifikasi tautan gagal (${e.message ?: "kesalahan tak dikenal"})."
                )
            }
        }

    private fun probeFirstBytes(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
                setRequestProperty("User-Agent", "NexusDeck-LinkVerifier/1.1.6")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK &&
                connection.responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                return null
            }
            val buffer = ByteArray(PROBE_BYTES)
            val read = connection.inputStream.use { it.read(buffer) }
            if (read <= 0) null else buffer.copyOf(read)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
