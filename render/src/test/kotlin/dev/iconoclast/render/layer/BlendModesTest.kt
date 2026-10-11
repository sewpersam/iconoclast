package dev.iconoclast.render.layer

import dev.iconoclast.render.BlendMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every blend mode against a separate double-precision transcription of the W3C
 * Compositing and Blending Level 1 formulas, on fixed pixel pairs, opaque and translucent.
 */
class BlendModesTest {

    /** Straight colour and alpha. */
    private data class Px(val r: Double, val g: Double, val b: Double, val a: Double)

    private val pairs = listOf(
        Px(0.8, 0.4, 0.2, 1.0) to Px(0.3, 0.6, 0.9, 1.0),
        Px(0.1, 0.9, 0.5, 1.0) to Px(0.7, 0.2, 0.25, 1.0),
        Px(1.0, 1.0, 1.0, 1.0) to Px(0.0, 0.0, 0.0, 1.0),
        Px(0.0, 0.0, 0.0, 1.0) to Px(1.0, 0.5, 0.0, 1.0),
        Px(0.6, 0.6, 0.6, 0.5) to Px(0.2, 0.4, 0.8, 0.75),
        Px(0.9, 0.1, 0.3, 0.3) to Px(0.5, 0.5, 0.1, 1.0),
        Px(0.25, 0.75, 0.5, 0.8) to Px(0.0, 0.0, 0.0, 0.0),
        Px(0.4, 0.2, 0.9, 0.0) to Px(0.3, 0.3, 0.3, 0.6),
        Px(0.95, 0.05, 0.6, 1.0) to Px(0.15, 0.85, 0.95, 0.4),
    )

    @Test
    fun everyModeMatchesTheW3cFormulas() {
        for (mode in BlendMode.entries) for ((k, pair) in pairs.withIndex()) {
            val (s, d) = pair
            val buf = floatArrayOf((d.r * d.a).toFloat(), (d.g * d.a).toFloat(), (d.b * d.a).toFloat(), d.a.toFloat())
            BlendModes.composite(mode, (s.r * s.a).toFloat(), (s.g * s.a).toFloat(), (s.b * s.a).toFloat(), s.a.toFloat(), buf, 0)
            val want = reference(mode, s, d)
            for (c in 0..3) assertEquals(want[c], buf[c].toDouble(), 2e-6, "$mode pair $k channel $c")
        }
    }

    /** A few values worked by hand, opaque over opaque. */
    @Test
    fun handWorkedValues() {
        fun one(mode: BlendMode, s: Float, d: Float): Float {
            val buf = floatArrayOf(d, d, d, 1f)
            BlendModes.composite(mode, s, s, s, 1f, buf, 0)
            return buf[0]
        }
        assertEquals(0.25f, one(BlendMode.MULTIPLY, 0.5f, 0.5f))
        assertEquals(0.75f, one(BlendMode.SCREEN, 0.5f, 0.5f))
        assertEquals(0.5f, one(BlendMode.DIFFERENCE, 0.25f, 0.75f))
        assertEquals(1f, one(BlendMode.ADD, 0.6f, 0.7f))
        assertEquals(0.5f, one(BlendMode.COLOR_DODGE, 0.5f, 0.25f))
        assertEquals(0.5f, one(BlendMode.EXCLUSION, 0.5f, 0.25f), 1e-7f)
        assertEquals(0.3f, one(BlendMode.NORMAL, 0.3f, 0.9f))
    }

    private fun reference(mode: BlendMode, s: Px, d: Px): DoubleArray {
        val cs = doubleArrayOf(s.r, s.g, s.b)
        val cb = doubleArrayOf(d.r, d.g, d.b)
        val premulS = DoubleArray(3) { cs[it] * s.a }
        val premulB = DoubleArray(3) { cb[it] * d.a }
        if (mode == BlendMode.ADD) {
            return DoubleArray(4) { if (it < 3) min(1.0, premulS[it] + premulB[it]) else min(1.0, s.a + d.a) }
        }
        val ao = s.a + d.a * (1 - s.a)
        val b: DoubleArray = when (mode) {
            BlendMode.NORMAL -> cs
            BlendMode.HUE -> setLum(setSat(cs, sat(cb)), lum(cb))
            BlendMode.SATURATION -> setLum(setSat(cb, sat(cs)), lum(cb))
            BlendMode.COLOR -> setLum(cs, lum(cb))
            BlendMode.LUMINOSITY -> setLum(cb, lum(cs))
            else -> DoubleArray(3) { sep(mode, cb[it], cs[it]) }
        }
        return DoubleArray(4) {
            if (it == 3) ao
            else (1 - d.a) * premulS[it] + (1 - s.a) * premulB[it] + s.a * d.a * b[it]
        }
    }

    private fun sep(mode: BlendMode, b: Double, s: Double): Double = when (mode) {
        BlendMode.MULTIPLY -> b * s
        BlendMode.SCREEN -> b + s - b * s
        BlendMode.OVERLAY -> sep(BlendMode.HARD_LIGHT, s, b)
        BlendMode.DARKEN -> min(b, s)
        BlendMode.LIGHTEN -> max(b, s)
        BlendMode.COLOR_DODGE -> if (b == 0.0) 0.0 else if (s == 1.0) 1.0 else min(1.0, b / (1 - s))
        BlendMode.COLOR_BURN -> if (b == 1.0) 1.0 else if (s == 0.0) 0.0 else 1 - min(1.0, (1 - b) / s)
        BlendMode.HARD_LIGHT -> if (s <= 0.5) b * 2 * s else sep(BlendMode.SCREEN, b, 2 * s - 1)
        BlendMode.SOFT_LIGHT -> if (s <= 0.5) b - (1 - 2 * s) * b * (1 - b) else {
            val dd = if (b <= 0.25) ((16 * b - 12) * b + 4) * b else sqrt(b)
            b + (2 * s - 1) * (dd - b)
        }
        BlendMode.DIFFERENCE -> abs(b - s)
        BlendMode.EXCLUSION -> b + s - 2 * b * s
        else -> error("not separable")
    }

    private fun lum(c: DoubleArray) = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]

    private fun clip(c: DoubleArray): DoubleArray {
        val l = lum(c)
        val n = c.min()
        val x = c.max()
        var o = c.copyOf()
        if (n < 0) o = DoubleArray(3) { l + (o[it] - l) * l / (l - n) }
        if (x > 1) o = DoubleArray(3) { l + (o[it] - l) * (1 - l) / (x - l) }
        return o
    }

    private fun setLum(c: DoubleArray, l: Double): DoubleArray {
        val d = l - lum(c)
        return clip(DoubleArray(3) { c[it] + d })
    }

    private fun sat(c: DoubleArray) = c.max() - c.min()

    private fun setSat(c: DoubleArray, s: Double): DoubleArray {
        val order = (0..2).sortedBy { c[it] }
        val o = DoubleArray(3)
        val (lo, mid, hi) = order
        if (c[hi] > c[lo]) {
            o[mid] = (c[mid] - c[lo]) * s / (c[hi] - c[lo])
            o[hi] = s
        }
        return o
    }
    /** The goldens, and so ART byte identity, exercise every blend mode. */
    @Test
    fun goldensCoverEveryBlendMode() {
        val used = dev.iconoclast.render.GoldenSet.CASES.flatMap { (_, theme, _) ->
            theme.layers.filterIsInstance<dev.iconoclast.render.PaintLayer>().map { it.blend }
        }.toSet()
        assertEquals(BlendMode.entries.toSet(), used)
    }
}
