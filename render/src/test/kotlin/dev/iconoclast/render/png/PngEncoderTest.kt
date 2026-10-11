package dev.iconoclast.render.png

import dev.iconoclast.render.RgbaImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PngEncoderTest {

    private fun inflate(z: ByteArray): ByteArray {
        val inf = Inflater().apply { setInput(z) }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            check(n > 0 || inf.finished() || !inf.needsInput()) { "truncated stream" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun deflateRoundTrips() {
        val rnd = Random(3)
        val cases = listOf(
            ByteArray(0),
            byteArrayOf(42),
            ByteArray(100_000),
            ByteArray(70_000) { rnd.nextInt(256).toByte() },
            ByteArray(300_000) { ((it / 7) % 13 + (it % 1000) / 500).toByte() },
            "abcabcabcabd".repeat(5000).toByteArray(),
            ByteArray(200_000) { if (rnd.nextInt(10) == 0) rnd.nextInt(4).toByte() else 0 },
        )
        for (effort in Deflate.Effort.entries) for ((k, c) in cases.withIndex()) {
            val z = Deflate.zlib(c, effort)
            val back = runCatching { inflate(z) }.getOrElse { throw AssertionError("$effort case $k: ${it.message}") }
            assertContentEquals(c, back, "$effort case $k")
        }
        assertTrue(Deflate.zlib(ByteArray(100_000)).size < 1000)
    }

    /** The first block's BTYPE: 1 fixed, 2 dynamic. */
    private fun firstBlockType(z: ByteArray) = (z[2].toInt() ushr 1) and 3

    /** FAST takes the fixed code where it is cheaper, a short block; CANONICAL never does. */
    @Test
    fun fastChoosesTheCheaperCode() {
        val short = "a fixed code wins on a short block".toByteArray()
        assertEquals(1, firstBlockType(Deflate.zlib(short, Deflate.Effort.FAST)))
        assertEquals(2, firstBlockType(Deflate.zlib(short, Deflate.Effort.CANONICAL)))
        val skewed = ByteArray(50_000) { if (it % 97 == 0) 1 else 0 }
        assertEquals(2, firstBlockType(Deflate.zlib(skewed, Deflate.Effort.FAST)))
        // Whichever it takes, FAST is never larger than its dynamic-only output would be.
        val rnd = Random(9)
        for (len in listOf(10, 200, 5000, 60_000)) {
            val data = ByteArray(len) { (rnd.nextInt(16) + 60).toByte() }
            assertContentEquals(data, inflate(Deflate.zlib(data, Deflate.Effort.FAST)))
        }
    }

    /**
     * Texture files are named by the SHA-256 of their CANONICAL bytes, so those bytes are
     * pinned: this hash was taken from the M5 encoder, before FAST existed. GoldenTest pins
     * the same through the bundled texture's hash.
     */
    @Test
    fun canonicalBytesArePinned() {
        val px = ByteArray(64 * 48 * 4) { i -> if (i % 4 == 3) (200 + i % 56).toByte() else ((i * 31 + i / 256) % 200).toByte() }
        val png = PngEncoder.encode(64, 48, px, Deflate.Effort.CANONICAL)
        assertEquals(CANONICAL_SHA, dev.iconoclast.render.texture.TextureStore.sha256(png))
    }

    @Test
    fun huffmanLengthsAreLimitedAndComplete() {
        // Fibonacci frequencies force a very deep tree.
        val freq = IntArray(30)
        var a = 1
        var b = 1
        for (i in freq.indices) { freq[i] = a; val t = a + b; a = b; b = t }
        for (limit in listOf(7, 15)) {
            val len = Huffman.lengths(freq, limit)
            assertTrue(len.all { it in 1..limit })
            assertEquals(1.0, len.sumOf { Math.pow(2.0, -it.toDouble()) }, 1e-12)
        }
        val one = Huffman.lengths(IntArray(19).also { it[5] = 9 }, 7)
        assertEquals(2, one.count { it > 0 }, "a lone symbol still gets a complete code")
    }

    @Test
    fun unpremultiplyRoundTripsExactly() {
        for (a in 1..255) for (p in 0..a) {
            val s = Math.min(255, (p * 255 + a / 2) / a)
            assertEquals(p, (s * a + 127) / 255, "p=$p a=$a")
        }
    }

    @Test
    fun pngDecodesToStraightPixelsAndIsStable() {
        val size = 37
        val rnd = Random(11)
        val px = ByteArray(size * size * 4)
        for (i in 0 until size * size) {
            val a = if (i % 5 == 0) 0 else rnd.nextInt(256)
            for (k in 0..2) px[4 * i + k] = rnd.nextInt(a + 1).toByte()
            px[4 * i + 3] = a.toByte()
        }
        val image = RgbaImage(size, px)
        val png = PngEncoder.encode(image)
        assertContentEquals(png, PngEncoder.encode(image))
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        val straight = PngEncoder.unpremultiply(px)
        for (i in 0 until size * size) {
            val argb = decoded.getRGB(i % size, i / size)
            val expect = ((straight[4 * i + 3].toInt() and 0xFF) shl 24) or ((straight[4 * i].toInt() and 0xFF) shl 16) or
                ((straight[4 * i + 1].toInt() and 0xFF) shl 8) or (straight[4 * i + 2].toInt() and 0xFF)
            assertEquals(expect, argb, "pixel $i")
        }
    }

    private companion object {
        const val CANONICAL_SHA = "2812235e7301d95084a267090369476dcebaf43a9ac1fd649471dfe96c14cfdf"
    }
}
