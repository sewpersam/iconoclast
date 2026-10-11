package dev.iconoclast.render.layer

import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.math.ceil

/**
 * Mask adjustments, spec 2.3: luminance to alpha matte. The luminance comes from the
 * source before any gradient map (layer/StackEvaluator): the W3C Lum, 0.3 r + 0.59 g +
 * 0.11 b, of premultiplied texels, so transparent reads as black, or raw noise. Then
 * levels or threshold, invert, and a separable Gaussian feather: sigma half the radius,
 * taps out to the radius, edges clamped, weights from StrictMath.exp normalised to 1.
 */
internal object Mattes {

    fun apply(matte: Matte, lum: FloatArray, size: Int): FloatArray {
        val out = FloatArray(lum.size)
        val span = matte.white - matte.black
        for (i in lum.indices) {
            var m = when (matte.mode) {
                MatteMode.LEVELS -> ((lum[i] - matte.black) / span).coerceIn(0f, 1f)
                MatteMode.THRESHOLD -> if (lum[i] >= matte.threshold) 1f else 0f
            }
            if (matte.invert) m = 1f - m
            out[i] = m
        }
        val radius = matte.feather * size
        return if (radius < 0.5f) out else feather(out, size, radius)
    }

    private fun feather(m: FloatArray, size: Int, radius: Float): FloatArray {
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
}
