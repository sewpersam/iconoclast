package dev.iconoclast.render.layer

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.math.sqrt

/**
 * The 17 blend modes, from W3C Compositing and Blending Level 1 (2015), in plain float
 * arithmetic.
 *
 * Inputs and the buffer are premultiplied RGBA. The source is already scaled by the
 * layer's coverage (clip, matte, opacity). Each blend works on unpremultiplied colours,
 * `C = c / a`, and 0 where `a` is 0, then composites source-over per section 10.1:
 *
 *     co = cs (1 - ab) + cb (1 - as) + as ab B(Cb, Cs)
 *     ao = as + ab - as ab
 *
 * with co clamped to [0, ao] against rounding. [BlendMode.NORMAL] is plain source-over,
 * `co = cs + cb (1 - as)`, the same function with B = Cs, written out so the base stack
 * reproduces the M4 renderer's bytes. [BlendMode.ADD] is the Porter-Duff plus operator,
 * section 9.1.12, clamped: `co = min(1, cs + cb)`, `ao = min(1, as + ab)`.
 */
internal object BlendModes {

    fun composite(mode: BlendMode, sr: Float, sg: Float, sb: Float, sa: Float, buf: FloatArray, o: Int) {
        when (mode) {
            BlendMode.NORMAL -> {
                val inv = 1f - sa
                buf[o] = sr + buf[o] * inv
                buf[o + 1] = sg + buf[o + 1] * inv
                buf[o + 2] = sb + buf[o + 2] * inv
                buf[o + 3] = sa + buf[o + 3] * inv
            }
            BlendMode.ADD -> {
                buf[o] = minOf(1f, sr + buf[o])
                buf[o + 1] = minOf(1f, sg + buf[o + 1])
                buf[o + 2] = minOf(1f, sb + buf[o + 2])
                buf[o + 3] = minOf(1f, sa + buf[o + 3])
            }
            else -> {
                if (sa == 0f) return
                val ab = buf[o + 3]
                val br = buf[o]
                val bg = buf[o + 1]
                val bb = buf[o + 2]
                val ao = sa + ab - sa * ab
                val csr = sr / sa
                val csg = sg / sa
                val csb = sb / sa
                val cbr = if (ab > 0f) br / ab else 0f
                val cbg = if (ab > 0f) bg / ab else 0f
                val cbb = if (ab > 0f) bb / ab else 0f
                val out = FloatArray(3)
                if (mode.ordinal >= BlendMode.HUE.ordinal) nonSeparable(mode, cbr, cbg, cbb, csr, csg, csb, out)
                else {
                    out[0] = separable(mode, cbr, csr)
                    out[1] = separable(mode, cbg, csg)
                    out[2] = separable(mode, cbb, csb)
                }
                val k = sa * ab
                buf[o] = (sr * (1f - ab) + br * (1f - sa) + k * out[0]).coerceIn(0f, ao)
                buf[o + 1] = (sg * (1f - ab) + bg * (1f - sa) + k * out[1]).coerceIn(0f, ao)
                buf[o + 2] = (sb * (1f - ab) + bb * (1f - sa) + k * out[2]).coerceIn(0f, ao)
                buf[o + 3] = ao
            }
        }
    }

    /** B(Cb, Cs) for the separable modes, section 9.1. */
    fun separable(mode: BlendMode, cb: Float, cs: Float): Float = when (mode) {
        BlendMode.MULTIPLY -> cb * cs
        BlendMode.SCREEN -> screen(cb, cs)
        BlendMode.OVERLAY -> hardLight(cs, cb)
        BlendMode.DARKEN -> minOf(cb, cs)
        BlendMode.LIGHTEN -> maxOf(cb, cs)
        BlendMode.COLOR_DODGE -> when {
            cb == 0f -> 0f
            cs >= 1f -> 1f
            else -> minOf(1f, cb / (1f - cs))
        }
        BlendMode.COLOR_BURN -> when {
            cb >= 1f -> 1f
            cs == 0f -> 0f
            else -> 1f - minOf(1f, (1f - cb) / cs)
        }
        BlendMode.HARD_LIGHT -> hardLight(cb, cs)
        BlendMode.SOFT_LIGHT -> if (cs <= 0.5f) {
            cb - (1f - 2f * cs) * cb * (1f - cb)
        } else {
            val d = if (cb <= 0.25f) ((16f * cb - 12f) * cb + 4f) * cb else sqrt(cb)
            cb + (2f * cs - 1f) * (d - cb)
        }
        BlendMode.DIFFERENCE -> kotlin.math.abs(cb - cs)
        BlendMode.EXCLUSION -> cb + cs - 2f * cb * cs
        else -> error("$mode is not separable")
    }

    private fun screen(cb: Float, cs: Float) = cb + cs - cb * cs

    private fun hardLight(cb: Float, cs: Float) = if (cs <= 0.5f) cb * (2f * cs) else screen(cb, 2f * cs - 1f)

    /** B(Cb, Cs) for hue, saturation, color and luminosity, section 9.2, into [out]. */
    fun nonSeparable(mode: BlendMode, br: Float, bg: Float, bb: Float, sr: Float, sg: Float, sb: Float, out: FloatArray) {
        when (mode) {
            BlendMode.HUE -> { setSat(sr, sg, sb, sat(br, bg, bb), out); setLum(out[0], out[1], out[2], lum(br, bg, bb), out) }
            BlendMode.SATURATION -> { setSat(br, bg, bb, sat(sr, sg, sb), out); setLum(out[0], out[1], out[2], lum(br, bg, bb), out) }
            BlendMode.COLOR -> setLum(sr, sg, sb, lum(br, bg, bb), out)
            BlendMode.LUMINOSITY -> setLum(br, bg, bb, lum(sr, sg, sb), out)
            else -> error("$mode is separable")
        }
    }

    fun lum(r: Float, g: Float, b: Float): Float = 0.3f * r + 0.59f * g + 0.11f * b

    private fun sat(r: Float, g: Float, b: Float): Float = maxOf(r, g, b) - minOf(r, g, b)

    private fun setLum(r: Float, g: Float, b: Float, l: Float, out: FloatArray) {
        val d = l - lum(r, g, b)
        clipColor(r + d, g + d, b + d, out)
    }

    private fun clipColor(r: Float, g: Float, b: Float, out: FloatArray) {
        val l = lum(r, g, b)
        val n = minOf(r, g, b)
        val x = maxOf(r, g, b)
        var cr = r
        var cg = g
        var cb = b
        if (n < 0f) {
            cr = l + (cr - l) * l / (l - n)
            cg = l + (cg - l) * l / (l - n)
            cb = l + (cb - l) * l / (l - n)
        }
        if (x > 1f) {
            cr = l + (cr - l) * (1f - l) / (x - l)
            cg = l + (cg - l) * (1f - l) / (x - l)
            cb = l + (cb - l) * (1f - l) / (x - l)
        }
        out[0] = cr
        out[1] = cg
        out[2] = cb
    }

    /** SetSat: the largest channel becomes [s], the smallest 0, the middle scaled between. */
    private fun setSat(r: Float, g: Float, b: Float, s: Float, out: FloatArray) {
        val c = floatArrayOf(r, g, b)
        // Channel indices sorted by value; ties keep channel order.
        var lo = 0
        var mid = 1
        var hi = 2
        if (c[mid] < c[lo]) { val t = lo; lo = mid; mid = t }
        if (c[hi] < c[mid]) { val t = mid; mid = hi; hi = t }
        if (c[mid] < c[lo]) { val t = lo; lo = mid; mid = t }
        val max = hi
        val min = lo
        val v = FloatArray(3)
        if (c[max] > c[min]) {
            v[mid] = (c[mid] - c[min]) * s / (c[max] - c[min])
            v[max] = s
        }
        out[0] = v[0]
        out[1] = v[1]
        out[2] = v[2]
    }
}
