package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput

/**
 * Outer and inner glow, one file since they mirror each other. Outer: the icon mask
 * blurred (sigma half the size, cached), divided by `1 - 0.99 spread` and clamped, outside
 * the icon only. Inner: the same on what lies outside, `1 - blur`, read inside the icon,
 * a glow from the edges inward.
 */
internal object Glows {

    fun outer(e: Effect.OuterGlow, blend: BlendMode, input: StackInput): EffectOutput {
        val icon = EffectSource.icon(input)
        val b = EffectSource.blurred(input, e.size * input.size / 2f)
        val k = 1f / (1f - 0.99f * e.spread)
        val cov = FloatArray(b.size) { minOf(1f, b[it] * k) * (1f - icon[it]) }
        return EffectOutput(cov, EffectPaint.solid(e.color), null, blend)
    }

    fun inner(e: Effect.InnerGlow, blend: BlendMode, input: StackInput): EffectOutput {
        val icon = EffectSource.icon(input)
        val b = EffectSource.blurred(input, e.size * input.size / 2f)
        val k = 1f / (1f - 0.99f * e.spread)
        val cov = FloatArray(b.size) { minOf(1f, (1f - b[it]) * k) * icon[it] }
        return EffectOutput(cov, EffectPaint.solid(e.color), null, blend)
    }
}
