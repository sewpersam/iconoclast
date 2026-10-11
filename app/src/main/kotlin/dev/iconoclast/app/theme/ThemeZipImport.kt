package dev.iconoclast.app.theme

import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A theme file checked for import: its JSON, and the canonical files of the textures it
 * brought, by hash. Nothing is stored yet; the caller stores these once the whole file passed.
 */
class ThemeArchive(val json: String, val textures: Map<String, ByteArray>)

/**
 * Theme import, spec 2.4. Takes an exported `.zip` or a bare theme JSON. The theme is
 * migrated and validated; every texture it uses must be in the archive, where its pixels
 * must match its hash after canonicalisation, or already be on the phone. Only
 * `theme.json` and `textures/<sha256>.png` for hashes the theme uses are read, and their
 * total is capped, so a crafted archive cannot exhaust memory. Anything else is refused
 * with a [ThemeFormatException] for the user. Validation touches no store and no file.
 */
object ThemeZipImport {
    /** A theme JSON is a few kilobytes. */
    const val MAX_JSON = 256 * 1024

    /** A 2048 px RGBA texture of noise stays under this. */
    const val MAX_TEXTURE = 24 * 1024 * 1024

    /** Everything read from one archive, decompressed. */
    const val MAX_TOTAL = 64 * 1024 * 1024

    private val TEXTURE_ENTRY = Regex("textures/([0-9a-f]{64})\\.png")

    /** [bytes] is the picked file. [onPhone] says whether a texture the archive lacks is available anyway. */
    fun read(bytes: ByteArray, onPhone: (String) -> Boolean): ThemeArchive {
        if (!isZip(bytes)) {
            val json = bytes.decodeToString()
            val missing = migrate(json).theme.textureHashes().filterNot(onPhone)
            if (missing.isNotEmpty()) {
                throw ThemeFormatException("This theme uses textures that are not in the file. Import its .zip export instead.")
            }
            return ThemeArchive(json, emptyMap())
        }
        val budget = Budget()
        val json = entries(bytes, budget) { if (it == ThemeZipExport.THEME_ENTRY) MAX_JSON else null }
            .values.firstOrNull()?.decodeToString()
            ?: throw ThemeFormatException("This archive has no theme.json, so it is not a theme export.")
        val hashes = migrate(json).theme.textureHashes()
        val found = entries(bytes, budget) { name ->
            TEXTURE_ENTRY.matchEntire(name)?.groupValues?.get(1)?.takeIf { it in hashes }?.let { MAX_TEXTURE }
        }.mapKeys { TEXTURE_ENTRY.matchEntire(it.key)!!.groupValues[1] }
        val brought = LinkedHashMap<String, ByteArray>()
        for (hash in hashes) {
            val png = found[hash]
            if (png == null) {
                if (onPhone(hash)) continue
                throw ThemeFormatException("The archive is missing a texture the theme uses, ${hash.take(12)}.")
            }
            val c = try {
                TextureStore.canonical(png)
            } catch (e: IllegalArgumentException) {
                throw ThemeFormatException("A texture in the archive is damaged, ${hash.take(12)}.", e)
            }
            if (c.hash != hash) throw ThemeFormatException("A texture in the archive is damaged, ${hash.take(12)}.")
            brought[hash] = c.png
        }
        return ThemeArchive(json, brought)
    }

    fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Decompressed bytes read so far from one archive. */
    private class Budget {
        var used = 0L
    }

    /** The entries [limit] names, each bounded by the limit it returns and all by [MAX_TOTAL]. The rest are skipped unread. */
    private fun entries(bytes: ByteArray, budget: Budget, limit: (String) -> Int?): Map<String, ByteArray> = try {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory || e.name in out) continue
                val max = limit(e.name) ?: continue
                out[e.name] = zip.readBounded(max, e.name, budget)
            }
        }
        out
    } catch (e: IOException) {
        throw ThemeFormatException("This archive is damaged.", e)
    } catch (e: IllegalArgumentException) {
        // ZipInputStream rejects malformed entry names this way.
        throw ThemeFormatException("This archive is damaged.", e)
    }

    private fun InputStream.readBounded(limit: Int, name: String, budget: Budget): ByteArray {
        val room = minOf(limit.toLong(), MAX_TOTAL - budget.used).toInt()
        val b = readNBytes(room + 1)
        if (b.size > room) {
            throw ThemeFormatException(if (room < limit) "This archive is too large to import." else "$name in this archive is too large.")
        }
        budget.used += b.size
        return b
    }
}
