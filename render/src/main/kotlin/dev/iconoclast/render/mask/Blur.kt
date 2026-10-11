package dev.iconoclast.render.mask

import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor
import dev.iconoclast.render.math.sqrt

/**
 * The shared separable Gaussian blur. Two forms:
 *
 * - [feather]: the matte feather of M5, kept bit for bit: sigma half the radius, taps out
 *   to the radius, edges clamped, weights from StrictMath.exp normalised to 1.
 * - [gaussian]: what the effects use, outside the grid read as 0, so a mask fades out at
 *   the canvas edge rather than smearing it. Up to [EXACT_SIGMA] it convolves with the
 *   sampled kernel out to three sigma, normalised to 1. Above, the cost of taps grows
 *   with the radius, so three box passes per axis stand in for the Gaussian (Wells, "Efficient
 *   synthesis of Gaussian filters by cascaded uniform filters", IEEE PAMI 1986), each an
 *   extended box whose fractional end weight makes the cascade's variance exactly sigma
 *   squared. Each box is a running sum in double: the cost per pixel is the same at any
 *   radius.
 *
 * Plain arithmetic in a fixed order, StrictMath for exp and sqrt.
 */
object Blur {

    /** Above this sigma, in pixels, [gaussian] switches to box passes. */
    const val EXACT_SIGMA = 3f

    fun feather(m: FloatArray, size: Int, radius: Float): FloatArray {
        val half = ceil(radius).toInt()
        val sigma = radius / 2.0
        val w = FloatArray(2 * half + 1) { StrictMath.exp(-((it - half) * (it - half)) / (2 * sigma * sigma)).toFloat() }
        val total = w.sum()
        for (k in w.indices) w[k] /= total
        val tmp = FloatArray(m.size)
        for (y in 0 until size) for (x in 0 until size) {
            var s = 0f
            for (k in w.indices) s += w[k] * m[y * size + (x + k - half).coerceIn(0, size - 1)]
            tmp[y * size + x] = s
        }
        val out = FloatArray(m.size)
        for (y in 0 until size) for (x in 0 until size) {
            var s = 0f
            for (k in w.indices) s += w[k] * tmp[(y + k - half).coerceIn(0, size - 1) * size + x]
            out[y * size + x] = s
        }
        return out
    }

    /** [m], [w] x [h], blurred with standard deviation [sigma] pixels, zero outside. A new array. */
    fun gaussian(m: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma <= 0f) return m.copyOf()
        val rows = FloatArray(m.size)
        val out = FloatArray(m.size)
        if (sigma <= EXACT_SIGMA) {
            val k = kernel(sigma)
            convolve(m, rows, w, h, k, horizontal = true)
            convolve(rows, out, w, h, k, horizontal = false)
        } else {
            val box = extendedBox(sigma)
            boxes(m, rows, w, h, box, horizontal = true)
            boxes(rows, out, w, h, box, horizontal = false)
        }
        return out
    }

    /** Sampled Gaussian taps out to ceil(3 sigma), normalised to 1. */
    internal fun kernel(sigma: Float): FloatArray {
        val half = ceil(3f * sigma).toInt()
        val s2 = 2.0 * sigma * sigma
        val k = FloatArray(2 * half + 1) { StrictMath.exp(-((it - half) * (it - half)) / s2).toFloat() }
        val total = k.sum()
        for (i in k.indices) k[i] /= total
        return k
    }

    private fun convolve(src: FloatArray, dst: FloatArray, w: Int, h: Int, k: FloatArray, horizontal: Boolean) {
        val half = k.size / 2
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            for (i in 0 until len) {
                var s = 0f
                val from = maxOf(0, i - half)
                val to = minOf(len - 1, i + half)
                for (j in from..to) s += k[j - i + half] * src[base + j * step]
                dst[base + i * step] = s
            }
        }
    }

    /**
     * One pass of the cascade: radius [r] and end weight [alpha], so the box with weights
     * 1 out to r and alpha at r + 1 has variance sigma squared over three. Integer boxes
     * alone step the variance by up to 10% at small radii; the end weight makes it exact
     * (Gwosdek, Grewenig, Bruhn and Weickert, "Theoretical foundations of Gaussian
     * convolution by extended box filtering", SSVM 2011).
     */
    internal class ExtendedBox(val r: Int, val alpha: Double)

    internal fun extendedBox(sigma: Float): ExtendedBox {
        val v = sigma.toDouble() * sigma / 3.0
        val r = floor((sqrt(1.0 + 12.0 * v) - 1.0) / 2.0).toInt()
        val s = r * (r + 1.0) * (2 * r + 1) / 3.0
        val alpha = (v * (2 * r + 1) - s) / (2.0 * (r + 1) * (r + 1) - 2.0 * v)
        return ExtendedBox(r, alpha)
    }

    private fun boxes(src: FloatArray, dst: FloatArray, w: Int, h: Int, box: ExtendedBox, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val a = DoubleArray(len)
        val b = DoubleArray(len)
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            for (i in 0 until len) a[i] = src[base + i * step].toDouble()
            box(a, b, len, box)
            box(b, a, len, box)
            box(a, b, len, box)
            for (i in 0 until len) dst[base + i * step] = b[i].toFloat()
        }
    }

    /** Weighted mean over [i - r, i + r] plus alpha at i -+ (r + 1), zero outside, by a running sum. */
    private fun box(src: DoubleArray, dst: DoubleArray, len: Int, e: ExtendedBox) {
        val r = e.r
        val inv = 1.0 / (2 * r + 1 + 2 * e.alpha)
        var sum = 0.0
        for (j in 0..minOf(r, len - 1)) sum += src[j]
        for (i in 0 until len) {
            val lo = i - r - 1
            val hi = i + r + 1
            val ends = (if (lo >= 0) src[lo] else 0.0) + (if (hi < len) src[hi] else 0.0)
            dst[i] = (sum + e.alpha * ends) * inv
            if (hi < len) sum += src[hi]
            if (i - r >= 0) sum -= src[i - r]
        }
    }
}
