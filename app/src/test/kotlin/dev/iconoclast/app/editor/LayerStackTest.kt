package dev.iconoclast.app.editor

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.Layer
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
    }
}
