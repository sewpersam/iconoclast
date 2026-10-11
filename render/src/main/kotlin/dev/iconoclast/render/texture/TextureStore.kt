package dev.iconoclast.render.texture

import dev.iconoclast.render.png.PngDecoder
import dev.iconoclast.render.png.PngEncoder
import java.security.MessageDigest

/** A decoded texture: premultiplied RGBA, 8 bits per channel, row-major. Read-only. */
class Texture internal constructor(val width: Int, val height: Int, internal val premultiplied: ByteArray)

/**
 * Textures by content hash, spec 2.4. A texture file is a canonical PNG: the pixels
 * written by [encode], the project's own encoder, so equal pixels give equal bytes and the
 * same SHA-256 on every platform. Themes refer to textures by that hash. The store decodes
 * through the pure-Kotlin [PngDecoder] and keeps the decoded pixels; it never touches the
 * mask cache. Downscaling to [MAX_SIDE] is the importer's job; the store refuses anything
 * larger. Thread-safe.
 */
class TextureStore {
    private val textures = HashMap<String, Texture>()

    /** Adds a texture PNG and returns its hash. Adding the same bytes twice is a no-op. */
    fun put(png: ByteArray): String {
        val hash = sha256(png)
        if (contains(hash)) return hash
        val t = decode(png)
        synchronized(this) { textures[hash] = t }
        return hash
    }

    /** Adds a texture read back from storage, checking that its bytes still have [hash]. */
    fun put(hash: String, png: ByteArray) {
        require(sha256(png) == hash) { "texture file does not match its hash $hash" }
        put(png)
    }

    operator fun get(hash: String): Texture? = synchronized(this) { textures[hash] }

    operator fun contains(hash: String): Boolean = synchronized(this) { hash in textures }

    fun remove(hash: String) = synchronized(this) { textures.remove(hash) }

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
                PngDecoder.decode(png, maxSide = Int.MAX_VALUE / 8)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("not a texture PNG: ${e.message}", e)
            }
            require(maxOf(d.width, d.height) <= MAX_SIDE) { "texture ${d.width}x${d.height} is over $MAX_SIDE px" }
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
