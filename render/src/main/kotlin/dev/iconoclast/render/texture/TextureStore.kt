package dev.iconoclast.render.texture

import dev.iconoclast.render.png.PngDecoder
import dev.iconoclast.render.png.PngEncoder
import java.security.MessageDigest
import java.util.concurrent.Semaphore

/** A decoded texture: premultiplied RGBA, 8 bits per channel, row-major. Read-only. */
class Texture internal constructor(val width: Int, val height: Int, internal val premultiplied: ByteArray)

/** A texture's canonical file and its hash, before it enters any store. */
class CanonicalTexture(val hash: String, val png: ByteArray)

/**
 * Textures by content hash, spec 2.4. A texture file is a canonical PNG: the pixels
 * written by [encode], the project's own encoder, so equal pixels give equal bytes and the
 * same SHA-256 on every platform. [put] canonicalises whatever 8-bit RGBA PNG it is given.
 * Themes refer to textures by that hash. The store decodes through the pure-Kotlin
 * [PngDecoder]; it never touches the mask cache. Downscaling to [MAX_SIDE] is the
 * importer's job; the store refuses anything larger.
 *
 * Memory is bounded: decoded pixels plus canonical files are kept least recently used
 * first out, up to [maxBytes]. An evicted texture comes back through [load], the canonical
 * file by hash from wherever the app keeps it. Files are canonical by construction, so the
 * check on the way in is the SHA-256 of the file's own bytes, with no re-encode; a damaged
 * file fails it. Loads run one at a time, so renders missing different textures do not
 * each hold a file, its inflated rows and its pixels at once. [exists] says whether [load]
 * would find a file, without reading it. With neither, the store holds only what was put,
 * still bounded. Thread-safe.
 */
class TextureStore(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val load: (String) -> ByteArray? = { null },
    private val exists: (String) -> Boolean = { false },
) {
    private class Entry(val texture: Texture, val png: ByteArray) {
        val bytes: Long = texture.premultiplied.size.toLong() + png.size
    }

    private val textures = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var resident = 0L
    private val loading = Semaphore(1)

    /** What [exists] said, per hash, so [contains] stats a file once; [forget] drops it. */
    private val onDisk = HashMap<String, Boolean>()

    /**
     * Adds a texture PNG and returns its hash. The PNG is decoded and re-encoded with the
     * project's encoder, and the hash is of those canonical bytes, so the same pixels get
     * the same hash whichever encoder wrote [png]. [bytes] gives back the canonical file.
     */
    fun put(png: ByteArray): String {
        val texture = decode(png)
        val canonical = PngEncoder.encode(texture.width, texture.height, texture.premultiplied)
        val hash = sha256(canonical)
        forget(hash)
        insert(hash, Entry(texture, canonical))
        return hash
    }

    /** Adds a texture read back from storage, checking that its pixels still have [hash]. */
    fun put(hash: String, png: ByteArray) {
        require(put(png) == hash) { "texture file does not match its hash $hash" }
    }

    /**
     * The texture, loaded again through [load] if it was evicted. Null when absent or
     * damaged. A built-in brush, [BuiltInBrushes], is always here.
     */
    operator fun get(hash: String): Texture? = BuiltInBrushes.texture(hash) ?: entry(hash)?.texture

    /**
     * The canonical PNG of a texture: what a theme export writes as `<hash>.png`. A
     * built-in brush is encoded on demand; exports leave it out.
     */
    fun bytes(hash: String): ByteArray? = BuiltInBrushes.png(hash) ?: entry(hash)?.png?.copyOf()

    /**
     * In memory, or on disk where [load] can reach it. Reads no file, and asks [exists] once
     * per hash: the answer is kept until [forget], [put] or [remove], or a failed load: a
     * missing, damaged or undecodable file.
     */
    operator fun contains(hash: String): Boolean {
        if (BuiltInBrushes.isBuiltIn(hash)) return true
        synchronized(this) {
            if (hash in textures) return true
            onDisk[hash]?.let { return it }
        }
        val found = exists(hash)
        synchronized(this) { onDisk[hash] = found }
        return found
    }

    /** Drops the remembered answer of [exists] for [hash]: call after writing or deleting its file. */
    fun forget(hash: String) {
        synchronized(this) { onDisk.remove(hash) }
    }

    /** Whether the decoded texture is in memory now. For tests and diagnostics. */
    fun isResident(hash: String): Boolean = synchronized(this) { textures.containsKey(hash) }

    /** Decoded pixels plus canonical files held now, in bytes. */
    val residentBytes: Long get() = synchronized(this) { resident }

    fun remove(hash: String) {
        synchronized(this) {
            textures.remove(hash)?.let { resident -= it.bytes }
            onDisk.remove(hash)
        }
    }

    private fun entry(hash: String): Entry? {
        synchronized(this) { textures[hash] }?.let { return it }
        // Loaded outside the lock, so one slow load does not stall renders of resident
        // textures, and behind one permit, so loads do not stack their peaks.
        loading.acquireUninterruptibly()
        try {
            synchronized(this) { textures[hash] }?.let { return it }
            // A file that is gone, damaged or undecodable cannot render: [contains] says so
            // from now on, until [forget], [put] or [remove].
            fun absent(): Entry? = null.also { synchronized(this) { onDisk[hash] = false } }
            val png = load(hash) ?: return absent()
            if (sha256(png) != hash) return absent()
            val texture = try {
                decode(png)
            } catch (e: IllegalArgumentException) {
                return absent()
            }
            return insert(hash, Entry(texture, png))
        } finally {
            loading.release()
        }
    }

    /** Inserts and evicts the least recently used down to [maxBytes]; the new entry always stays. */
    private fun insert(hash: String, e: Entry): Entry = synchronized(this) {
        textures[hash]?.let { return it }
        textures[hash] = e
        resident += e.bytes
        val iter = textures.entries.iterator()
        while (resident > maxBytes && iter.hasNext()) {
            val eldest = iter.next()
            if (eldest.key == hash) continue
            resident -= eldest.value.bytes
            iter.remove()
        }
        e
    }

    companion object {
        /** Spec 2.4: the longest side of an imported texture. */
        const val MAX_SIDE = 2048

        /** Decoded pixels plus files kept in memory: four full-size textures. */
        const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024

        /** The canonical texture PNG of [premultiplied] RGBA pixels. */
        fun encode(width: Int, height: Int, premultiplied: ByteArray): ByteArray {
            require(maxOf(width, height) <= MAX_SIDE) { "texture ${width}x$height is over $MAX_SIDE px" }
            return PngEncoder.encode(width, height, premultiplied)
        }

        /**
         * The canonical file and hash of [png], without storing it: what [put] would keep.
         * Throws IllegalArgumentException when [png] is not a texture.
         */
        fun canonical(png: ByteArray): CanonicalTexture {
            val t = decode(png)
            val canonical = PngEncoder.encode(t.width, t.height, t.premultiplied)
            return CanonicalTexture(sha256(canonical), canonical)
        }

        /** Lowercase hex SHA-256 of [bytes]: a texture's name in themes, files and exports. */
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun decode(png: ByteArray): Texture {
            val d = try {
                PngDecoder.decode(png, maxSide = MAX_SIDE)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("not a texture PNG: ${e.message}", e)
            }
            val s = d.straight
            val p = ByteArray(s.size)
            for (i in s.indices step 4) {
                val a = s[i + 3].toInt() and 0xFF
                for (k in 0..2) p[i + k] = (((s[i + k].toInt() and 0xFF) * a + 127) / 255).toByte()
                p[i + 3] = a.toByte()
            }
            return Texture(d.width, d.height, p)
        }
    }
}
