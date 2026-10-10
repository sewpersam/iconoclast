package dev.iconoclast.render.fill

import dev.iconoclast.render.FillMode
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb
import dev.iconoclast.render.geom.Affine
import dev.iconoclast.render.raster.GlyphSpec
import dev.iconoclast.render.raster.rasterizeGlyph
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Small pixel-space fixtures: 64 px, strokes 2 px wide, gap 2 px. */
class FillModesTest {

    private val size = 64
    private val stroke = 2f
    private val gap = 2f

    private fun line(vararg xy: Float, closed: Boolean = false): Primitive {
        val verbs = ByteArray(xy.size / 2 + if (closed) 1 else 0) { if (it == 0) Verb.MOVE else if (it < xy.size / 2) Verb.LINE else Verb.CLOSE }
        return Primitive(Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND), 1f, 4f, verbs, xy)
    }

    private val square = line(16f, 16f, 48f, 16f, 48f, 48f, 16f, 48f, closed = true)

    private fun input(vararg prims: Primitive, closing: Float = 0f, gap: Float = this.gap): FillInput {
        val icon = Icon(prims.toList())
        val g = rasterizeGlyph(icon, GlyphSpec(Affine(1f, 0f, 0f), strokeScale = stroke), size)
        val cut = rasterizeGlyph(
            icon,
            GlyphSpec(Affine(1f, 0f, 0f), strokeScale = stroke, extraWidth = 2 * gap, cap = LineCap.ROUND, join = LineJoin.ROUND),
            size,
        )
        return FillInput(g, size, stroke, gap, closing, cut)
    }

    private fun at(m: FloatArray, x: Int, y: Int) = m[y * size + x]

    @Test
    fun invertFillsTheEnclosedRegionLessTheGap() {
        val m = FillModes.invert(input(square), 0.1f, keepLoneLines = true)
        val fill = assertNotNull(m.body)
        assertFalse(m.fellBack)
        // Inner edge of the stroke at 17, gap to 19: the fill spans 19 to 45.
        assertEquals(26f * 26f, fill.sum(), 0.5f)
        assertEquals(1f, at(fill, 32, 32))
        assertEquals(0f, at(fill, 18, 32))
        assertEquals(1f, at(fill, 19, 32))
        assertEquals(0f, at(fill, 8, 8))
        // The square borders the fill everywhere: no lone lines.
        assertEquals(0f, assertNotNull(m.lines).sum(), 0.01f)
    }

    @Test
    fun loneLinesSurviveAndAttachedOnesStopShort() {
        val dash = line(4f, 58f, 12f, 58f)
        val antenna = line(16f, 16f, 6f, 6f)
        val m = FillModes.invert(input(square, dash, antenna), 0.1f, keepLoneLines = true)
        val lines = assertNotNull(m.lines)
        // The free dash survives whole: 8 long, 2 wide, two round caps.
        var dashSum = 0f
        for (y in 54..62) for (x in 0..16) dashSum += at(lines, x, y)
        assertEquals(16f + PI.toFloat(), dashSum, 0.05f)
        // The antenna's free end is drawn, its root on the square is not.
        assertEquals(1f, at(lines, 8, 8))
        assertEquals(0f, at(lines, 16, 16))
        assertEquals(0f, at(lines, 16, 32))
        // Without the option nothing is drawn on top.
        assertNull(FillModes.invert(input(square, dash), 0.1f, keepLoneLines = false).lines)
    }

    @Test
    fun openShapeFallsBackToOutline() {
        val u = line(16f, 16f, 16f, 48f, 48f, 48f, 48f, 16f)
        val i = input(u)
        val m = FillModes.invert(i, 0.1f, keepLoneLines = true)
        assertTrue(m.fellBack)
        assertNull(m.body)
        assertTrue(m.lines.contentEquals(i.geometry))
        // A threshold of 0 never falls back.
        assertFalse(FillModes.invert(i, 0f, keepLoneLines = true).fellBack)
    }

    @Test
    fun gapClosingSealsANearlyClosedShape() {
        // The square's left side has a 3 px gap between its ends.
        val c = line(16f, 30f, 16f, 16f, 48f, 16f, 48f, 48f, 16f, 48f, 16f, 35f)
        assertTrue(FillModes.invert(input(c, closing = 0f), 0.1f, true).fellBack)
        // Closing 2 px seals a 3 px gap between thin stroke ends.
        assertFalse(FillModes.invert(input(c, closing = 2f), 0.1f, true).fellBack)
        val sealed = FillModes.invert(input(c, closing = 2f), 0.1f, true)
        assertFalse(sealed.fellBack)
        assertEquals(1f, at(assertNotNull(sealed.body), 32, 32))
    }

    /**
     * A bracket off the square's top right corner makes a channel 6 px wide between
     * strokes, open at the bottom: outside the icon. Closing 4 px seals its width, so the
     * channel is a pocket neither side reaches. It must stay outside. The gap of 1 px
     * leaves the channel's middle uncut, so a wrong fill would show there.
     */
    @Test
    fun exteriorPocketsStayOutsideWhenClosingExceedsGap() {
        val bracket = line(48f, 16f, 56f, 16f, 56f, 40f)
        val m = FillModes.invert(input(square, bracket, closing = 4f, gap = 1f), 0.1f, keepLoneLines = false)
        val fill = assertNotNull(m.body)
        assertEquals(1f, at(fill, 32, 32), "the square is still filled")
        for (y in 20..36) assertEquals(0f, at(fill, 52, y), "channel at y $y")
        // Silhouette reads the same regions: the channel is not part of the shape.
        val sil = FillModes.silhouette(input(square, bracket, closing = 4f, gap = 1f))
        assertEquals(0f, at(sil, 52, 28))
        // A pocket inside still fills: the square's corners under the same closing.
        assertEquals(1f, at(fill, 20, 20))
    }

    /**
     * A closed square inside a dashed square, signal's layout: the band between them is
     * 4 px clear, the dashes 2 px apart. Closing 1.5 px seals the dashes while the band's
     * core is still open, so the band is enclosed. Closing 3 px buries the band under the
     * dilated strokes; it must stay enclosed, not turn into an exterior pocket.
     */
    @Test
    fun sealingIsMonotoneInTheClosing() {
        val inner = line(20f, 20f, 44f, 20f, 44f, 44f, 20f, 44f, closed = true)
        val dashes = listOf(14f, 22f, 30f, 38f, 46f).flatMap { a ->
            val b = a + 4f
            listOf(line(a, 14f, b, 14f), line(a, 50f, b, 50f), line(14f, a, 14f, b), line(50f, a, 50f, b))
        }.toTypedArray()
        val band = { closing: Float ->
            val m = FillModes.invert(input(inner, *dashes, closing = closing, gap = 0.5f), 0f, keepLoneLines = false)
            at(assertNotNull(m.body), 47, 32)
        }
        assertEquals(0f, band(0f), "open dashes: the band is outside")
        var last = 0f
        for (c in listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 4f)) {
            val v = band(c)
            assertTrue(v >= last, "band fill fell from $last to $v at closing $c")
            last = v
        }
        assertEquals(1f, band(1.5f))
        assertEquals(1f, band(3f), "still enclosed once buried")
    }

    @Test
    fun silhouetteFillsInsideAndStroke() {
        val i = input(square)
        val sil = FillModes.silhouette(i)
        // Outer edge 15 to 49, round outer corners of radius 1.
        assertEquals(34f * 34f - (4 - PI.toFloat()), sil.sum(), 0.5f)
        assertEquals(1f, at(sil, 17, 32))
        assertEquals(1f, at(sil, 32, 32))
        val both = FillModes.apply(FillMode.OUTLINE_SILHOUETTE, i, 0.1f, true)
        assertTrue(both.body.contentEquals(sil))
        assertTrue(both.lines.contentEquals(i.geometry))
        val outline = FillModes.apply(FillMode.OUTLINE, i, 0.1f, true)
        assertNull(outline.body)
    }
}
