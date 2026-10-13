package dev.iconoclast.app.theme

import dev.iconoclast.pack.KeyBackup
import dev.iconoclast.render.texture.BuiltInBrushes
import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Theme export, spec 2.4, archive layout v2 (docs/theme-format.md): a `.zip` with
 * `theme.json` and `textures/<sha256>.png` for every texture the theme uses, the canonical
 * files from the store, except the built-in brushes ([BuiltInBrushes]), which every install
 * generates for itself; then, only when a key is passed, `key/pack.p12` and `key/password`,
 * the signing key's two files, spec 6.5. Entries are written in a fixed order with a fixed
 * time, so one theme and key give one archive.
 */
object ThemeZipExport {
    const val THEME_ENTRY = "theme.json"
    const val TEXTURE_DIR = "textures/"
    const val KEY_STORE_ENTRY = "key/pack.p12"
    const val KEY_PASSWORD_ENTRY = "key/password"
    const val MIME = "application/zip"

    /** Every texture of [doc] must be in [textures]; [dev.iconoclast.app.texture.TexturePresence] checks first. */
    /**
     * [key] is the signing key, passed only when the user tapped through the key export
     * warning; null, the default, leaves `key/` out.
     */
    fun write(doc: ThemeDocument, textures: TextureStore, key: KeyBackup? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry(THEME_ENTRY, doc.toJson().toByteArray())
            for (hash in doc.theme.textureHashes()) {
                if (BuiltInBrushes.isBuiltIn(hash)) continue
                val png = checkNotNull(textures.bytes(hash)) { "texture $hash is not in the store" }
                zip.entry("$TEXTURE_DIR$hash.png", png)
            }
            if (key != null) {
                zip.entry(KEY_STORE_ENTRY, key.p12)
                zip.entry(KEY_PASSWORD_ENTRY, key.password)
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        // setTimeLocal is API 35; the app runs from 33. setTime converts through the default
        // zone, so the zone's own instant of the local time gives the same DOS time anywhere.
        putNextEntry(ZipEntry(name).apply { time = EPOCH.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() })
        write(bytes)
        closeEntry()
    }

    /** The earliest time a zip can hold. */
    private val EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0)
}
