package dev.iconoclast.render.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Readiness item 8: one separable Gaussian, exact for small sigma, box passes for large. */
class BlurTest {

    /** The impulse response: mass 1, centred, variance sigma squared, symmetric. */
    @Test
    fun impulseResponseIsGaussian() {
        val n = 201
        for (sigma in listOf(0.7f, 2f, 3f, 3.01f, 6f, 12.5f, 21.6f)) {
            val m = FloatArray(n * n).also { it[100 * n + 100] = 1f }
            val b = Blur.gaussian(m, n, n, sigma)
            var mass = 0.0
            var vx = 0.0
            var vy = 0.0
            for (y in 0 until n) for (x in 0 until n) {
                val v = b[y * n + x].toDouble()
                mass += v
                vx += v * (x - 100) * (x - 100)
                vy += v * (y - 100) * (y - 100)
            }
            assertEquals(1.0, mass, 1e-4, "mass at sigma $sigma")
            val variance = (sigma * sigma).toDouble()
            // The sampled kernel is cut at 3 sigma; the box cascade is exact by construction.
            assertEquals(variance, vx, variance * (if (sigma <= Blur.EXACT_SIGMA) 0.02 else 0.002), "x variance at sigma $sigma")
            assertEquals(vx, vy, 1e-3 * variance + 1e-6, "y variance at sigma $sigma")
            for (d in 1..20) assertEquals(b[100 * n + 100 - d], b[100 * n + 100 + d], 1e-6f, "symmetric at $d, sigma $sigma")
            // Gaussian-shaped: the centre near the true peak 1 / (2 pi sigma^2); three boxes run about 10% flat.
            val peak = 1.0 / (2 * Math.PI * variance)
            assertEquals(peak, b[100 * n + 100].toDouble(), peak * 0.12, "peak at sigma $sigma")
        }
    }

    /** Across the switch the two forms agree, so a slider passing 3 px does not jump. */
    @Test
    fun exactAndBoxAgreeAtTheSwitch() {
        val n = 96
        val disc = FloatArray(n * n) { val x = it % n - 48; val y = it / n - 48; if (x * x + y * y < 400) 1f else 0f }
        val below = Blur.gaussian(disc, n, n, 3f)
        val above = Blur.gaussian(disc, n, n, 3.01f)
        val diff = below.indices.maxOf { kotlin.math.abs(below[it] - above[it]) }
        assertTrue(diff < 0.03f, "max difference $diff")
    }

    @Test
    fun zeroOutsideTheGrid() {
        val n = 32
        val full = FloatArray(n * n) { 1f }
        for (sigma in listOf(2f, 8f)) {
            val b = Blur.gaussian(full, n, n, sigma)
            assertTrue(b[0] < 0.4f, "the corner fades at sigma $sigma: ${b[0]}")
            assertTrue(b[16 * n + 16] > 0.6f)
        }
        assertTrue(Blur.gaussian(full, n, n, 0f).all { it == 1f })
    }
}
