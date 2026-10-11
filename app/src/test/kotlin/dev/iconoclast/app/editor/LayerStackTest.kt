package dev.iconoclast.app.editor

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LayerStackTest {
    private val base = Theme()
    private fun ids(t: Theme) = t.layers.map { it.id }

    @Test
    fun insertMoveRemove() {
        val l = LayerStack.newLayer(Paint.Solid(-1))
        val t = LayerStack.insertAbove(base, l, Layer.EFFECTS_ID)
        assertEquals(listOf("background", "effects", l.id, "icon-fill", "icon-lines"), ids(t))
        assertEquals(listOf("background", "effects", l.id, "icon-fill", "icon-lines", "x"),
            ids(LayerStack.insertAbove(t, l.copy(id = "x"), null)))
        assertEquals(listOf("background", "effects", "icon-fill", l.id, "icon-lines"), ids(LayerStack.move(t, l.id, up = true)))
        assertEquals(listOf("background", l.id, "effects", "icon-fill", "icon-lines"), ids(LayerStack.move(t, l.id, up = false)))
        assertSame(base, LayerStack.move(base, "icon-lines", up = true))
        assertSame(base, LayerStack.move(base, "background", up = false))
        assertEquals(ids(base), ids(LayerStack.remove(t, l.id)))
        assertSame(base, LayerStack.remove(base, Layer.EFFECTS_ID))
    }

    @Test
    fun backgroundTextureSitsAboveTheBackgroundPaint() {
        val t = LayerStack.withBackgroundTexture(base, GoldenSet.TEXTURE)
        assertEquals(listOf("background", LayerStack.BACKGROUND_TEXTURE_ID, "effects", "icon-fill", "icon-lines"), ids(t))
        val layer = LayerStack.backgroundTexture(t)!!
        assertEquals(ClipTarget.BACKGROUND, layer.clip)
        assertEquals(2f, layer.mapping.scale, "room for a patch per icon")
        assertEquals(2f, LayerStack.newLayer(Paint.Texture(GoldenSet.TEXTURE)).mapping.scale)
        assertEquals(1f, LayerStack.newLayer(Paint.Solid(-1)).mapping.scale)
        val tuned = LayerStack.replace(t, layer.copy(blend = BlendMode.MULTIPLY))
        val again = LayerStack.withBackgroundTexture(tuned, "a".repeat(64))
        assertEquals(BlendMode.MULTIPLY, LayerStack.backgroundTexture(again)!!.blend)
        assertEquals("a".repeat(64), (LayerStack.backgroundTexture(again)!!.source as Paint.Texture).sha256)
        assertEquals(ids(t), ids(again))
    }

    @Test
    fun labelsNumberLayersOfOneKind() {
        val a = PaintLayer("a", Paint.Texture(GoldenSet.TEXTURE))
        val b = PaintLayer("b", Paint.Texture(GoldenSet.TEXTURE))
        val t = base.copy(layers = base.layers + a + b)
        assertEquals("Texture 1", LayerStack.label(t, a))
        assertEquals("Texture 2", LayerStack.label(t, b))
        assertEquals("Icon fill", LayerStack.label(t, t.layers[2]))
    }

    @Test
    fun everyBlendModeHasItsPhotoshopName() {
        assertEquals(BlendMode.entries.toList(), BLEND_NAMES.keys.toList())
        assertEquals("Add", BLEND_NAMES[BlendMode.ADD])
        assertEquals("Normal", BLEND_NAMES[BlendMode.NORMAL])
        assertEquals(ClipTarget.entries.toSet(), CLIP_NAMES.keys)
        assertEquals("Effects", clipName(ClipTarget.EFFECTS))
    }

    @Test
    fun switchingToATextureStartsAtTheNewTextureMapping() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val solid = LayerStack.newLayer(Paint.Solid(0))
        assertEquals(LayerStack.NEW_TEXTURE_MAPPING, LayerStack.withTexture(solid, a).mapping)
        val tex = LayerStack.withTexture(solid, a).copy(mapping = Mapping(MappingMode.TILE, scale = 3f))
        val swapped = LayerStack.withTexture(tex, b)
        assertEquals(Paint.Texture(b) to tex.mapping, swapped.source to swapped.mapping)
        val bg = LayerStack.backgroundTexture(LayerStack.withBackgroundTexture(Theme(), a))!!
        assertEquals(bg.mapping, defaultsOf(bg).mapping, "the reset target is where it started")
    }

    @Test
    fun clippingToTheWholeCanvasDropsTheInverse() {
        val l = LayerStack.newLayer(Paint.Solid(0)).copy(clipInverse = true)
        assertEquals(ClipTarget.CANVAS to false, LayerStack.withClip(l, ClipTarget.CANVAS).let { it.clip to it.clipInverse })
        assertEquals(ClipTarget.BACKGROUND to true, LayerStack.withClip(l, ClipTarget.BACKGROUND).let { it.clip to it.clipInverse })
    }

    /** Task 9's placement: effects outside the icon go below it, effects on it on top. */
    @Test
    fun newEffectsGoWhereTheyShow() {
        val streaks = LayerStack.newEffect(base, Effect.Streaks())
        val glow = LayerStack.newEffect(base, Effect.OuterGlow())
        val bevel = LayerStack.newEffect(base, Effect.Bevel())
        val grain = LayerStack.newEffect(base, Effect.Grain())
        var t = LayerStack.insertEffect(base, streaks)
        t = LayerStack.insertEffect(t, glow)
        t = LayerStack.insertEffect(t, bevel)
        t = LayerStack.insertEffect(t, grain)
        assertEquals(listOf("background", "effects", streaks.id, glow.id, "icon-fill", "icon-lines", bevel.id, grain.id), ids(t))
        assertEquals(false, LayerStack.aboveIcon(t, streaks))
        assertEquals(true, LayerStack.aboveIcon(t, bevel))
        assertEquals(BlendMode.OVERLAY, grain.blend)
        assertEquals("Streaks", LayerStack.label(t, streaks))
        assertEquals("Bevel and emboss", LayerStack.label(t, bevel))
        val two = LayerStack.insertEffect(t, LayerStack.newEffect(t, Effect.Grain()))
        assertEquals("Grain 1", LayerStack.label(two, grain))
        assertEquals(ids(t) - grain.id, ids(LayerStack.remove(t, grain.id)))
    }

    @Test
    fun newStreaksTakeTheFirstTextureOrNoiseLayer() {
        assertEquals(null, (LayerStack.newEffect(base, Effect.Streaks()).effect as Effect.Streaks).paintLayer)
        val noise = LayerStack.newLayer(Paint.Noise())
        val t = LayerStack.insertAbove(base, noise, null)
        assertEquals(listOf(noise), LayerStack.streakSources(t))
        assertEquals(noise.id, (LayerStack.newEffect(t, Effect.Streaks()).effect as Effect.Streaks).paintLayer)
    }
}
