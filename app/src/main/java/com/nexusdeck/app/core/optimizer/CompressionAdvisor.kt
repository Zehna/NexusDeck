package com.nexusdeck.app.core.optimizer

import com.nexusdeck.app.core.model.GamePlatform
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Tipe format ROM / disc image game.
 */
enum class RomFormatType(
    val extension: String,
    val isCompressed: Boolean,
    val isLossless: Boolean,
    val description: String
) {
    CHD("chd", true, true, "Compressed Hunks of Data (MAME / PCSX2 / NetherSX2 / Dolphin)"),
    CSO("cso", true, true, "Compressed ISO (PPSSPP / Sony PSP Block Compression)"),
    ZSO("zso", true, true, "Zstandard Compressed ISO (PPSSPP / PCSX2)"),
    RVZ("rvz", true, true, "RVZ Lossless WIA Compression (Dolphin)"),
    PBP("pbp", true, true, "PlayStation Official EBOOT / Compressed Package"),
    RAW_ISO("iso", false, false, "Raw Disc Image uncompressed ISO-9660"),
    RAW_BIN("bin", false, false, "Raw Disc Binary image"),
    ARCHIVE_7Z("7z", true, true, "7-Zip High Compression Archive"),
    ARCHIVE_ZIP("zip", true, true, "Standard ZIP Archive"),
    UNKNOWN("", false, false, "Unknown ROM Format")
}

/**
 * Model data rekomendasi kompresi format game.
 */
data class CompressionAdvice(
    val shouldRecommend: Boolean,
    val currentFormat: String,
    val recommendedFormat: String,
    val rawSizeBytes: Long,
    val estimatedSavingsPercent: Int,
    val estimatedCompressedSizeBytes: Long,
    val adviceHeadline: String,
    val detailedMessage: String,
    val whyBetter: String
) {
    val estimatedSavingsGb: Double
        get() = (rawSizeBytes - estimatedCompressedSizeBytes) / (1024.0 * 1024.0 * 1024.0)

    val formattedSavings: String
        get() {
            val gb = estimatedSavingsGb
            val df = DecimalFormat("#,##0.0", DecimalFormatSymbols(Locale.US))
            return if (gb >= 1.0) {
                "${df.format(gb)} GB"
            } else {
                val mb = (rawSizeBytes - estimatedCompressedSizeBytes) / (1024.0 * 1024.0)
                "${df.format(mb)} MB"
            }
        }
}

/**
 * Smart ROM Format & Compression Advisor
 * Mengimplementasikan arsitektur kompresi disc image berdasarkan standar Ottavio97/CHDroid & unknownbrackets/maxcso:
 * 1. Deteksi otomatis format mentah (.iso/.bin) berukuran besar (> 2 GB PS2 / > 800 MB PSP).
 * 2. Menghitung rasio kompresi matematis lossless (50–60% hemat memori).
 * 3. Verifikasi signature / magic bytes header (CHD "MComprHD", CSO "CISO", ISO "CD001").
 * 4. Pembobotan prioritas link format (.chd/.cso diprioritaskan di atas .iso).
 */
object CompressionAdvisor {

    // Magic Header Signatures
    val MAGIC_CHD = byteArrayOf(0x4D, 0x43, 0x6F, 0x6D, 0x70, 0x72, 0x48, 0x44) // "MComprHD"
    val MAGIC_CSO = byteArrayOf(0x43, 0x49, 0x53, 0x4F)                         // "CISO"
    val MAGIC_ZSO = byteArrayOf(0x5A, 0x49, 0x53, 0x4F)                         // "ZISO"
    val MAGIC_ISO_9660 = byteArrayOf(0x43, 0x44, 0x30, 0x30, 0x31)             // "CD001" at sector 16

    // Ambang Batas Rekomendasi Kompresi
    const val THRESHOLD_PS2_RAW_BYTES = 2_000_000_000L   // 2.0 GB
    const val THRESHOLD_PSP_RAW_BYTES = 800_000_000L     // 800 MB
    const val THRESHOLD_PC_PS3_RAW_BYTES = 3_000_000_000L // 3.0 GB

    /**
     * Memeriksa magic bytes header pada array byte awal file.
     */
    fun detectHeaderMagic(headerBytes: ByteArray): RomFormatType {
        if (headerBytes.size >= 8 && headerBytes.take(8).toByteArray().contentEquals(MAGIC_CHD)) {
            return RomFormatType.CHD
        }
        if (headerBytes.size >= 4 && headerBytes.take(4).toByteArray().contentEquals(MAGIC_CSO)) {
            return RomFormatType.CSO
        }
        if (headerBytes.size >= 4 && headerBytes.take(4).toByteArray().contentEquals(MAGIC_ZSO)) {
            return RomFormatType.ZSO
        }
        // ISO 9660 Standard Volume Descriptor di sector 16 (offset 0x8000 = 32768)
        if (headerBytes.size >= 32773) {
            val isoId = headerBytes.sliceArray(32769..32773)
            if (isoId.contentEquals(MAGIC_ISO_9660)) {
                return RomFormatType.RAW_ISO
            }
        }
        return RomFormatType.UNKNOWN
    }

    /**
     * Memeriksa apakah ekstensi file termasuk format kompresi modern yang didukung emulator.
     */
    fun isCompressedFormat(extensionOrName: String): Boolean {
        val ext = extractExtension(extensionOrName).lowercase(Locale.US)
        return ext in listOf("chd", "cso", "zso", "rvz", "pbp", "7z", "zip")
    }

    /**
     * Memeriksa apakah ekstensi file merupakan disc image mentah (uncompressed).
     */
    fun isRawDiscFormat(extensionOrName: String): Boolean {
        val ext = extractExtension(extensionOrName).lowercase(Locale.US)
        return ext in listOf("iso", "bin", "img", "cue", "gdi", "mdf", "nrg")
    }

    /**
     * Menghitung bobot prioritas format file untuk scraper dan download sniffer.
     * Format modern hemat memori (.chd / .cso) diprioritaskan di atas .iso mentah.
     */
    fun getFormatPriorityScore(fileNameOrUrl: String): Int {
        val ext = extractExtension(fileNameOrUrl).lowercase(Locale.US)
        return when (ext) {
            "chd" -> 100 // Paling hemat & optimal untuk PS2/PS1/GameCube
            "cso", "zso" -> 95 // Sangat optimal untuk PSP di PPSSPP
            "rvz" -> 90 // Optimal untuk GameCube/Wii
            "pbp" -> 85 // Paket terkompresi PSP resmi
            "7z" -> 80
            "zip" -> 70
            "iso" -> 40 // Mentah tanpa kompresi
            "bin", "img" -> 30
            else -> 20
        }
    }

    /**
     * Menganalisis ukuran dan platform game untuk menghasilkan rekomendasi kompresi cerdas.
     */
    fun analyzeGameFormat(
        platform: GamePlatform,
        fileSizeLabelOrBytes: Any,
        fileNameOrExtension: String
    ): CompressionAdvice {
        val ext = extractExtension(fileNameOrExtension).lowercase(Locale.US)
        val rawBytes = parseSizeBytes(fileSizeLabelOrBytes)

        val isRaw = isRawDiscFormat(ext) || ext.isEmpty()
        val (thresholdBytes, recommendedExt, savingsPercent) = when (platform) {
            GamePlatform.PS2 -> Triple(THRESHOLD_PS2_RAW_BYTES, ".chd", 55) // 55% rata-rata hemat
            GamePlatform.PSP -> Triple(THRESHOLD_PSP_RAW_BYTES, ".cso", 45) // 45% rata-rata hemat
            GamePlatform.NINTENDO -> Triple(1_500_000_000L, ".chd / .rvz", 50)
            GamePlatform.PC -> Triple(THRESHOLD_PC_PS3_RAW_BYTES, ".zip / .7z", 40)
            GamePlatform.PS3 -> Triple(THRESHOLD_PC_PS3_RAW_BYTES, ".chd / .pkg", 50)
        }

        val shouldRecommend = isRaw && rawBytes >= thresholdBytes
        val estimatedCompressedBytes = if (shouldRecommend) {
            (rawBytes * (1.0 - (savingsPercent / 100.0))).toLong()
        } else {
            rawBytes
        }

        val headline = if (platform == GamePlatform.PSP) {
            "💡 Hemat Storage: Format .cso menghemat hingga 45-50% memori tanpa penurunan grafis"
        } else {
            "💡 Hemat Storage: Format .chd menghemat hingga 50-60% memori tanpa penurunan grafis"
        }

        val detailed = if (shouldRecommend) {
            "File game ini terdeteksi sebagai disc image mentah (.iso) berukuran besar. Menggunakan format $recommendedExt dapat memotong penggunaan penyimpanan secara signifikan tanpa menurunkan kualitas audio, video, maupun performa emulasi."
        } else {
            "Format file game sudah optimal untuk platform ${platform.displayName}."
        }

        val whyBetter = when (platform) {
            GamePlatform.PSP -> "PPSSPP mendukung penuh format .cso secara native. Sektor disc dikompresi per blok 2048-byte sehingga kecepatan loading tetap secepat ISO asli."
            GamePlatform.PS2 -> "NetherSX2 & PCSX2 mendukung penuh format .chd v5 (Compressed Hunks of Data). Format ini memotong sektor kosong (zero-padding) dan mengompresi data dengan algoritma LZMA/Zstandard tanpa pengurangan bitrate."
            else -> "Format kompresi modern didukung oleh core emulator secara transparan dengan dekompresi on-the-fly di RAM."
        }

        return CompressionAdvice(
            shouldRecommend = shouldRecommend,
            currentFormat = if (ext.isNotEmpty()) ext.uppercase(Locale.US) else "RAW ISO",
            recommendedFormat = recommendedExt.uppercase(Locale.US),
            rawSizeBytes = rawBytes,
            estimatedSavingsPercent = savingsPercent,
            estimatedCompressedSizeBytes = estimatedCompressedBytes,
            adviceHeadline = headline,
            detailedMessage = detailed,
            whyBetter = whyBetter
        )
    }

    /**
     * Mengurai string label ukuran file (e.g. "3.8 GB", "950 MB", "1.2 GB") atau Long menjadi bytes.
     */
    fun parseSizeBytes(sizeInput: Any): Long {
        if (sizeInput is Long) return sizeInput
        if (sizeInput is Number) return sizeInput.toLong()

        val text = sizeInput.toString().trim()
        val numStr = text.replace(Regex("""[^0-9.]"""), "")
        val num = numStr.toDoubleOrNull() ?: 1.0

        val lower = text.lowercase(Locale.US)
        return when {
            lower.contains("gb") -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            lower.contains("mb") -> (num * 1024.0 * 1024.0).toLong()
            lower.contains("kb") -> (num * 1024.0).toLong()
            else -> (num * 1024.0 * 1024.0 * 1024.0).toLong() // default assume GB
        }
    }

    fun extractExtension(fileNameOrPath: String): String {
        val clean = fileNameOrPath.substringBefore("?").substringBefore("#").trim().removePrefix(".")
        return if (clean.contains(".")) {
            clean.substringAfterLast(".").lowercase(Locale.US)
        } else {
            clean.lowercase(Locale.US)
        }
    }
}
