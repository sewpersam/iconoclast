package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.layer.Mappings
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput

/**
 * Grain, readiness item 10: a thin wrapper over the grain noise source, so grain has one
 * code path. The field is layer/Mappings' box-filtered grain at mapping scale [Effect.Grain.size],
 * cells size/432 of the canvas, each icon its own patch. Grey `0.5 + (n - 0.5) amount`,
 * opaque, source-atop: it lands only on what is already painted, weighted by its alpha,
 * and keeps that alpha, so with the background off a transparent canvas stays
 * transparent. A contrast blend such as overlay makes it grain.
 */
internal object Grain {

    fun paint(e: Effect.Grain, blend: BlendMode, input: StackInput): EffectOutput {
        val n = Mappings.noise(NoiseKind.GRAIN, 1, e.seed, Mapping(scale = e.size), input.seedName, input.size)
        val px = FloatArray(4 * n.size)
        for (i in n.indices) {
            val g = 0.5f + (n[i] - 0.5f) * e.amount
            px[4 * i] = g
            px[4 * i + 1] = g
            px[4 * i + 2] = g
            px[4 * i + 3] = 1f
        }
        return EffectOutput(FloatArray(n.size) { 1f }, null, px, blend, atop = true)
    }
}
