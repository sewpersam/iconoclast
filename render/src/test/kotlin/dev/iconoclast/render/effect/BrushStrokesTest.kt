package dev.iconoclast.render.effect

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.Theme
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.mask.label4
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Brush strokes: ranges, what seeds them, where they land, and that they agree across sizes. */
class BrushStrokesTest {
    private val bundle = IconBundle(ByteBuffer.wrap(javaClass.getResourceAsStream("/golden/samples.bin")!!.readBytes()))
    private fun icon(name: String) = bundle.icon(GoldenSet.SAMPLES.indexOf(name))

    /** White strokes on a transparent canvas, no background, invert fill: the effect alone shows where the icon is not. */
    private fun theme(e: Effect.BrushStrokes) = Theme(
        fill = FillParams(mode = FillMode.INVERT),
        background = BackgroundParams(enabled = false),
        layers = Theme.baseLayers(Paint.Solid(0), body = 0, line = 0),
    ).withEffect(EffectLayer("strokes", e))

    private val strokes = Effect.BrushStrokes(count = 80, length = 0.2f, width = 0.03f, color = 0xFFFFFFFF.toInt())

    /** Alpha per pixel, 0 to 1. */
    private fun alpha(name: String, t: Theme, size: Int, seedName: String = name): FloatArray {
        val px = IconRenderer().render(name, icon(name), seedName, t, size).pixels
        return FloatArray(size * size) { (px[4 * it + 3].toInt() and 0xFF) / 255f }
    }

    @Test
    fun rangesAreChecked() {
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(count = 0) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(count = 401) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(length = 0.6f) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(width = 0.001f) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(bend = 1.5f) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(angle = 361f) }
        assertFailsWith<IllegalArgumentException> { Effect.BrushStrokes(softness = -0.1f) }
    }

    /** The per-icon offset and the seed pick the strokes; the same pair gives the same bytes. */
    @Test
    fun seededByTheIconAndTheSeed() {
        val t = theme(strokes)
        val a = alpha("camera", t, 128)
        assertTrue(a.any { it > 0.5f }, "strokes show")
        assertTrue(a.contentEquals(alpha("camera", t, 128)))
        assertFalse(a.contentEquals(alpha("camera", t, 128, seedName = "signal")), "another slot, other strokes")
        assertFalse(a.contentEquals(alpha("camera", theme(strokes.copy(seed = 1)), 128)), "another seed, other strokes")
        val pinned = t.copy(textureOffset = TextureOffset(0.25f, 0.5f))
        assertFalse(a.contentEquals(alpha("camera", pinned, 128)), "a texture offset override moves them too")
    }

    /** Strokes start on the icon and travel out: none far from it, and none in its enclosed counters. */
    @Test
    fun strokesLeaveTheIconAndKeepOutOfItsHoles() {
        for (name in listOf("termux", "fdroid", "camera", "spotify")) {
            val r = IconRenderer()
            val input = r.stackInput(name, icon(name), name, theme(strokes), 432)
            val out = BrushStrokes.paint(strokes, BlendMode.NORMAL, theme(strokes).layers, input)
            val m = EffectSource.icon(input)
            val sd = EffectSource.signedDistance(input)
            val bg = BooleanArray(m.size) { m[it] < 0.5f }
            val l = label4(bg, 432, 432)
            val open = BooleanArray(l.count + 1)
            for (k in 0 until 432) for (i in intArrayOf(k, 431 * 432 + k, k * 432, k * 432 + 431)) open[l.labels[i]] = true
            var shown = 0
            for (i in m.indices) {
                if (bg[i] && !open[l.labels[i]]) assertEquals(0f, out.coverage[i], "$name: a stroke in a hole at $i")
                // The longest stroke reaches 1.3 lengths, its blur a little more.
                if (-sd[i] > 1.6f * strokes.length * 432) assertEquals(0f, out.coverage[i], "$name: a stroke too far out at $i")
                if (bg[i] && out.coverage[i] > 0.25f) shown++
            }
            assertTrue(shown > 500, "$name: strokes show outside the icon, $shown px")
        }
    }

    /**
     * A 144 px preview shows the strokes the 432 px pack does: the pack, box-filtered to 144,
     * is far closer to the preview than another seed at 144 is.
     */
    @Test
    fun previewAndPackAgree() {
        for (name in listOf("camera", "google_chrome", "weather")) {
            val small = alpha(name, theme(strokes), 144)
            val big = alpha(name, theme(strokes), 432)
            val down = FloatArray(144 * 144) { k ->
                val x = k % 144
                val y = k / 144
                var s = 0f
                for (j in 0 until 3) for (i in 0 until 3) s += big[(3 * y + j) * 432 + 3 * x + i]
                s / 9f
            }
            val other = alpha(name, theme(strokes.copy(seed = 7)), 144)
            val same = down.indices.sumOf { kotlin.math.abs(down[it] - small[it]).toDouble() }
            val differ = other.indices.sumOf { kotlin.math.abs(other[it] - small[it]).toDouble() }
            assertTrue(same < 0.5 * differ, "$name: preview against pack $same, against another seed $differ")
        }
    }

    /** Coverage and colours stay in range, so the compositor never clamps a stroke; colours never exceed their alpha. */
    @Test
    fun outputStaysInRange() {
        val e = strokes.copy(blur = 1f, softness = 1f, randomness = 1f, count = 400)
        val input = IconRenderer().stackInput("camera", icon("camera"), "camera", theme(e), 216)
        val out = BrushStrokes.paint(e, BlendMode.NORMAL, theme(e).layers, input)
        assertTrue(out.coverage.all { it in 0f..1f })
        val c = out.colors!!
        for (i in out.coverage.indices) for (k in 0..2) assertTrue(c[4 * i + k] <= c[4 * i + 3] + 1e-6f && c[4 * i + k] >= 0f)
    }
}
