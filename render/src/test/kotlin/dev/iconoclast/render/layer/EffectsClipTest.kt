package dev.iconoclast.render.layer

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.fill.GlyphMasks
import dev.iconoclast.render.texture.TextureStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Readiness item 2: the EFFECTS clip target is the union of the effect outputs below. */
class EffectsClipTest {
    private val size = 4
    private val n = size * size
    private val input = StackInput("x", size, GlyphMasks(null, null), icon = { null }, shape = null, border = null, borderColor = null,
        textures = TextureStore())

    @Test
    fun unionOfEffectOutputsTimesOpacity() {
        val pass = StackEvaluator.Pass(n)
        val buf = FloatArray(4 * n)
        val white = floatArrayOf(1f, 1f, 1f, 1f)
        // Left half at 0.5, then the top half at 0.5 under opacity 0.5.
        StackEvaluator.paintEffect(buf, StackEvaluator.EffectOutput(FloatArray(n) { if (it % size < 2) 0.5f else 0f }, white, null, BlendMode.NORMAL), 1f, pass)
        StackEvaluator.paintEffect(buf, StackEvaluator.EffectOutput(FloatArray(n) { if (it / size < 2) 0.5f else 0f }, white, null, BlendMode.NORMAL), 0.5f, pass)
        val e = pass.effects!!
        assertEquals(0.5f + 0.25f - 0.125f, e[0], "both")
        assertEquals(0.5f, e[size * 3], "left only")
        assertEquals(0.25f, e[3], "top only, at half opacity")
        assertEquals(0f, e[n - 1], "neither")
        assertEquals(0.625f, buf[3], "the effects painted what they cover")

        // A layer clipped to EFFECTS paints through the union; its inverse through the rest.
        val red = 0xFFFF0000.toInt()
        val over = FloatArray(4 * n)
        StackEvaluator.paint(over, listOf(PaintLayer("t", Paint.Solid(red), clip = ClipTarget.EFFECTS)), input, pass)
        for (i in 0 until n) assertEquals(e[i], over[4 * i + 3], "pixel $i")
        val inverse = FloatArray(4 * n)
        StackEvaluator.paint(inverse, listOf(PaintLayer("t", Paint.Solid(red), clip = ClipTarget.EFFECTS, clipInverse = true)), input, pass)
        for (i in 0 until n) assertEquals(1f - e[i], inverse[4 * i + 3], "pixel $i")
    }

    @Test
    fun emptyBeforeAnyEffect() {
        val buf = FloatArray(4 * n)
        StackEvaluator.paint(buf, listOf(PaintLayer("t", Paint.Solid(-1), clip = ClipTarget.EFFECTS)), input)
        assertEquals(0f, buf.max())
    }
}
