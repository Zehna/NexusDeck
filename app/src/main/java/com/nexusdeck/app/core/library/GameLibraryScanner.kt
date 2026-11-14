package com.nexusdeck.app.core.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexusdeck.app.core.model.GamePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LibraryScanResult(
    val files: List<ScannedGameFile>,
    /** File yang dilewati: ekstensi tidak ada di allow-list sistem folder itu. */
    val skippedCount: Int
)

/**
 * Pemindai pustaka lokal (Fase 1): menelusuri satu folder SAF
 * (dipilih pengguna lewat ACTION_OPEN_DOCUMENT_TREE) secara rekursif
 * dan mengumpulkan file game yang ekstensinya ada di allow-list sistem
 * folder tersebut (lihat GameLibraryRules). Berjalan di Dispatchers.IO.
 *
 * Folder dipilih PER SISTEM — karena banyak format dipakai beberapa
 * konsol sekaligus (.bin, .chd, .iso), sistem ditentukan foldernya,
 * bukan ditebak dari ekstensi.
 */
object GameLibraryScanner {

    private const val MAX_DEPTH = 6

    suspend fun scanFolder(
        context: Context,
        treeUri: Uri,
        platform: GamePlatform
    ): LibraryScanResult = withContext(Dispatchers.IO) {
        val root = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (_: Exception) {
            null
        } ?: return@withContext LibraryScanResult(emptyList(), 0)

        val found = mutableListOf<ScannedGameFile>()
        var skipped = 0
        scanRecursive(root, platform, found, { skipped++ }, depth = 0)
        LibraryScanResult(found, skipped)
    }

    suspend fun scanPublicFolder(
        folder: File,
        platform: GamePlatform
    ): LibraryScanResult = withContext(Dispatchers.IO) {
        if (!folder.exists() || !folder.isDirectory) {
            return@withContext LibraryScanResult(emptyList(), 0)
        }

        val found = mutableListOf<ScannedGameFile>()
        var skipped = 0
        folder.walkTopDown()
            .maxDepth(MAX_DEPTH + 1)
            .filter { it.isFile }
            .forEach { file ->
                val ext = GameLibraryRules.extensionOf(file.name)
                if (!GameLibraryRules.isSupportedExtension(platform, ext)) {
                    skipped++
                } else {
                    found.add(
                        ScannedGameFile(
                            contentUri = file.absolutePath,
                            fileName = file.name,
                            displayName = GameLibraryRules.cleanDisplayName(file.name),
                            platform = platform,
                            targetEmulator = GameLibraryRules.emulatorFor(platform, ext),
                            sizeBytes = file.length()
                        )
                    )
                }
            }
        LibraryScanResult(found, skipped)
    }

    private fun scanRecursive(
        dir: DocumentFile,
        platform: GamePlatform,
        found: MutableList<ScannedGameFile>,
        onSkip: () -> Unit,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            return
        }
        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanRecursive(child, platform, found, onSkip, depth + 1)
                    continue
                }
                val name = child.name ?: continue
                val ext = GameLibraryRules.extensionOf(name)
                if (!GameLibraryRules.isSupportedExtension(platform, ext)) {
                    onSkip()
                    continue
                }
                found.add(
                    ScannedGameFile(
                        contentUri = child.uri.toString(),
                        fileName = name,
                        displayName = GameLibraryRules.cleanDisplayName(name),
                        platform = platform,
                        targetEmulator = GameLibraryRules.emulatorFor(platform, ext),
                        sizeBytes = child.length()
                    )
                )
            } catch (_: Exception) {
                onSkip()
            }
        }
    }
}
