package dev.iconoclast.render.png

import dev.iconoclast.render.RgbaImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Minimal PNG writer: 8-bit RGBA, one IDAT, per-row adaptive filter.
 *
 * The renderer's output is premultiplied, spec 3.3, but PNG stores straight alpha, so
 * each pixel is unpremultiplied here with integer rounding. Android decodes PNGs back to
 * premultiplied bitmaps, and the round trip is exact: re-premultiplying gives the
 * renderer's bytes again. Every step is integer arithmetic, so the bytes are the same on
 * every platform.
 */
object PngEncoder {

    /** An icon: [Deflate.Effort.FAST]. */
    fun encode(image: RgbaImage): ByteArray = encode(image.size, image.size, image.pixels)

    /**
     * [premultiplied] is [width] x [height] premultiplied RGBA, row-major. Texture files
     * pass [Deflate.Effort.CANONICAL], whose bytes their hashes name.
     */
    fun encode(width: Int, height: Int, premultiplied: ByteArray, effort: Deflate.Effort = Deflate.Effort.FAST): ByteArray {
        require(width > 0 && height > 0 && premultiplied.size == width * height * 4) { "pixels for ${width}x$height" }
        val stride = width * 4
        val straight = unpremultiply(premultiplied)
        val raw = ByteArray((stride + 1) * height)
        val zeros = ByteArray(stride)
        val candidate = Array(5) { ByteArray(stride) }
        for (y in 0 until height) {
            val row = y * stride
            val prior = if (y == 0) -1 else (y - 1) * stride
            var best = 0
            var bestScore = Long.MAX_VALUE
            for (f in 0..4) {
                val score = filter(f, straight, row, if (prior < 0) zeros else straight, if (prior < 0) 0 else prior, stride, candidate[f])
                if (score < bestScore) { bestScore = score; best = f }
            }
            raw[y * (stride + 1)] = best.toByte()
            candidate[best].copyInto(raw, y * (stride + 1) + 1)
        }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, width)
        putInt(ihdr, 4, height)
        ihdr[8] = 8 // bit depth
        ihdr[9] = 6 // RGBA
        chunk(out, "IHDR", ihdr)
        chunk(out, "IDAT", Deflate.zlib(raw, effort))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Premultiplied to straight RGBA: round(c * 255 / a), and zero where a is zero. */
    fun unpremultiply(p: ByteArray): ByteArray {
        val out = ByteArray(p.size)
        for (i in p.indices step 4) {
            val a = p[i + 3].toInt() and 0xFF
            if (a == 0) continue
            for (k in 0..2) out[i + k] = minOf(255, ((p[i + k].toInt() and 0xFF) * 255 + a / 2) / a).toByte()
            out[i + 3] = a.toByte()
        }
        return out
    }

    /**
     * Filters the row at [row] of [px] against the prior row at [prior] of [pp] into [out]
     * and returns the sum of the filtered bytes as signed values, the selection heuristic.
     */
    private fun filter(type: Int, px: ByteArray, row: Int, pp: ByteArray, prior: Int, stride: Int, out: ByteArray): Long {
        var score = 0L
        for (i in 0 until stride) {
            val x = px[row + i].toInt() and 0xFF
            val a = if (i >= 4) px[row + i - 4].toInt() and 0xFF else 0
            val b = pp[prior + i].toInt() and 0xFF
            val c = if (i >= 4) pp[prior + i - 4].toInt() and 0xFF else 0
            val v = when (type) {
                0 -> x
                1 -> x - a
                2 -> x - b
                3 -> x - (a + b) / 2
                else -> x - paeth(a, b, c)
            }.toByte()
            out[i] = v
            score += kotlin.math.abs(v.toInt())
        }
        return score
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val len = ByteArray(4).also { putInt(it, 0, data.size) }
        val t = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(t); update(data) }.value.toInt()
        out.write(len)
        out.write(t)
        out.write(data)
        out.write(ByteArray(4).also { putInt(it, 0, crc) })
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte()
        b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
