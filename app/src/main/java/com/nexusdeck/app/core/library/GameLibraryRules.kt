package com.nexusdeck.app.core.library

import com.nexusdeck.app.core.model.GameItem
import com.nexusdeck.app.core.model.GamePlatform
import com.nexusdeck.app.core.model.TargetEmulator

/**
 * Satu file game yang ditemukan saat memindai folder milik pengguna.
 * Murni data — tidak ada tipe Android di sini, supaya aturan pemetaan
 * bisa diuji unit tanpa emulator.
 */
data class ScannedGameFile(
    /** Content URI SAF (string) dari file — dipakai sebagai "romPath" saat boot. */
    val contentUri: String,
    val fileName: String,
    val displayName: String,
    val platform: GamePlatform,
    val targetEmulator: TargetEmulator,
    val sizeBytes: Long
)

/**
 * Aturan murni pustaka lokal (Fase 1): folder dipilih PER SISTEM
 * (pengguna menunjuk folder PS1, PS2, dst.), lalu file di dalamnya
 * dikenali dari ekstensinya. Daftar ekstensi diambil dari
 * es_systems.xml ES-DE (lihat laporan riset putaran 5).
 *
 * Arsip (.zip/.7z) TIDAK dipindai — NexusDeck tidak mengekstrak arsip
 * saat boot; pengguna harus mengekstrak dulu (kecuali zip game PC,
 * yang memang format game-nya).
 */
object GameLibraryRules {

    val extensionsForPlatform: Map<GamePlatform, Set<String>> = mapOf(
        GamePlatform.PS1 to setOf(
            "bin", "cbn", "ccd", "chd", "cue", "ecm", "exe", "img", "iso",
            "m3u", "mdf", "mds", "minipsf", "pbp", "psexe", "psf", "toc", "z", "znx"
        ),
        GamePlatform.PS2 to setOf(
            "bin", "chd", "ciso", "cso", "dump", "elf", "gz", "m3u", "mdf",
            "img", "iso", "isz", "nrg"
        ),
        GamePlatform.PSP to setOf(
            "chd", "cso", "elf", "iso", "pbp", "prx"
        ),
        GamePlatform.NINTENDO to setOf(
            "app", "bin", "3ds", "3dsx", "axf", "cci", "cxi", "gba", "gb", "gbc",
            "nds", "n64", "nes", "sfc", "smc", "v64", "z64", "gen", "md", "sms"
        ),
        GamePlatform.PS3 to setOf(
            "iso", "pkg"
        ),
        GamePlatform.PC to setOf(
            "zip"
        )
    )

    /**
     * Emulator target dari sistem + ekstensi. Nintendo mencakup tiga
     * keluarga (GBA -> Lemuroid, NDS -> melonDS, 3DS -> Azahar) sehingga
     * emulatornya ditentukan ekstensi; sistem lain satu emulator.
     */
    fun emulatorFor(platform: GamePlatform, extension: String): TargetEmulator {
        return when (platform) {
            GamePlatform.PS1 -> TargetEmulator.DuckStation
            GamePlatform.PSP -> TargetEmulator.PPSSPP
            GamePlatform.PS2 -> TargetEmulator.ARMSX2
            GamePlatform.PS3 -> TargetEmulator.ARMSX3
            GamePlatform.PC -> TargetEmulator.GameNative
            GamePlatform.NINTENDO -> when (extension.lowercase()) {
                "gba" -> TargetEmulator.Lemuroid
                "nds", "bin", "app" -> TargetEmulator.MelonDS
                "3ds", "3dsx", "cci", "cxi", "axf" -> TargetEmulator.Lime3DS
                else -> TargetEmulator.Lemuroid
            }
        }
    }

    fun isSupportedExtension(platform: GamePlatform, extension: String): Boolean {
        return extensionsForPlatform[platform]?.contains(extension.lowercase()) == true
    }

    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return ""
        return fileName.substring(dot + 1).lowercase()
    }

    /**
     * Nama tampil dari nama file: buang ekstensi dan tag dalam kurung
     * ("Tekken 3 (USA).chd" -> "Tekken 3"), ganti pemisah (_ - .) dengan
     * spasi, rapikan spasi ganda.
     */
    fun cleanDisplayName(fileName: String): String {
        var name = fileName.substringBeforeLast('.', fileName)
        name = name.replace(Regex("\\([^)]*\\)"), " ")
        name = name.replace(Regex("\\[[^]]*]"), " ")
        name = name.replace(Regex("[_\\-.]+"), " ")
        name = name.replace(Regex("\\s+"), " ").trim()
        return name.ifBlank { fileName.substringBeforeLast('.', fileName) }
    }

    /** ID deterministik supaya pindai ulang tidak menduplikasi game. */
    fun localGameId(platform: GamePlatform, displayName: String): String {
        val slug = displayName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(64)
            .trim('_')
        val safeSlug = slug.ifBlank { "game" }
        return "local_${platform.name.lowercase()}_$safeSlug"
    }

    fun humanSizeLabel(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "—"
        val kb = sizeBytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    fun toGameItem(file: ScannedGameFile): GameItem {
        return GameItem(
            id = localGameId(file.platform, file.displayName),
            title = file.displayName,
            platform = file.platform,
            coverUrl = "",
            minRamGb = 4,
            recommendedSocKeywords = emptyList(),
            targetEmulator = file.targetEmulator,
            fileSizeLabel = humanSizeLabel(file.sizeBytes),
            downloadUrl = "",
            isWebResult = false,
            sourceRepositoryName = "Pustaka Lokal"
        )
    }
}
