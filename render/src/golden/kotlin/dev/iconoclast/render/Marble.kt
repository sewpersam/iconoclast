package dev.iconoclast.render

import dev.iconoclast.render.texture.TextureStore

/**
 * A procedural marble, original work: white stone with grey-green veins, from turbulence-
 * warped sine bands (Perlin, "An image synthesizer", SIGGRAPH 1985). [WIDTH] x [HEIGHT],
 * wider than tall so a crop matters. Plain integer hashing and StrictMath, so the pixels
 * and the canonical PNG are the same on the JVM and on the phone. The look pass's test
 * image: goldens, the contact sheet and the image pushed to the emulator's gallery.
 */
object Marble {
    const val WIDTH = 512
    const val HEIGHT = 384

    /** Premultiplied, opaque RGBA. */
    fun pixels(): ByteArray {
        val px = ByteArray(WIDTH * HEIGHT * 4)
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            val u = x / 96f
            val v = y / 96f
            val t = turbulence(u, v)
            val band = StrictMath.sin((u * 2.6f + v * 1.2f + 7f * t).toDouble()).toFloat()
            // Dark veins where the band crosses zero, finer ones across them, a cloud over the stone.
            val vein = 1f - minOf(1f, kotlin.math.abs(band) * 3f)
            val fine = 1f - minOf(1f, kotlin.math.abs(StrictMath.sin((u * 6.2f - v * 3.4f + 11f * t).toDouble()).toFloat()) * 7f)
            val cloud = 0.95f - 0.25f * turbulence(u * 0.7f + 7f, v * 0.7f + 3f)
            val dark = (0.85f * vein * vein + 0.45f * fine).coerceIn(0f, 1f)
            val r = cloud * (1f - dark) + 0.16f * dark
            val g = cloud * (1f - dark) + 0.24f * dark
            val b = cloud * (1f - dark) + 0.22f * dark
            val o = (y * WIDTH + x) * 4
            px[o] = byte(r); px[o + 1] = byte(g); px[o + 2] = byte(b); px[o + 3] = -1
        }
        return px
    }

    /** The canonical texture PNG, and its hash: what a theme names. */
    val PNG: ByteArray by lazy { TextureStore.encode(WIDTH, HEIGHT, pixels()) }
    val HASH: String by lazy { TextureStore.sha256(PNG) }

    private fun byte(f: Float): Byte = (f.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

    private fun turbulence(x: Float, y: Float): Float {
        var sum = 0f
        var f = 1f
        var a = 0.5f
        repeat(5) {
            sum += a * kotlin.math.abs(value(x * f, y * f) * 2f - 1f)
            f *= 2f
            a *= 0.5f
        }
        return sum
    }

    private fun value(x: Float, y: Float): Float {
        val ix = StrictMath.floor(x.toDouble()).toInt()
        val iy = StrictMath.floor(y.toDouble()).toInt()
        val fx = x - ix
        val fy = y - iy
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val a = hash(ix, iy)
        val b = hash(ix + 1, iy)
        val c = hash(ix, iy + 1)
        val d = hash(ix + 1, iy + 1)
        val top = a + (b - a) * sx
        return top + (c + (d - c) * sx - top) * sy
    }

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h ushr 8).toFloat() / (1 shl 24)
    }
}
