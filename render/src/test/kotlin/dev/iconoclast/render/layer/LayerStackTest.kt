package dev.iconoclast.render.layer

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.RgbaImage
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb
import dev.iconoclast.render.texture.TextureStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stack evaluator through the renderer: clip targets, inverses, opacity, mattes, textures. */
class LayerStackTest {

    /** A closed square, 12 to 36 on the 48 grid. */
    private val icon = Icon(listOf(
        Primitive(Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.LINE, Verb.LINE, Verb.LINE, Verb.CLOSE),
            floatArrayOf(12f, 12f, 36f, 12f, 36f, 36f, 12f, 36f)),
    ))
    private val size = 96
    private val red = 0xFFFF0000.toInt()
    private val clear = BackgroundParams(enabled = false)

    private fun render(theme: Theme, name: String = "square", r: IconRenderer = IconRenderer()) = r.render(name, icon, theme, size)

    private fun with(vararg extra: PaintLayer, base: Theme = Theme(background = clear)) = base.copy(layers = base.layers + extra)

    private fun alpha(img: RgbaImage, i: Int) = img.pixels[4 * i + 3].toInt() and 0xFF

    @Test
    fun effectsTargetIsEmptyUntilM6() {
        val base = Theme(fill = FillParams(mode = FillMode.INVERT))
        assertContentEquals(render(base).pixels, render(with(PaintLayer("e", Paint.Solid(red), clip = ClipTarget.EFFECTS), base = base)).pixels)
        // Its inverse is the whole canvas.
        val all = render(with(PaintLayer("e", Paint.Solid(red), clip = ClipTarget.EFFECTS, clipInverse = true)))
        assertTrue((0 until size * size).all { alpha(all, it) == 255 })
    }

    @Test
    fun inverseIconClipsAwayTheGlyph() {
        val base = Theme(background = clear, layers = Theme.baseLayers(Paint.Solid(0)))
        val glyph = render(base)
        val outside = render(base.copy(layers = listOf(base.layers[1], PaintLayer("x", Paint.Solid(red), clip = ClipTarget.ICON, clipInverse = true))))
        for (i in 0 until size * size) assertEquals(255.0, (alpha(glyph, i) + alpha(outside, i)).toDouble(), 1.0, "pixel $i")
    }

    @Test
    fun iconIsBodyUnionLines() {
        val base = Theme(fill = FillParams(mode = FillMode.OUTLINE_SILHOUETTE), background = clear)
        val twoLayers = render(base.copy(layers = Theme.baseLayers(Paint.Solid(0), body = red, line = red)))
        val union = render(base.copy(layers = listOf(base.layers[1], PaintLayer("x", Paint.Solid(red), clip = ClipTarget.ICON))))
        for (i in twoLayers.pixels.indices) {
            assertEquals((twoLayers.pixels[i].toInt() and 0xFF).toDouble(), (union.pixels[i].toInt() and 0xFF).toDouble(), 1.0, "byte $i")
        }
    }

    @Test
    fun opacityScalesCoverage() {
        val half = render(Theme(background = clear, layers = listOf(Theme().layers[1], PaintLayer("x", Paint.Solid(red), opacity = 0.5f))))
        assertTrue((0 until size * size).all { alpha(half, it) == 128 })
    }

    @Test
    fun thresholdMatteGivesHardAlpha() {
        val noise = PaintLayer("n", Paint.Noise(NoiseKind.FBM, 3, 5), matte = Matte(MatteMode.THRESHOLD, threshold = 0.5f))
        val img = render(Theme(background = clear, layers = listOf(Theme().layers[1], noise)))
        val alphas = (0 until size * size).map { alpha(img, it) }.toSet()
        assertEquals(setOf(0, 255), alphas)
    }

    /**
     * The spec's use of a matte: a black-and-white texture becomes the alpha of a colour.
     * Single-colour stops paint red; the matte reads the texture before the stops, so red
     * shows where the texture is white and nothing where it is black.
     */
    @Test
    fun blackAndWhiteTextureBecomesTheAlphaOfAColour() {
        val r = IconRenderer()
        val px = ByteArray(size * size * 4) { val p = it / 4; if (it % 4 == 3) -1 else if ((p % size / 8 + p / size / 8) % 2 == 0) -1 else 0 }
        val hash = r.textures.put(TextureStore.encode(size, size, px))
        val layer = PaintLayer("t", Paint.Texture(hash, listOf(dev.iconoclast.render.ColorStop(0f, red))),
            mapping = Mapping(lockOffset = true), matte = Matte())
        val img = render(Theme(background = clear, layers = listOf(Theme().layers[1], layer)), r = r)
        for (i in 0 until size * size) {
            val white = px[4 * i] != 0.toByte()
            assertEquals(if (white) 255 else 0, alpha(img, i), "pixel $i")
            if (white) assertEquals(255, img.pixels[4 * i].toInt() and 0xFF, "red at $i")
        }
    }

    @Test
    fun texturesComeFromTheStoreAndMovePerIcon() {
        val r = IconRenderer()
        val px = ByteArray(64 * 64 * 4) { if (it % 4 == 3) -1 else ((it / 4) * 37 % 251).toByte() }
        val hash = r.textures.put(TextureStore.encode(64, 64, px))
        val layer = PaintLayer("t", Paint.Texture(hash), mapping = Mapping(MappingMode.TILE, scale = 2f), blend = BlendMode.MULTIPLY)
        val theme = with(layer, base = Theme())
        val a = render(theme, "fdroid", r)
        assertContentEquals(a.pixels, render(theme, "fdroid", r).pixels)
        assertFalse(a.pixels.contentEquals(render(theme, "camera", r).pixels), "another icon, another patch")
        val locked = with(layer.copy(mapping = layer.mapping.copy(lockOffset = true)), base = Theme())
        assertContentEquals(render(locked, "fdroid", r).pixels, render(locked, "camera", r).pixels)
        assertFailsWith<IllegalStateException> { render(theme, "fdroid") }
    }

    @Test
    fun stackNeedsOneEffectsLayerAndUniqueIds() {
        assertFailsWith<IllegalArgumentException> { Theme(layers = Theme.baseLayers().filterNot { it.id == "effects" }) }
        assertFailsWith<IllegalArgumentException> { Theme(layers = Theme.baseLayers() + PaintLayer("background", Paint.Solid(red))) }
    }
}
