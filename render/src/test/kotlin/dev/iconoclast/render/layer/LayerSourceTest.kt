package dev.iconoclast.render.layer

import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Matte
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.effect.EffectTestIcons
import dev.iconoclast.render.texture.TextureStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * Readiness item 6: a layer's source, evaluated alone, is exactly what the layer paints on
 * the whole canvas at full opacity, per-icon offset included, whatever its clip and matte.
 */
class LayerSourceTest {
    private val size = 64

    @Test
    fun sourceIsTheUnclippedLayer() {
        val store = TextureStore()
        val px = ByteArray(16 * 16 * 4) { if (it % 4 == 3) -1 else ((it * 37) % 256).toByte() }
        val tex = store.put(TextureStore.encode(16, 16, px))
        val stops = listOf(ColorStop(0f, 0xFF102030.toInt()), ColorStop(1f, 0xFFE0F0A0.toInt()))
        val sources = listOf(
            Paint.Solid(0x80FF8040.toInt()),
            Paint.LinearGradient(0f, 0f, 1f, 1f, stops),
            Paint.Texture(tex, stops),
            Paint.Noise(NoiseKind.FBM, 3, 5, stops),
        )
        val r = IconRenderer(textures = store)
        for (src in sources) for (name in listOf("a", "b")) {
            val layer = PaintLayer("x", src, clip = ClipTarget.ICON_FILL, opacity = 0.3f, matte = Matte(),
                mapping = Mapping(MappingMode.TILE, scale = 2f, rotation = 30f))
            val input = r.stackInput(name, EffectTestIcons.square, Theme(), size)
            val colors = StackEvaluator.sourceColors(layer, input)
            val painted = FloatArray(4 * size * size)
            StackEvaluator.paint(painted, listOf(layer.copy(clip = ClipTarget.CANVAS, opacity = 1f, matte = null)), input)
            assertContentEquals(painted, colors, "$src $name")
        }
        // The per-icon offset reaches it: two icons get two patches.
        val noise = PaintLayer("n", sources[3])
        assertFalse(StackEvaluator.sourceColors(noise, r.stackInput("a", EffectTestIcons.square, Theme(), size))
            .contentEquals(StackEvaluator.sourceColors(noise, r.stackInput("b", EffectTestIcons.square, Theme(), size))))
    }
}
