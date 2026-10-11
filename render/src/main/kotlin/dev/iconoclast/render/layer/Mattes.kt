package dev.iconoclast.render.layer

import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.mask.Blur

/**
 * Mask adjustments, spec 2.3: luminance to alpha matte. The luminance comes from the
 * source before any gradient map (layer/StackEvaluator): the W3C Lum, 0.3 r + 0.59 g +
 * 0.11 b, of premultiplied texels, so transparent reads as black, or raw noise. Then
 * levels or threshold, invert, and the shared separable Gaussian, mask/Blur.feather: sigma
 * half the radius, taps out to the radius, edges clamped.
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
        return if (radius < 0.5f) out else Blur.feather(out, size, radius)
    }
}
