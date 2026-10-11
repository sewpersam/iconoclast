package dev.iconoclast.render.png

import java.util.zip.CRC32

/** Straight (non-premultiplied) RGBA, 8 bits per channel, row-major. */
class DecodedPng(val width: Int, val height: Int, val straight: ByteArray)

/**
 * Reads the PNGs [PngEncoder] writes: 8-bit RGBA, non-interlaced, any filter types, IDAT
 * split over any number of chunks. CRCs are checked. Through [Inflate], so no platform
 * code. Anything else throws [IllegalArgumentException].
 */
object PngDecoder {

    fun decode(png: ByteArray, maxSide: Int = 16384): DecodedPng {
        require(png.size >= 8 && SIGNATURE.indices.all { png[it] == SIGNATURE[it] }) { "not a PNG" }
        var p = 8
        var width = 0
        var height = 0
        val idat = java.io.ByteArrayOutputStream()
        var ended = false
        while (!ended) {
            require(p + 12 <= png.size) { "truncated PNG" }
            val len = int(png, p)
            require(len >= 0 && p + 12 + len <= png.size) { "truncated chunk" }
            val type = String(png, p + 4, 4, Charsets.US_ASCII)
            val crc = CRC32().apply { update(png, p + 4, 4 + len) }.value.toInt()
            require(crc == int(png, p + 8 + len)) { "CRC mismatch in $type" }
            val at = p + 8
            when (type) {
                "IHDR" -> {
                    require(len == 13 && width == 0) { "bad IHDR" }
                    width = int(png, at)
                    height = int(png, at + 4)
                    require(width in 1..maxSide && height in 1..maxSide) { "size ${width}x$height over $maxSide" }
                    require(png[at + 8].toInt() == 8 && png[at + 9].toInt() == 6) { "only 8-bit RGBA is read" }
                    require(png[at + 10].toInt() == 0 && png[at + 11].toInt() == 0) { "unknown compression or filter method" }
                    require(png[at + 12].toInt() == 0) { "interlaced PNGs are not read" }
                }
                "IDAT" -> idat.write(png, at, len)
                "IEND" -> ended = true
                else -> require(type[0].isLowerCase()) { "unknown critical chunk $type" }
            }
            require(width > 0) { "IHDR must come first" }
            p += 12 + len
        }
        val stride = width * 4
        val raw = Inflate.zlib(idat.toByteArray(), limit = (stride + 1) * height)
        require(raw.size == (stride + 1) * height) { "image data is ${raw.size} bytes" }
        val out = ByteArray(stride * height)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            val src = y * (stride + 1) + 1
            val row = y * stride
            for (i in 0 until stride) {
                val x = raw[src + i].toInt() and 0xFF
                val a = if (i >= 4) out[row + i - 4].toInt() and 0xFF else 0
                val b = if (y > 0) out[row - stride + i].toInt() and 0xFF else 0
                val c = if (i >= 4 && y > 0) out[row - stride + i - 4].toInt() and 0xFF else 0
                out[row + i] = when (filter) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> x + paeth(a, b, c)
                    else -> throw IllegalArgumentException("filter type $filter")
                }.toByte()
            }
        }
        return DecodedPng(width, height, out)
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun int(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
