package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput

/**
 * Inner shadow: what lies outside the icon, blurred and moved away from the light, read
 * inside it, `icon (1 - moved blur)`. It darkens the inner edges that face the light, as
 * if the icon were cut into the surface.
 */
internal object InnerShadow {

    fun paint(e: Effect.InnerShadow, blend: BlendMode, input: StackInput): EffectOutput {
        val size = input.size
        val icon = EffectSource.icon(input)
        val blurred = EffectSource.blurred(input, e.blur * size / 2f)
        val (lx, ly) = EffectPaint.toward(e.angle)
        val d = e.distance * size
        val moved = EffectPaint.shifted(blurred, size, -lx * d, -ly * d)
        val cov = FloatArray(size * size) { icon[it] * (1f - moved[it]) }
        return EffectOutput(cov, EffectPaint.solid(e.color), null, blend)
    }
}
