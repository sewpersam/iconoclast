package dev.iconoclast.render.effect

import dev.iconoclast.render.math.sqrt

/**
 * Lights a paint height field, after Hertzmann, "Fast Paint Texture" (NPAR 2002): each
 * stroke writes its height beside its colour, and the painting is bump mapped by that
 * field under one fixed light. Ridges catch the light, grooves and the far side of the
 * paint body fall into shade, and wet paint shows a small highlight on its crests.
 *
 * Heights are in pixels and scale with the canvas, so slopes, and the look, are the same
 * at every size. Plain float arithmetic and one square root per painted pixel, so the
 * bytes are the same on every platform.
 */
internal object Impasto {

    /** Shades premultiplied [prem] where [cov] is painted, by the slope of [height]. */
    fun light(raw: FloatArray, n: Int, cov: FloatArray, prem: FloatArray) {
        // Smoothed first, so the slopes are not one pixel's aliasing.
        val height = tent(raw, cov, n)
        for (j in 0 until n) {
            val up = (if (j > 0) j - 1 else j) * n
            val down = (if (j < n - 1) j + 1 else j) * n
            val row = j * n
            for (i in 0 until n) {
                val idx = row + i
                val a = cov[idx]
                if (a <= 0f) continue
                val l = if (i > 0) i - 1 else i
                val r = if (i < n - 1) i + 1 else i
                // Off the paint, a neighbour reads as this pixel's height: the edge of the stroke
                // is not a slope, or every stroke would get a drawn outline.
                val c = height[idx]
                val hr = if (cov[row + r] < EDGE) c else height[row + r]
                val hl = if (cov[row + l] < EDGE) c else height[row + l]
                val hd = if (cov[down + i] < EDGE) c else height[down + i]
                val hu = if (cov[up + i] < EDGE) c else height[up + i]
                val gx = (hr - hl) * 0.5f
                val gy = (hd - hu) * 0.5f
                // Normal (-gx, -gy, 1), y down.
                val inv = 1f / sqrt(gx * gx + gy * gy + 1f)
                val nl = (-gx * LX - gy * LY + LZ) * inv
                val shade = (1f + DIFFUSE * (nl / LZ - 1f)).coerceIn(MIN_SHADE, MAX_SHADE)
                var nh = (-gx * HX - gy * HY + HZ) * inv
                if (nh < 0f) nh = 0f
                // nh^32 by squaring, less the flat surface's, so only crests shine.
                var p = nh * nh
                p *= p; p *= p; p *= p; p *= p
                val spec = (SPECULAR * (p - FLAT_SPEC)).coerceAtLeast(0f) * a
                val o = 4 * idx
                for (ch in 0..2) prem[o + ch] = minOf(a, prem[o + ch] * shade + spec)
            }
        }
    }

    /**
     * A 1-2-1 tent across and down over the paint only: neighbours off the paint are left
     * out and the weights renormalised, so the stroke's edge does not sag into a slope.
     */
    private fun tent(src: FloatArray, cov: FloatArray, n: Int): FloatArray {
        val tmp = FloatArray(n * n)
        for (j in 0 until n) {
            val row = j * n
            for (i in 0 until n) {
                var sum = 2f * src[row + i]
                var w = 2f
                if (i > 0 && cov[row + i - 1] >= EDGE) { sum += src[row + i - 1]; w += 1f }
                if (i < n - 1 && cov[row + i + 1] >= EDGE) { sum += src[row + i + 1]; w += 1f }
                tmp[row + i] = sum / w
            }
        }
        val out = FloatArray(n * n)
        for (j in 0 until n) {
            val row = j * n
            for (i in 0 until n) {
                var sum = 2f * tmp[row + i]
                var w = 2f
                if (j > 0 && cov[row - n + i] >= EDGE) { sum += tmp[row - n + i]; w += 1f }
                if (j < n - 1 && cov[row + n + i] >= EDGE) { sum += tmp[row + n + i]; w += 1f }
                out[row + i] = sum / w
            }
        }
        return out
    }

    /** The light, a unit vector from the upper left at about 45 degrees altitude, y down. */
    private const val LX = -0.4243f
    private const val LY = -0.5657f
    private const val LZ = 0.7071f

    /** Blinn's half vector between the light and a viewer straight above. */
    private const val HX = -0.2297f
    private const val HY = -0.3062f
    private const val HZ = 0.9238f

    /** HZ^32: the highlight a flat surface already has. */
    private const val FLAT_SPEC = 0.0794f

    /** How strongly slope changes the colour, and its bounds. */
    private const val DIFFUSE = 0.85f
    private const val MIN_SHADE = 0.5f
    private const val MAX_SHADE = 1.1f

    /** Neighbours with less coverage than this are off the paint. */
    private const val EDGE = 0.5f

    /** The wet highlight's strength. */
    private const val SPECULAR = 0.35f
}
