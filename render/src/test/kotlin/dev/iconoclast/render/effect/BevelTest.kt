package dev.iconoclast.render.effect

import dev.iconoclast.render.mask.Blur
import dev.iconoclast.render.math.SplitMix64
import kotlin.test.Test
import kotlin.test.assertTrue

/** The bevel's cropped blur is the whole-grid blur bit for bit. */
class BevelTest {

    private fun sparse(n: Int, seed: Long, x0: Int, x1: Int, y0: Int, y1: Int): FloatArray {
        val r = SplitMix64(seed)
        val h = FloatArray(n * n)
        for (y in y0..y1) for (x in x0..x1) if (r.nextFloat() < 0.15f) h[y * n + x] = r.nextFloat() * 12f
        return h
    }

    @Test
    fun croppedBlurEqualsTheWholeGrid() {
        val n = 160
        val sigmas = listOf(0.3f, 0.75f, 1.5f, 2.2f, Blur.EXACT_SIGMA, 3.0001f, 5f, 11f)
        val masks = mapOf(
            "centre" to sparse(n, 1, 50, 110, 60, 100),
            "small" to sparse(n, 2, 79, 82, 79, 81),
            "left and top edges" to sparse(n, 3, 0, 30, 0, 25),
            "right and bottom edges" to sparse(n, 4, 140, n - 1, 120, n - 1),
            "spans the grid" to sparse(n, 5, 0, n - 1, 70, 90),
            "one corner pixel" to FloatArray(n * n).also { it[n * n - 1] = 7f },
            "empty" to FloatArray(n * n),
        )
        for ((name, h) in masks) for (sigma in sigmas) {
            val full = Blur.gaussian(h, n, n, sigma)
            val cropped = Bevel.blurNonZero(h, n, sigma)
            assertTrue(full.contentEquals(cropped), "$name at sigma $sigma")
        }
    }
}
