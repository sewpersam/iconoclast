package dev.iconoclast.app.theme

import dev.iconoclast.render.texture.BuiltInBrushes
import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Theme export, spec 2.4: a `.zip` with `theme.json` and `textures/<sha256>.png` for every
 * texture the theme uses, the canonical files from the store, except the built-in brushes
 * ([BuiltInBrushes]), which every install generates for itself. Entries are written in a
 * fixed order with a fixed time, so one theme gives one archive.
 */
object ThemeZipExport {
    const val THEME_ENTRY = "theme.json"
    const val TEXTURE_DIR = "textures/"
    const val MIME = "application/zip"

    /** Every texture of [doc] must be in [textures]; [dev.iconoclast.app.texture.TexturePresence] checks first. */
    fun write(doc: ThemeDocument, textures: TextureStore): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry(THEME_ENTRY, doc.toJson().toByteArray())
            for (hash in doc.theme.textureHashes()) {
                if (BuiltInBrushes.isBuiltIn(hash)) continue
                val png = checkNotNull(textures.bytes(hash)) { "texture $hash is not in the store" }
                zip.entry("$TEXTURE_DIR$hash.png", png)
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name).apply { timeLocal = EPOCH })
        write(bytes)
        closeEntry()
    }

    /** The earliest time a zip can hold. */
    private val EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0)
}
