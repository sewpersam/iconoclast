package dev.iconoclast.render.effect

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.background.Paints
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput

/**
 * Turns an enabled [EffectLayer] into what it paints, one output or two (the bevel's
 * highlight and shadow). The stack evaluator composites them at the layer's opacity.
 * [layers] is the whole stack, for the layer a streak takes its paint from.
 */
internal object EffectPaint {

    fun outputs(layer: EffectLayer, layers: List<Layer>, input: StackInput): List<EffectOutput> = when (val e = layer.effect) {
        is Effect.Streaks -> listOf(Streaks.paint(e, layer.blend, layers, input))
        is Effect.Bevel -> Bevel.paint(e, input)
        is Effect.DropShadow -> listOf(DropShadow.paint(e, layer.blend, input))
        is Effect.InnerShadow -> listOf(InnerShadow.paint(e, layer.blend, input))
        is Effect.OuterGlow -> listOf(Glows.outer(e, layer.blend, input))
        is Effect.InnerGlow -> listOf(Glows.inner(e, layer.blend, input))
        is Effect.Grain -> emptyList() // lands in its own commit
    }

    /** Straight ARGB as one premultiplied colour. */
    fun solid(argb: Int): FloatArray = FloatArray(4).also { Paints.premultiply(argb, it) }

    /**
     * The light's direction on screen, y down, for a light angle in degrees counterclockwise
     * from the right: (cos, -sin).
     */
    fun toward(angleDegrees: Float): Pair<Float, Float> {
        val a = angleDegrees.toDouble() * (StrictMath.PI / 180.0)
        return StrictMath.cos(a).toFloat() to -StrictMath.sin(a).toFloat()
    }

    /** [m], [size] x [size], read at (x - dx, y - dy): moved by (dx, dy) pixels, bilinear, zero outside. */
    fun shifted(m: FloatArray, size: Int, dx: Float, dy: Float): FloatArray {
        if (dx == 0f && dy == 0f) return m
        val out = FloatArray(size * size)
        for (y in 0 until size) for (x in 0 until size) out[y * size + x] = Smear.bilinear(m, size, x - dx, y - dy)
        return out
    }
}
