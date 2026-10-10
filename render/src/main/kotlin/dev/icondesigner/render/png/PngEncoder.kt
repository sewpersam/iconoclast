package dev.icondesigner.render.png

import dev.icondesigner.render.RgbaImage
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

    fun encode(image: RgbaImage): ByteArray {
        val size = image.size
        val stride = size * 4
        val straight = unpremultiply(image.pixels)
        val raw = ByteArray((stride + 1) * size)
        val prevRow = ByteArray(stride)
        val candidate = Array(5) { ByteArray(stride) }
        for (y in 0 until size) {
            val row = straight.copyOfRange(y * stride, (y + 1) * stride)
            val prior = if (y == 0) prevRow else straight.copyOfRange((y - 1) * stride, y * stride)
            var best = 0
            var bestScore = Long.MAX_VALUE
            for (f in 0..4) {
                filter(f, row, prior, candidate[f])
                var score = 0L
                for (b in candidate[f]) score += kotlin.math.abs(b.toInt())
                if (score < bestScore) { bestScore = score; best = f }
            }
            raw[y * (stride + 1)] = best.toByte()
            candidate[best].copyInto(raw, y * (stride + 1) + 1)
        }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, size)
        putInt(ihdr, 4, size)
        ihdr[8] = 8 // bit depth
        ihdr[9] = 6 // RGBA
        chunk(out, "IHDR", ihdr)
        chunk(out, "IDAT", Deflate.zlib(raw))
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

    private fun filter(type: Int, row: ByteArray, prior: ByteArray, out: ByteArray) {
        for (i in row.indices) {
            val x = row[i].toInt() and 0xFF
            val a = if (i >= 4) row[i - 4].toInt() and 0xFF else 0
            val b = prior[i].toInt() and 0xFF
            val c = if (i >= 4) prior[i - 4].toInt() and 0xFF else 0
            out[i] = when (type) {
                0 -> x
                1 -> x - a
                2 -> x - b
                3 -> x - (a + b) / 2
                else -> x - paeth(a, b, c)
            }.toByte()
        }
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
