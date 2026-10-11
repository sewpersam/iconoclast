package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput

/**
 * Drop shadow: the icon mask blurred (sigma half the blur radius, cached), moved away from
 * the light by the distance, and knocked out under the icon, `shadow (1 - icon)`, so it
 * shows only beside it wherever it sits in the stack.
 */
internal object DropShadow {

    fun paint(e: Effect.DropShadow, blend: BlendMode, input: StackInput): EffectOutput {
        val size = input.size
        val icon = EffectSource.icon(input)
        val blurred = EffectSource.blurred(input, e.blur * size / 2f)
        val (lx, ly) = EffectPaint.toward(e.angle)
        val d = e.distance * size
        val shadow = EffectPaint.shifted(blurred, size, -lx * d, -ly * d)
        val cov = FloatArray(size * size) { shadow[it] * (1f - icon[it]) }
        return EffectOutput(cov, EffectPaint.solid(e.color), null, blend)
    }
}
