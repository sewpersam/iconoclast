package dev.iconoclast.render.layer

import dev.iconoclast.render.Mapping
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.NoiseKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Readiness item 10: grain cells are a fixed fraction of the canvas and every render is the
 * area average of the cells under each pixel, so a preview is the pack's grain box-filtered
 * down, not a coarser, harsher grain.
 */
class GrainSamplingTest {

    private fun field(size: Int, scale: Float = 1f) = Mappings.noise(NoiseKind.GRAIN, 1, 9, Mapping(scale = scale), TextureOffset.of("camera"), size)

    /** Area average of [src], [n] x [n], onto [m] x [m]. */
    private fun downscale(src: FloatArray, n: Int, m: Int): FloatArray {
        val k = n.toDouble() / m
        fun weights(p: Int): List<Pair<Int, Double>> {
            val a = p * k
            val b = (p + 1) * k
            return (kotlin.math.floor(a).toInt()..minOf(n - 1, kotlin.math.ceil(b).toInt() - 1))
                .map { it to (minOf(b, it + 1.0) - maxOf(a, it.toDouble())) / k }.filter { it.second > 0 }
        }
        return FloatArray(m * m) { i ->
            var s = 0.0
            for ((y, wy) in weights(i / m)) for ((x, wx) in weights(i % m)) s += wy * wx * src[y * n + x]
            s.toFloat()
        }
    }

    @Test
    fun packGrainIsOneCellPerPixel() {
        val f = field(432)
        val mean = f.average()
        val variance = f.sumOf { (it - mean) * (it - mean) } / f.size
        // Uniform values on [0, 1): variance 1/12, not blurred by straddled cells.
        assertEquals(1.0 / 12, variance, 0.002)
    }

    @Test
    fun previewsAreThePackBoxFilteredDown() {
        for (scale in listOf(1f, 2f, 4f)) {
            val full = field(432, scale)
            for (m in listOf(144, 128)) {
                val expected = downscale(full, 432, m)
                val got = field(m, scale)
                val worst = got.indices.maxOf { kotlin.math.abs(got[it] - expected[it]) }
                assertEquals(0f, worst, 2e-3f, "scale $scale at $m px")
            }
        }
    }
}
