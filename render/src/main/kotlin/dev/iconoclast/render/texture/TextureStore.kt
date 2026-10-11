package dev.iconoclast.render.texture

import dev.iconoclast.render.png.PngDecoder
import dev.iconoclast.render.png.PngEncoder
import java.security.MessageDigest

/** A decoded texture: premultiplied RGBA, 8 bits per channel, row-major. Read-only. */
class Texture internal constructor(val width: Int, val height: Int, internal val premultiplied: ByteArray)

/**
 * Textures by content hash, spec 2.4. A texture file is a canonical PNG: the pixels
 * written by [encode], the project's own encoder, so equal pixels give equal bytes and the
 * same SHA-256 on every platform. [put] canonicalises whatever 8-bit RGBA PNG it is given. Themes refer to textures by that hash. The store decodes
 * through the pure-Kotlin [PngDecoder] and keeps the decoded pixels; it never touches the
 * mask cache. Downscaling to [MAX_SIDE] is the importer's job; the store refuses anything
 * larger. Thread-safe.
 */
class TextureStore {
    private class Entry(val texture: Texture, val png: ByteArray)

    private val textures = HashMap<String, Entry>()

    /**
     * Adds a texture PNG and returns its hash. The PNG is decoded and re-encoded with the
     * project's encoder, and the hash is of those canonical bytes, so the same pixels get
     * the same hash whichever encoder wrote [png]. [bytes] gives back the canonical file.
     */
    fun put(png: ByteArray): String {
        val texture = decode(png)
        val canonical = PngEncoder.encode(texture.width, texture.height, texture.premultiplied)
        val hash = sha256(canonical)
        synchronized(this) { if (hash !in textures) textures[hash] = Entry(texture, canonical) }
        return hash
    }

    /** Adds a texture read back from storage, checking that its pixels still have [hash]. */
    fun put(hash: String, png: ByteArray) {
        require(put(png) == hash) { "texture file does not match its hash $hash" }
    }

    operator fun get(hash: String): Texture? = synchronized(this) { textures[hash]?.texture }

    /** The canonical PNG of a stored texture: what a theme export writes as `<hash>.png`. */
    fun bytes(hash: String): ByteArray? = synchronized(this) { textures[hash]?.png?.copyOf() }

    operator fun contains(hash: String): Boolean = synchronized(this) { hash in textures }

    fun remove(hash: String) {
        synchronized(this) { textures.remove(hash) }
    }

    companion object {
        /** Spec 2.4: the longest side of an imported texture. */
        const val MAX_SIDE = 2048

        /** The canonical texture PNG of [premultiplied] RGBA pixels. */
        fun encode(width: Int, height: Int, premultiplied: ByteArray): ByteArray {
            require(maxOf(width, height) <= MAX_SIDE) { "texture ${width}x$height is over $MAX_SIDE px" }
            return PngEncoder.encode(width, height, premultiplied)
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
