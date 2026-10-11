package dev.iconoclast.render.png

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PngDecoderTest {

    /** A premultiplied image survives encode, decode and re-premultiply exactly, at any shape. */
    @Test
    fun roundTripsTheEncoder() {
        val rnd = Random(5)
        for ((w, h) in listOf(1 to 1, 37 to 5, 64 to 64, 300 to 41)) {
            val px = ByteArray(w * h * 4)
            for (i in px.indices step 4) {
                val a = if (rnd.nextInt(4) == 0) 255 else rnd.nextInt(256)
                for (k in 0..2) px[i + k] = rnd.nextInt(a + 1).toByte()
                px[i + 3] = a.toByte()
            }
            val png = PngEncoder.encode(w, h, px, Deflate.Effort.FAST)
            val d = PngDecoder.decode(png)
            assertEquals(w to h, d.width to d.height)
            val back = ByteArray(px.size)
            for (i in px.indices step 4) {
                val a = d.straight[i + 3].toInt() and 0xFF
                for (k in 0..2) back[i + k] = (((d.straight[i + k].toInt() and 0xFF) * a + 127) / 255).toByte()
                back[i + 3] = a.toByte()
            }
            assertContentEquals(px, back, "${w}x$h")
        }
    }

    @Test
    fun rejectsDamageAndOversize() {
        val png = PngEncoder.encode(8, 4, ByteArray(8 * 4 * 4) { 0xFF.toByte() }, Deflate.Effort.FAST)
        assertFailsWith<IllegalArgumentException> { PngDecoder.decode(png.copyOf().also { it[40] = (it[40] + 1).toByte() }) }
        assertFailsWith<IllegalArgumentException> { PngDecoder.decode(png.copyOf(png.size - 5)) }
        assertFailsWith<IllegalArgumentException> { PngDecoder.decode(png, maxSide = 7) }
        assertFailsWith<IllegalArgumentException> { PngDecoder.decode(ByteArray(20)) }
    }
}
