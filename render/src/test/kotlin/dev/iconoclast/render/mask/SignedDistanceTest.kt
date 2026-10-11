package dev.iconoclast.render.mask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Readiness item 7. A disc with an off-grid centre and radius, its coverage supersampled
 * 16 x 16 per pixel, against the exact signed distance R - |p - c|. Three measures in a
 * band 8 px either side of the edge: the field's error, the direction of its gradient,
 * and the shading a bevel gets from it under a low light, the banding the eye sees. The
 * binary build is measured first, so the probe is shown to see the defect; then the
 * anti-aliased field must clear it.
 */
class SignedDistanceTest {
    private val n = 128
    private val cx = 63.7f
    private val cy = 64.3f
    private val r = 37.37f

    private val coverage = FloatArray(n * n).also { cov ->
        for (y in 0 until n) for (x in 0 until n) {
            var hits = 0
            for (sy in 0 until 16) for (sx in 0 until 16) {
                val px = x - 0.5f + (sx + 0.5f) / 16f - cx
                val py = y - 0.5f + (sy + 0.5f) / 16f - cy
                if (px * px + py * py <= r * r) hits++
            }
            cov[y * n + x] = hits / 256f
        }
    }

    private fun exact(x: Int, y: Int): Float {
        val dx = x - cx
        val dy = y - cy
        return r - StrictMath.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * Max field error from one pixel off the edge outward, where the edge pixels' own
     * estimates no longer decide it alone; max gradient direction error in degrees; max
     * bevel shading error, the banding.
     */
    private fun measure(field: FloatArray): Triple<Float, Float, Float> {
        var err = 0f
        var angle = 0f
        var shade = 0f
        val size = 6f
        val lx = (StrictMath.cos(StrictMath.toRadians(120.0)) * StrictMath.cos(StrictMath.toRadians(30.0))).toFloat()
        val ly = (-StrictMath.sin(StrictMath.toRadians(120.0)) * StrictMath.cos(StrictMath.toRadians(30.0))).toFloat()
        val lz = StrictMath.sin(StrictMath.toRadians(30.0)).toFloat()
        fun height(d: Float) = d.coerceIn(0f, size)
        for (y in 2 until n - 2) for (x in 2 until n - 2) {
            val d = exact(x, y)
            if (kotlin.math.abs(d) in 1f..8f) err = maxOf(err, kotlin.math.abs(field[y * n + x] - d))
            if (kotlin.math.abs(d) in 1.5f..8f) {
                val gx = (field[y * n + x + 1] - field[y * n + x - 1]) / 2f
                val gy = (field[(y + 1) * n + x] - field[(y - 1) * n + x]) / 2f
                val rx = cx - x
                val ry = cy - y
                val cos = (gx * rx + gy * ry) / StrictMath.sqrt(((gx * gx + gy * gy) * (rx * rx + ry * ry)).toDouble()).toFloat()
                angle = maxOf(angle, StrictMath.toDegrees(StrictMath.acos(cos.coerceIn(-1f, 1f).toDouble())).toFloat())
            }
            if (d in 1f..(size - 1f)) {
                fun lit(f: (Int, Int) -> Float): Float {
                    val hx = (height(f(x + 1, y)) - height(f(x - 1, y))) / 2f
                    val hy = (height(f(x, y + 1)) - height(f(x, y - 1))) / 2f
                    val len = StrictMath.sqrt((hx * hx + hy * hy + 1f).toDouble()).toFloat()
                    return (-hx * lx - hy * ly + lz) / len
                }
                shade = maxOf(shade, kotlin.math.abs(lit { a, b -> field[b * n + a] } - lit(::exact)))
            }
        }
        return Triple(err, angle, shade)
    }

    @Test
    fun binaryFieldBands() {
        val (err, angle, shade) = measure(SignedDistance.binary(coverage, n, n))
        println("binary EDT: max error %.3f px, gradient off by up to %.1f deg, shading off by up to %.3f".format(err, angle, shade))
        assertTrue(err > 0.4f, "binary error $err")
        assertTrue(angle > 10f, "binary angle $angle")
        assertTrue(shade > 0.15f, "binary shading $shade")
    }

    @Test
    fun antiAliasedFieldIsSmooth() {
        val (err, angle, shade) = measure(SignedDistance.of(coverage, n, n))
        println("anti-aliased EDT: max error %.3f px, gradient off by up to %.1f deg, shading off by up to %.3f".format(err, angle, shade))
        assertTrue(err < 0.12f, "error $err")
        assertTrue(angle < 4f, "angle $angle")
        assertTrue(shade < 0.08f, "shading $shade")
    }

    @Test
    fun straightEdgesAreExact() {
        // A half plane at x = 20.3: pixel x covers [x - 0.5, x + 0.5].
        val w = 40
        val cov = FloatArray(w * w) { ((20.3f - (it % w - 0.5f)).coerceIn(0f, 1f)) }
        val f = SignedDistance.of(cov, w, w)
        for (y in 0 until w) for (x in 0 until w) assertEquals(20.3f - x, f[y * w + x], 1e-4f, "($x, $y)")
    }

    /** Faint coverage that never reaches one half, a lone line's ramp in a gap, plants no edge. */
    @Test
    fun faintSpecksAreNoEdge() {
        val w = 40
        val cov = FloatArray(w * w) { ((20.3f - (it % w - 0.5f)).coerceIn(0f, 1f)) }
        cov[10 * w + 28] = 0.1f
        cov[10 * w + 29] = 0.2f
        val f = SignedDistance.of(cov, w, w)
        for (x in 26..31) assertEquals(20.3f - x, f[10 * w + x], 1e-4f, "($x, 10)")
    }

    @Test
    fun noEdgeIsFarInsideOrOut() {
        assertEquals(SignedDistance.NO_EDGE, SignedDistance.of(FloatArray(16) { 1f }, 4, 4)[5])
        assertEquals(-SignedDistance.NO_EDGE, SignedDistance.of(FloatArray(16), 4, 4)[5])
    }
}
