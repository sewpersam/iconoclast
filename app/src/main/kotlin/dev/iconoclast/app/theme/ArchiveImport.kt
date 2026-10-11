package dev.iconoclast.app.theme

import dev.iconoclast.app.texture.TextureFiles
import dev.iconoclast.pack.KeyMismatchException
import dev.iconoclast.pack.PackKeyStore
import dev.iconoclast.pack.RestoreResult
import dev.iconoclast.pack.restore

/** What an import stored: the new theme, and what became of the key when restoring was asked. */
class ImportResult(val doc: ThemeDocument, val key: RestoreResult?)

/**
 * Stores a checked [ThemeArchive], spec 2.4 and 6.5. With [restoreKey], which the caller
 * passes only after the user confirmed, and a key in the archive, the key goes first: it is
 * restored only when this phone has none, and a different key here throws
 * [ThemeFormatException] before anything is stored, never overwriting it. Then the
 * textures, then the theme under a new id. Without [restoreKey] the archive's key is ignored.
 */
object ArchiveImport {
    suspend fun store(archive: ThemeArchive, restoreKey: Boolean, keys: PackKeyStore, textures: TextureFiles, themes: ThemeRepository): ImportResult {
        val key = archive.key?.takeIf { restoreKey }?.let {
            try {
                keys.restore(it)
            } catch (e: KeyMismatchException) {
                throw ThemeFormatException(
                    "This phone already has a different signing key, so the backup's key was not restored. " +
                        "Import again without the key to bring in the theme only.", e,
                )
            }
        }
        archive.textures.forEach { (hash, png) -> textures.write(hash, png) }
        return ImportResult(themes.import(archive.json), key)
    }
}
