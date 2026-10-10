package dev.icondesigner.render

import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.format.Primitive
import dev.icondesigner.render.format.Verb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositorTest {

    /** A closed square, 12 to 36 on the 48 grid, and a free dash. */
    private val icon = Icon(listOf(
        Primitive(Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.LINE, Verb.LINE, Verb.LINE, Verb.CLOSE),
            floatArrayOf(12f, 12f, 36f, 12f, 36f, 36f, 12f, 36f)),
        Primitive(Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.LINE), floatArrayOf(6f, 44f, 14f, 44f)),
    ))

    private fun alpha(img: RgbaImage, x: Int, y: Int) = img.pixels[(y * img.size + x) * 4 + 3].toInt() and 0xFF

    @Test
    fun paintChangesReuseGeometryAndFill() {
        val r = IconRenderer()
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT))
        r.render(1, icon, theme, 96)
        val misses = r.cache.misses
        r.render(1, icon, theme.copy(background = theme.background.copy(paint = Paint.Solid(0xFF336699.toInt()))), 96)
        r.render(1, icon, theme.copy(glyph = GlyphPaint(line = 0xFFFF0000.toInt())), 96)
        assertEquals(misses, r.cache.misses, "only paint changed")
        r.render(1, icon, theme.copy(fill = theme.fill.copy(gapWidth = 2f)), 96)
        assertEquals(misses + 1, r.cache.misses, "a fill change recomputes the fill only")
        r.render(1, icon, theme.copy(shape = theme.shape.copy(strokeWeight = 2f)), 96)
        assertEquals(misses + 3, r.cache.misses, "a shape change recomputes geometry and fill")
    }

    @Test
    fun outputIsPremultipliedAndClippedToTheShape() {
        val theme = Theme(background = BackgroundParams(shape = BackgroundShape.Circle, size = 0.8f))
        val img = IconRenderer().render(0, icon, theme, 128)
        for (i in 0 until 128 * 128) {
            val a = img.pixels[4 * i + 3].toInt() and 0xFF
            for (k in 0..2) assertTrue((img.pixels[4 * i + k].toInt() and 0xFF) <= a)
        }
        assertEquals(0, alpha(img, 2, 2))
        assertEquals(255, alpha(img, 64, 64))
    }

    @Test
    fun disabledBackgroundLeavesOnlyTheGlyph() {
        val theme = Theme(background = BackgroundParams(enabled = false))
        val img = IconRenderer().render(0, icon, theme, 128)
        assertEquals(0, alpha(img, 64, 64), "inside the square, outline mode")
        val s = IconRenderer.unitScale(theme.shape, 128)
        val o = (128 - 48 * s) / 2
        assertEquals(255, alpha(img, (o + 12 * s).toInt(), 64), "on the left stroke")
    }

    @Test
    fun deterministic() {
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT), background = BackgroundParams(
            paint = Paint.RadialGradient(0.3f, 0.3f, 0.9f, listOf(ColorStop(0f, 0xFF3366AA.toInt()), ColorStop(1f, 0xFF112233.toInt()))),
        ))
        val a = IconRenderer().renderPng(0, icon, theme, 160)
        val b = IconRenderer().renderPng(0, icon, theme, 160)
        assertContentEquals(a, b)
    }
}
