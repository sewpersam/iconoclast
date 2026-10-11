package dev.iconoclast.app.theme

import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** A theme file checked for import: its JSON, and the textures it brought into the store. */
class ThemeArchive(val json: String, val textures: Set<String>)

/**
 * Theme import, spec 2.4. Takes an exported `.zip` or a bare theme JSON. The theme is
 * migrated and validated; every texture it uses must be in the archive, where its pixels
 * must match its hash after canonicalisation, or already on the phone. Anything else is
 * refused with a [ThemeFormatException] for the user.
 */
object ThemeZipImport {
    /** A theme JSON is a few kilobytes. */
    const val MAX_JSON = 256 * 1024

    /** A 2048 px RGBA texture of noise stays under this. */
    const val MAX_TEXTURE = 24 * 1024 * 1024

    /**
     * [bytes] is the picked file. Textures from the archive go into [store];
     * [onPhone] says whether a texture the archive lacks is available anyway.
     */
    fun read(bytes: ByteArray, store: TextureStore, onPhone: (String) -> Boolean): ThemeArchive {
        if (!isZip(bytes)) {
            val json = bytes.decodeToString()
            val missing = migrate(json).theme.textureHashes().filterNot(onPhone)
            if (missing.isNotEmpty()) {
                throw ThemeFormatException("This theme uses textures that are not in the file. Import its .zip export instead.")
            }
            return ThemeArchive(json, emptySet())
        }
        val entries = unzip(bytes)
        val json = entries[ThemeZipExport.THEME_ENTRY]?.decodeToString()
            ?: throw ThemeFormatException("This archive has no theme.json, so it is not a theme export.")
        val hashes = migrate(json).theme.textureHashes()
        val brought = LinkedHashSet<String>()
        for (hash in hashes) {
            val png = entries["${ThemeZipExport.TEXTURE_DIR}$hash.png"]
            if (png == null) {
                if (onPhone(hash)) continue
                throw ThemeFormatException("The archive is missing a texture the theme uses, ${hash.take(12)}.")
            }
            try {
                store.put(hash, png)
            } catch (e: IllegalArgumentException) {
                throw ThemeFormatException("A texture in the archive is damaged, ${hash.take(12)}.", e)
            }
            brought += hash
        }
        return ThemeArchive(json, brought)
    }

    fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** theme.json and textures/<name>.png entries, each bounded; everything else is skipped. */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = try {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val limit = when {
                    e.isDirectory -> continue
                    e.name == ThemeZipExport.THEME_ENTRY -> MAX_JSON
                    e.name.startsWith(ThemeZipExport.TEXTURE_DIR) && e.name.endsWith(".png") -> MAX_TEXTURE
                    else -> continue
                }
                out[e.name] = zip.readBounded(limit, e.name)
            }
        }
        out
    } catch (e: IOException) {
        throw ThemeFormatException("This archive is damaged.", e)
    } catch (e: IllegalArgumentException) {
        // ZipInputStream rejects malformed entry names this way.
        throw ThemeFormatException("This archive is damaged.", e)
    }

    private fun InputStream.readBounded(limit: Int, name: String): ByteArray {
        val b = readNBytes(limit + 1)
        if (b.size > limit) throw ThemeFormatException("$name in this archive is too large.")
        return b
    }
}
