package dev.iconoclast.render.layer

import dev.iconoclast.render.Adjust

/**
 * An image fill's colour adjustments, schema 9, in place on premultiplied RGBA. Each pixel
 * is unpremultiplied, adjusted in this order, clamped to 0..1 and premultiplied again; its
 * alpha never changes. Plain float arithmetic, so the result is the same everywhere.
 *
 * - Brightness b: up, `c + (1 - c) 0.6 b`, toward white; down, `c (1 + 0.6 b)`, toward black.
 * - Contrast k about mid-grey: `(c - 0.5) f + 0.5`, f = 1 + k below 0 (flat grey at -1),
 *   1 / (1 - 0.8 k) above (five times at 1).
 * - Saturation s: each channel's distance from the luminance `0.3 r + 0.59 g + 0.11 b`
 *   times 1 + s; grey at -1, twice as strong at 1.
 * - Warmth w: red up and blue down by 0.12 w.
 */
internal object Adjustments {

    fun apply(a: Adjust, px: FloatArray) {
        val bright = a.brightness * BRIGHT
        val f = if (a.contrast <= 0f) 1f + a.contrast else 1f / (1f - CONTRAST * a.contrast)
        val sat = 1f + a.saturation
        val warm = a.warmth * WARM
        for (o in px.indices step 4) {
            val alpha = px[o + 3]
            if (alpha <= 0f) continue
            val inv = 1f / alpha
            var r = px[o] * inv
            var g = px[o + 1] * inv
            var b = px[o + 2] * inv
            if (bright > 0f) {
                r += (1f - r) * bright
                g += (1f - g) * bright
                b += (1f - b) * bright
            } else if (bright < 0f) {
                r *= 1f + bright
                g *= 1f + bright
                b *= 1f + bright
            }
            if (f != 1f) {
                r = (r - 0.5f) * f + 0.5f
                g = (g - 0.5f) * f + 0.5f
                b = (b - 0.5f) * f + 0.5f
            }
            if (sat != 1f) {
                val l = 0.3f * r + 0.59f * g + 0.11f * b
                r = l + (r - l) * sat
                g = l + (g - l) * sat
                b = l + (b - l) * sat
            }
            if (warm != 0f) {
                r += warm
                b -= warm
            }
            px[o] = r.coerceIn(0f, 1f) * alpha
            px[o + 1] = g.coerceIn(0f, 1f) * alpha
            px[o + 2] = b.coerceIn(0f, 1f) * alpha
        }
    }

    private const val BRIGHT = 0.6f
    private const val CONTRAST = 0.8f
    private const val WARM = 0.12f
}
