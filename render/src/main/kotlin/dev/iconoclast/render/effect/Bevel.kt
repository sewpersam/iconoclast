package dev.iconoclast.render.effect

import dev.iconoclast.render.BevelStyle
import dev.iconoclast.render.Effect
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput
import dev.iconoclast.render.mask.Blur
import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.sqrt

/**
 * Bevel and emboss from the signed sub-pixel distance field of the icon mask, cached
 * (mask/SignedDistance, readiness item 7). The surface rises over the bevel's width w:
 * with s the signed distance, t = clamp(s / w) for an inner bevel or clamp(s / w + 1/2)
 * for emboss, and height `depth w (1 - (1 - t)^2)`, a rounded rise that meets the
 * plateau flat. Softness blurs the height with sigma `softness w / 2`. The normal comes
 * from central differences; the light from the light angle at 30 degrees altitude.
 * Shade `n . L - Lz` is 0 on flat ground; above it the highlight, `shade / (1 - Lz)`,
 * below it the shadow, `-shade / Lz`, each clamped to 1 and times its colour's alpha.
 * An inner bevel is clipped to the icon mask; emboss shades the ground outside too.
 */
internal object Bevel {

    fun paint(e: Effect.Bevel, input: StackInput): List<EffectOutput> {
        val n = input.size
        val w = e.size * n
        // The surface changes only within w of the edge (inner) or w / 2 (emboss); the
        // central differences and softness reach a little farther.
        val sd = EffectSource.signedDistance(input, band = ceil(w * (1f + e.softness) + 3f))
        val rise = e.depth * w
        val inner = e.style == BevelStyle.INNER
        var h = FloatArray(n * n) {
            val t = (if (inner) sd[it] / w else sd[it] / w + 0.5f).coerceIn(0f, 1f)
            val u = 1f - t
            rise * (1f - u * u)
        }
        if (e.softness > 0f) h = Blur.gaussian(h, n, n, e.softness * w / 2f)
        val (lx, ly) = EffectPaint.toward(e.lightAngle)
        val lz = ALTITUDE_SIN
        val dx = lx * ALTITUDE_COS
        val dy = ly * ALTITUDE_COS
        val icon = if (inner) EffectSource.icon(input) else null
        val hi = FloatArray(n * n)
        val sh = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val hx = (h[y * n + minOf(x + 1, n - 1)] - h[y * n + maxOf(x - 1, 0)]) / 2f
            val hy = (h[minOf(y + 1, n - 1) * n + x] - h[maxOf(y - 1, 0) * n + x]) / 2f
            if (hx == 0f && hy == 0f) continue
            val len = sqrt(hx * hx + hy * hy + 1f)
            val shade = (-hx * dx - hy * dy + lz) / len - lz
            val clip = icon?.get(i) ?: 1f
            if (shade > 0f) hi[i] = minOf(1f, shade / (1f - lz)) * clip
            else sh[i] = minOf(1f, -shade / lz) * clip
        }
        return listOf(
            EffectOutput(hi, EffectPaint.solid(e.highlight), null, e.highlightBlend),
            EffectOutput(sh, EffectPaint.solid(e.shadow), null, e.shadowBlend),
        )
    }


    /** sin and cos of the light's 30 degree altitude. */
    private const val ALTITUDE_SIN = 0.5f
    private const val ALTITUDE_COS = 0.8660254f
}
