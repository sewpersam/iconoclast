package dev.iconoclast.app.theme

import dev.iconoclast.pack.InvalidKeyBackupException
import dev.iconoclast.pack.KeyBackup
import dev.iconoclast.render.texture.BuiltInBrushes
import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A theme file checked for import: its JSON, the canonical files of the textures it
 * brought, by hash, and the signing key when the export included it, already opened once
 * to prove it is whole. Nothing is stored yet; [ArchiveImport] stores these.
 */
class ThemeArchive(val json: String, val textures: Map<String, ByteArray>, val key: KeyBackup? = null)

/**
 * Theme import, spec 2.4. Takes an exported `.zip` or a bare theme JSON. The theme is
 * migrated and validated; every texture it uses must be in the archive, where its pixels
 * must match its hash after canonicalisation, or already be on the phone. Only
 * `theme.json`, `textures/<sha256>.png` for hashes the theme uses, and the two `key/`
 * entries, both or neither, are read, each bounded and their total capped, so a crafted archive cannot exhaust memory. Anything else is refused
 * with a [ThemeFormatException] for the user. Validation touches no store and no file.
 */
object ThemeZipImport {
    /** A theme JSON is a few kilobytes. */
    const val MAX_JSON = 256 * 1024

    /** A 2048 px RGBA texture of noise stays under this. */
    const val MAX_TEXTURE = 24 * 1024 * 1024

    /** Everything read from one archive, decompressed. */
    const val MAX_TOTAL = 64 * 1024 * 1024

    /** A PKCS12 store with one EC key and its certificate is about 1 KB. */
    const val MAX_KEY_STORE = 64 * 1024

    /** The password is 44 Base64 characters. */
    const val MAX_KEY_PASSWORD = 1024

    private val TEXTURE_ENTRY = Regex("textures/([0-9a-f]{64})\\.png")

    /** [bytes] is the picked file. [onPhone] says whether a texture the archive lacks is available anyway. */
    fun read(bytes: ByteArray, onPhone: (String) -> Boolean): ThemeArchive = read({ ByteArrayInputStream(bytes) }, onPhone)

    /**
     * Reads the picked file from [open], which gives a new stream from its start on each call.
     * The file is streamed, never held whole: a zip is read entry by entry under the bounds
     * above, a bare JSON up to [MAX_JSON]. An [IOException] from [open] itself propagates.
     */
    fun read(open: () -> InputStream, onPhone: (String) -> Boolean): ThemeArchive {
        if (!isZip(open().use { it.readNBytes(4) })) {
            val bytes = open().use { it.readNBytes(MAX_JSON + 1) }
            if (bytes.size > MAX_JSON) throw ThemeFormatException("This file is too large to be a theme.")
            val json = bytes.decodeToString()
            val missing = migrate(json).theme.textureHashes().filterNot { BuiltInBrushes.isBuiltIn(it) || onPhone(it) }
            if (missing.isNotEmpty()) {
                throw ThemeFormatException("This theme uses textures that are not in the file. Import its .zip export instead.")
            }
            return ThemeArchive(json, emptyMap())
        }
        val budget = Budget()
        val json = entries(open, budget) { if (it == ThemeZipExport.THEME_ENTRY) MAX_JSON else null }
            .values.firstOrNull()?.decodeToString()
            ?: throw ThemeFormatException("This archive has no theme.json, so it is not a theme export.")
        // Built-in brushes are on every phone and never in an archive.
        val hashes = migrate(json).theme.textureHashes().filterNot(BuiltInBrushes::isBuiltIn).toSet()
        val found = entries(open, budget) { name ->
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
        return ThemeArchive(json, brought, key(open, budget))
    }

    /** The signing key entries: both or neither, and the store must open with the password. */
    private fun key(open: () -> InputStream, budget: Budget): KeyBackup? {
        val found = entries(open, budget) {
            when (it) {
                ThemeZipExport.KEY_STORE_ENTRY -> MAX_KEY_STORE
                ThemeZipExport.KEY_PASSWORD_ENTRY -> MAX_KEY_PASSWORD
                else -> null
            }
        }
        if (found.isEmpty()) return null
        val p12 = found[ThemeZipExport.KEY_STORE_ENTRY]
        val password = found[ThemeZipExport.KEY_PASSWORD_ENTRY]
        if (p12 == null || password == null) throw ThemeFormatException("The signing key in this archive is incomplete.")
        val key = KeyBackup(p12, password)
        try {
            key.open()
        } catch (e: InvalidKeyBackupException) {
            throw ThemeFormatException("The signing key in this archive is damaged.", e)
        }
        return key
    }

    fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Decompressed bytes read so far from one archive. */
    private class Budget {
        var used = 0L
    }

    /** The entries [limit] names, each bounded by the limit it returns and all by [MAX_TOTAL]. The rest are skipped unread. */
    private fun entries(open: () -> InputStream, budget: Budget, limit: (String) -> Int?): Map<String, ByteArray> {
        val raw = open()
        return try {
            val out = LinkedHashMap<String, ByteArray>()
            ZipInputStream(raw.buffered()).use { zip ->
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
        } finally {
            raw.close()
        }
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
