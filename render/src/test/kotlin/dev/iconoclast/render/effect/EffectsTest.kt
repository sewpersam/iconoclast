package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The effects through the renderer: determinism, switches, the EFFECTS target, links, read-only masks. */
class EffectsTest {
    private val bundle = IconBundle(ByteBuffer.wrap(javaClass.getResourceAsStream("/golden/samples.bin")!!.readBytes()))
    private val name = "camera"
    private val icon = bundle.icon(GoldenSet.SAMPLES.indexOf(name))

    private val looks: Map<String, Theme> = GoldenSet.EFFECTS + ("effects_jade" to GoldenSet.JADE_LIKE)

    /** Determinism, per effect: fresh renderers, a warm cache and two sizes give the same bytes again. */
    @Test
    fun everyEffectIsDeterministic() {
        for ((look, theme) in looks) for (size in listOf(128, 432)) {
            val a = IconRenderer().renderPng(name, icon, theme, size)
            val b = IconRenderer().renderPng(name, icon, theme, size)
            assertContentEquals(a, b, "$look at $size, fresh renderers")
            val warm = IconRenderer()
            warm.renderPng(name, icon, theme, size)
            assertContentEquals(a, warm.renderPng(name, icon, theme, size), "$look at $size, warm cache")
        }
    }

    /** Each effect paints something, and switched off or at opacity 0 paints nothing. */
    @Test
    fun switchesAndOpacity() {
        for ((look, theme) in GoldenSet.EFFECTS) {
            val bare = theme.copy(layers = theme.layers.filterNot { it is EffectLayer })
            val off = theme.copy(layers = theme.layers.map { if (it is EffectLayer) it.copy(enabled = false) else it })
            val clear = theme.copy(layers = theme.layers.map { if (it is EffectLayer) it.copy(opacity = 0f) else it })
            val r = IconRenderer()
            val base = r.render(name, icon, bare, 128).pixels
            assertFalse(base.contentEquals(r.render(name, icon, theme, 128).pixels), "$look paints")
            assertContentEquals(base, r.render(name, icon, off, 128).pixels, "$look off")
            assertContentEquals(base, r.render(name, icon, clear, 128).pixels, "$look at opacity 0")
        }
    }

    /** Effects read cached masks and never write them. */
    @Test
    fun cachedMasksStayUntouched() {
        val r = IconRenderer()
        val theme = GoldenSet.JADE_LIKE
        val input = r.stackInput(name, icon, theme, 432)
        val before = EffectSource.icon(input).copyOf()
        val sd = EffectSource.signedDistance(input).copyOf()
        for (t in looks.values) r.render(name, icon, t.copy(shape = theme.shape, fill = theme.fill), 432)
        assertContentEquals(before, EffectSource.icon(input))
        assertContentEquals(sd, EffectSource.signedDistance(input))
    }

    /**
     * A layer clipped to EFFECTS above a drop shadow paints exactly where the shadow fell:
     * over a shadow in clear colour, which adds its coverage but no paint, the red layer's
     * alpha is the white shadow's alpha.
     */
    @Test
    fun effectsTargetFollowsTheShadow() {
        fun stack(color: Int, vararg extra: Layer) = Theme(fill = GoldenSet.JADE_LIKE.fill,
            background = GoldenSet.JADE_LIKE.background.copy(enabled = false), layers = listOf(
                dev.iconoclast.render.EffectsLayer(),
                EffectLayer("shadow", Effect.DropShadow(distance = 0.02f, blur = 0.02f, color = color)),
            ) + extra)
        val r = IconRenderer()
        val shadow = r.render(name, icon, stack(0xFFFFFFFF.toInt()), 128).pixels
        val red = r.render(name, icon, stack(0x00FFFFFF, PaintLayer("on-fx", Paint.Solid(0xFFFF0000.toInt()), clip = ClipTarget.EFFECTS)), 128).pixels
        var lit = 0
        for (i in 0 until 128 * 128) {
            val a = shadow[4 * i + 3].toInt() and 0xFF
            assertEquals(a, red[4 * i + 3].toInt() and 0xFF, "alpha at $i")
            assertEquals(a, red[4 * i].toInt() and 0xFF, "red at $i")
            if (a > 0) lit++
        }
        assertTrue(lit > 500, "the shadow covers $lit px")
    }

    /** A streak linked to a solid layer paints what the same colour unlinked paints; a dangling link falls back. */
    @Test
    fun streaksTakeTheLinkedLayersPaint() {
        val red = 0xFFE04030.toInt()
        val base = GoldenSet.EFFECTS.getValue("effect_streaks").let { t ->
            t.copy(layers = t.layers.map { if (it.id == Layer.ICON_FILL_ID) (it as PaintLayer).copy(source = Paint.Solid(red)) else it })
        }
        fun with(e: Effect.Streaks) = base.copy(layers = base.layers.map { if (it is EffectLayer) it.copy(effect = e) else it })
        val streak = (base.layers.first { it is EffectLayer } as EffectLayer).effect as Effect.Streaks
        val r = IconRenderer()
        val linked = r.render(name, icon, with(streak.copy(paintLayer = Layer.ICON_FILL_ID)), 128).pixels
        val coloured = r.render(name, icon, with(streak.copy(paintLayer = null, color = red)), 128).pixels
        val dangling = r.render(name, icon, with(streak.copy(paintLayer = "gone", color = red)), 128).pixels
        assertContentEquals(coloured, linked)
        assertContentEquals(coloured, dangling)
    }

    /** The drop shadow is knocked out under the icon: a fully covered pixel looks as without it. */
    @Test
    fun dropShadowStaysOutsideTheIcon() {
        val theme = GoldenSet.EFFECTS.getValue("effect_drop_shadow")
        val bare = theme.copy(layers = theme.layers.filterNot { it is EffectLayer })
        val r = IconRenderer()
        val mask = EffectSource.icon(r.stackInput(name, icon, theme, 128))
        val a = r.render(name, icon, theme, 128).pixels
        val b = r.render(name, icon, bare, 128).pixels
        var inside = 0
        for (i in mask.indices) if (mask[i] == 1f) {
            inside++
            for (k in 0..3) assertEquals(b[4 * i + k], a[4 * i + k], "pixel $i")
        }
        assertTrue(inside > 1000)
        assertEquals(BlendMode.MULTIPLY, (theme.layers.first { it is EffectLayer } as EffectLayer).blend)
    }
}
