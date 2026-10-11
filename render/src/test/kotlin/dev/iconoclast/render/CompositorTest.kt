package dev.iconoclast.render

import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb
import dev.iconoclast.render.raster.rasterizeGlyph
import kotlin.math.PI
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
        r.render("square", icon, theme, 96)
        val misses = r.cache.misses
        r.render("square", icon, theme.copy(layers = Theme.baseLayers(Paint.Solid(0xFF336699.toInt()))), 96)
        r.render("square", icon, theme.copy(layers = Theme.baseLayers(line = 0xFFFF0000.toInt())), 96)
        assertEquals(misses, r.cache.misses, "only paint changed")
        r.render("square", icon, theme.copy(fill = theme.fill.copy(gapWidth = 2f)), 96)
        assertEquals(misses + 2, r.cache.misses, "a gap change recomputes the fill and its lone lines only")
        r.render("square", icon, theme.copy(shape = theme.shape.copy(strokeWeight = 2f)), 96)
        assertEquals(misses + 5, r.cache.misses, "a shape change recomputes geometry, fill and lone lines")
    }

    @Test
    fun thresholdAndLoneLinesReuseTheFillMasks() {
        val r = IconRenderer()
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT))
        val first = r.render("square", icon, theme, 96)
        val misses = r.cache.misses
        r.render("square", icon, theme.copy(fill = theme.fill.copy(fallbackThreshold = 0.3f)), 96)
        r.render("square", icon, theme.copy(fill = theme.fill.copy(keepLoneLines = false)), 96)
        assertEquals(misses, r.cache.misses, "threshold and lone lines choose between cached masks")
        // The same pixels as a renderer that never cached the other settings.
        assertContentEquals(first.pixels, r.render("square", icon, theme, 96).pixels)
        val noLines = r.render("square", icon, theme.copy(fill = theme.fill.copy(keepLoneLines = false)), 96)
        assertContentEquals(IconRenderer().render("square", icon, theme.copy(fill = theme.fill.copy(keepLoneLines = false)), 96).pixels, noLines.pixels)
        // Outline ignores gap and closing, so changing them hits the cache.
        val outline = Theme()
        r.render("square", icon, outline, 96)
        val m2 = r.cache.misses
        r.render("square", icon, outline.copy(fill = outline.fill.copy(gapWidth = 3f, gapClosing = 1.5f)), 96)
        assertEquals(m2, r.cache.misses, "outline does not read gap or closing")
    }

    /**
     * The fill entry holds the body alone: geometry, body and background shape are each
     * counted once, so the byte bound is the memory held. Writing a copy of a mask reaches
     * nothing cached.
     */
    @Test
    fun cachedMasksAreHeldOnceAndReadOnly() {
        val mask = 4L * 96 * 96
        val r = IconRenderer()
        val theme = Theme(fill = FillParams(mode = FillMode.OUTLINE_SILHOUETTE))
        val first = r.render("square", icon, theme, 96)
        assertEquals(3 * mask, r.cache.bytesHeld)
        r.glyphMasks("square", icon, theme, 96).lines!!.toFloatArray().fill(1f)
        assertContentEquals(first.pixels, r.render("square", icon, theme, 96).pixels)
        // Bounded below what one render needs, the cache still gives the same pixels.
        val small = IconRenderer(MaskCache(maxBytes = 2 * mask))
        assertContentEquals(first.pixels, small.render("square", icon, theme, 96).pixels)
        assertTrue(small.cache.bytesHeld <= 2 * mask)
        assertContentEquals(first.pixels, small.render("square", icon, theme, 96).pixels)
    }

    /** The cache is keyed by drawable name: two icons never share masks, one name always does. */
    @Test
    fun cacheKeysAreDrawableNames() {
        val dash = Icon(icon.primitives.drop(1))
        val r = IconRenderer()
        val square = r.render("square", icon, Theme(), 96)
        assertTrue(!square.pixels.contentEquals(r.render("dash", dash, Theme(), 96).pixels))
        val misses = r.cache.misses
        assertContentEquals(square.pixels, r.render("square", icon, Theme(), 96).pixels)
        assertEquals(misses, r.cache.misses)
    }

    /**
     * Derived masks key on the fallback outcome and the lone-lines switch, not on the
     * threshold that produced the outcome.
     */
    @Test
    fun effectKeysCarryTheFallback() {
        val fill = FillKey.of("square", ShapeParams(), FillParams(mode = FillMode.INVERT), 96)
        assertTrue(EffectKey.of(fill, false, true) != EffectKey.of(fill, true, true))
        assertTrue(EffectKey.of(fill, false, true) != EffectKey.of(fill, false, false))
        assertEquals(EffectKey.of(fill, true, true), EffectKey.of(fill, true, false), "a fallback draws no lone lines")
        val outline = FillKey.of("square", ShapeParams(), FillParams(), 96)
        assertEquals(EffectKey.of(outline, false, false), EffectKey.of(outline, true, true))

        val r = IconRenderer()
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT))
        val union = r.iconMask("square", icon, theme, 96)!!
        val m = r.glyphMasks("square", icon, theme, 96)
        for (i in 0 until union.pixels) {
            val b = m.body!![i]
            val l = m.lines!![i]
            assertEquals(b + l - b * l, union[i])
        }
        val misses = r.cache.misses
        r.iconMask("square", icon, theme.copy(fill = theme.fill.copy(fallbackThreshold = 0.2f)), 96)
        assertEquals(misses, r.cache.misses, "same outcome, same entry")
    }

    @Test
    fun outputIsPremultipliedAndClippedToTheShape() {
        val theme = Theme(background = BackgroundParams(shape = BackgroundShape.Circle, size = 0.8f))
        val img = IconRenderer().render("square", icon, theme, 128)
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
        val img = IconRenderer().render("square", icon, theme, 128)
        assertEquals(0, alpha(img, 64, 64), "inside the square, outline mode")
        val s = IconRenderer.unitScale(theme.shape, 128)
        val o = (128 - 48 * s) / 2
        assertEquals(255, alpha(img, (o + 12 * s).toInt(), 64), "on the left stroke")
    }

    @Test
    fun deterministic() {
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT), layers = Theme.baseLayers(
            Paint.RadialGradient(0.3f, 0.3f, 0.9f, listOf(ColorStop(0f, 0xFF3366AA.toInt()), ColorStop(1f, 0xFF112233.toInt()))),
        ))
        val a = IconRenderer().renderPng("square", icon, theme, 160)
        val b = IconRenderer().renderPng("square", icon, theme, 160)
        assertContentEquals(a, b)
    }

    @Test
    fun dotsScaleWithStrokeUnlessKept() {
        val k = 0.5522848f * 0.75f
        val dot = Primitive(Primitive.flags(false, true, false, 0, 0), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CLOSE),
            floatArrayOf(24.75f, 24f, 24.75f, 24f + k, 24f + k, 24.75f, 24f, 24.75f, 24f - k, 24.75f, 23.25f, 24f + k,
                23.25f, 24f, 23.25f, 24f - k, 24f - k, 23.25f, 24f, 23.25f, 24f + k, 23.25f, 24.75f, 24f - k, 24.75f, 24f))
        val dotIcon = Icon(listOf(dot))
        fun area(shape: ShapeParams) = IconRenderer().glyphMasks("square", dotIcon, Theme(shape = shape), 256).lines!!.sum()
        val one = area(ShapeParams())
        assertEquals(4f * one, area(ShapeParams(strokeWeight = 2f)), 0.02f * 4f * one)
        assertEquals(one, area(ShapeParams(strokeWeight = 2f, dots = DotMode.KEEP_ORIGINAL)), 0.001f * one)
    }

    /** The invert cut around a dot is the dot, scaled, plus a gap that does not scale. */
    @Test
    fun dotGapIsConstantAcrossStrokeWeights() {
        val k = 0.5522848f * 0.75f
        val dot = Primitive(Primitive.flags(false, true, false, 0, 0), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CLOSE),
            floatArrayOf(24.75f, 24f, 24.75f, 24f + k, 24f + k, 24.75f, 24f, 24.75f, 24f - k, 24.75f, 23.25f, 24f + k,
                23.25f, 24f, 23.25f, 24f - k, 24f - k, 23.25f, 24f, 23.25f, 24f + k, 23.25f, 24.75f, 24f - k, 24.75f, 24f))
        val gap = 1f
        for (w in listOf(1f, 2f, 3f)) {
            val shape = ShapeParams(strokeWeight = w)
            val scale = IconRenderer.unitScale(shape, 256)
            val spec = IconRenderer.glyphSpec(shape, 256, extraWidth = 2 * gap)
            val cut = rasterizeGlyph(Icon(listOf(dot)), spec, 256).sum()
            val r = (0.75f * w + gap) * scale
            assertEquals((PI * r * r).toFloat(), cut, 0.02f * cut, "stroke weight $w")
        }
    }
}
