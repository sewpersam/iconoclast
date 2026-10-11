package dev.iconoclast.render.png

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class InflateTest {

    private fun javaDeflate(data: ByteArray, level: Int, strategy: Int = Deflater.DEFAULT_STRATEGY): ByteArray {
        val d = Deflater(level).apply { setStrategy(strategy); setInput(data); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        return out.toByteArray()
    }

    private val rnd = Random(11)
    private val cases = listOf(
        ByteArray(0),
        byteArrayOf(7),
        ByteArray(100_000),
        ByteArray(70_000) { rnd.nextInt(256).toByte() },
        "abcabcabcabd".repeat(5000).toByteArray(),
        ByteArray(300_000) { ((it / 7) % 13 + (it % 1000) / 500).toByte() },
    )

    /** Our own encoder, and zlib's stored, fixed-only, fast and best streams. */
    @Test
    fun inflatesEveryBlockType() {
        for ((k, c) in cases.withIndex()) {
            assertContentEquals(c, Inflate.zlib(Deflate.zlib(c)), "ours, case $k")
            for (level in listOf(0, 1, 6, 9)) assertContentEquals(c, Inflate.zlib(javaDeflate(c, level)), "zlib $level, case $k")
            assertContentEquals(c, Inflate.zlib(javaDeflate(c, 9, Deflater.HUFFMAN_ONLY)), "huffman only, case $k")
        }
        // Fixed Huffman blocks: zlib picks them for short inputs.
        val short = "hello hello hello".toByteArray()
        assertContentEquals(short, Inflate.zlib(javaDeflate(short, 9)))
    }

    @Test
    fun rejectsDamage() {
        val z = Deflate.zlib(cases[4])
        assertFailsWith<IllegalArgumentException> { Inflate.zlib(z.copyOf(z.size - 1)) }
        assertFailsWith<IllegalArgumentException> { Inflate.zlib(z.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }) }
        assertFailsWith<IllegalArgumentException> { Inflate.zlib(z.copyOf().also { it[1] = 0 }) }
        assertFailsWith<IllegalArgumentException> { Inflate.zlib(z, limit = 1000) }
    }
}
