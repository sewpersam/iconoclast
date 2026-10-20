package dev.iconoclast.render.background

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.Border
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Crop
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Marble
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Pattern
import dev.iconoclast.render.RgbaImage
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.Icon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Background size, schema 10: the background layer's own gradient or image is laid out on
 * the shape's square; patterns, layers above it and the border keep the canvas's scale.
 */
class BackgroundSizeTest {

    private val none = Icon(emptyList())

    /** [bg] as the background paint of a square tile [size] of the canvas, the icon hidden. */
    private fun theme(bg: Paint, size: Float, mapping: Mapping = Mapping(), extra: List<Layer> = emptyList()) = Theme(
        background = BackgroundParams(shape = BackgroundShape.Square, size = size),
        layers = Theme.baseLayers(bg).flatMap { l ->
            when (l.id) {
                Layer.BACKGROUND_ID -> listOf((l as PaintLayer).copy(mapping = mapping)) + extra
                Layer.ICON_FILL_ID, Layer.ICON_LINES_ID -> listOf((l as PaintLayer).copy(visible = false))
                else -> listOf(l)
            }
        },
    )

    private fun renderer() = IconRenderer().apply { textures.put(Marble.PNG) }

    private fun px(img: RgbaImage, x: Int, y: Int, c: Int) = img.pixels[(y * img.size + x) * 4 + c].toInt() and 0xFF

    /**
     * The shape's square at half of a [canvas] canvas against the whole of a canvas half
     * that size: pixel for pixel the same picture, up to float rounding. Returns the
     * largest channel difference.
     */
    private fun halfAgainstWhole(t: (Float) -> Theme, canvas: Int = 128): Int {
        val r = renderer()
        val half = r.render("slot", none, t(0.5f), canvas)
        val whole = r.render("slot", none, t(1f), canvas / 2)
        val o = canvas / 4
        var worst = 0
        for (y in 0 until canvas / 2) for (x in 0 until canvas / 2) for (c in 0..3) {
            worst = maxOf(worst, abs(px(half, x + o, y + o, c) - px(whole, x, y, c)))
        }
        return worst
    }

    private val ramp = listOf(ColorStop(0f, 0xFF000000.toInt()), ColorStop(1f, 0xFFFFFFFF.toInt()))

    @Test
    fun sizeRunsFromHalfToFull() {
        BackgroundParams(size = 0.5f)
        BackgroundParams(size = 1f)
        assertFailsWith<IllegalArgumentException> { BackgroundParams(size = 0.49f) }
        assertFailsWith<IllegalArgumentException> { BackgroundParams(size = 1.01f) }
    }

    @Test
    fun frameOneIsTheCanvasFill() {
        for (p in listOf(Paint.LinearGradient(0.1f, 0.2f, 0.9f, 0.7f, ramp), Paint.RadialGradient(0.4f, 0.3f, 0.6f, ramp),
            Paint.AngularGradient(0.5f, 0.5f, 30f, ramp))) {
            assertContentEquals(Paints.fill(p, 37), Paints.fill(p, 37, 1f))
        }
    }

    @Test
    fun gradientsSpanTheShape() {
        for (p in listOf(Paint.LinearGradient(0f, 0f, 1f, 1f, ramp), Paint.RadialGradient(0.4f, 0.35f, 0.8f, ramp),
            Paint.AngularGradient(0.5f, 0.5f, 270f, ramp))) {
            val worst = halfAgainstWhole({ theme(p, it) })
            assertTrue(worst <= 1, "$p differs by $worst")
        }
    }

    @Test
    fun imagesSpanTheShapeWithCropAndRandomPlacement() {
        val crop = Crop(0.1f, 0.1f, 0.7f, 0.8f)
        for (m in listOf(
            Mapping(MappingMode.FILL, scale = 1.5f, randomRotation = 30f, crop = crop),
            Mapping(MappingMode.FIT, scale = 0.8f, randomScale = 0.5f),
            Mapping(MappingMode.TILE, scale = 0.5f, randomShift = 0.5f, seed = 3, crop = crop),
        )) {
            val worst = halfAgainstWhole({ theme(Paint.Texture(Marble.HASH), it, m) })
            assertTrue(worst <= 1, "$m differs by $worst")
        }
    }

    @Test
    fun patternsAndLayersAboveKeepTheCanvasScale() {
        val clouds = Paint.Noise(Pattern.Noise(detail = 3f), 2, ramp)
        val overlay = PaintLayer("overlay", Paint.LinearGradient(0f, 0f, 1f, 0f, ramp), clip = ClipTarget.BELOW, opacity = 0.5f)
        val r = renderer()
        for (t in listOf<(Float) -> Theme>({ theme(clouds, it) }, { theme(Paint.Solid(0xFF336699.toInt()), it, extra = listOf(overlay)) })) {
            val half = r.render("slot", none, t(0.5f), 128)
            val whole = r.render("slot", none, t(1f), 128)
            for (y in 33 until 95) for (x in 33 until 95) for (c in 0..3) {
                assertEquals(px(whole, x, y, c), px(half, x, y, c), "at $x,$y")
            }
            assertEquals(0, px(half, 20, 64, 3), "outside the shape")
        }
    }

    @Test
    fun borderKeepsItsWidth() {
        fun width(size: Float): Int {
            val t = theme(Paint.Solid(0xFF000000.toInt()), size).let { it.copy(background = it.background.copy(border = Border(0.05f, 0xFFFF0000.toInt()))) }
            val img = renderer().render("slot", none, t, 200)
            return (0 until 100).count { x -> px(img, x, 100, 0) > 128 }
        }
        assertEquals(10, width(1f))
        assertEquals(10, width(0.5f))
    }

    @Test
    fun shapeShrinksAboutTheCentre() {
        val img = renderer().render("slot", none, theme(Paint.Solid(0xFFFFFFFF.toInt()), 0.5f), 128)
        assertEquals(0, px(img, 31, 64, 3))
        assertEquals(255, px(img, 32, 64, 3))
        assertEquals(255, px(img, 95, 64, 3))
        assertEquals(0, px(img, 96, 64, 3))
    }
}
