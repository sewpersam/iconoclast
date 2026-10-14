package dev.iconoclast.app.editor

import dev.iconoclast.render.ColorStop
import dev.iconoclast.app.ThemePreviews
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.SampleIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorControlsTest {

    @Test
    fun sampleSetIsTheGoldenSet() {
        assertSame(SampleIcons.NAMES, GoldenSet.SAMPLES)
        assertEquals(24, SampleIcons.NAMES.size)
        assertTrue(ThemePreviews.SAMPLES.all { it in SampleIcons.NAMES })
    }

    @Test
    fun hsvRoundTripsEveryChannelStep() {
        for (c in listOf(0xFF202124, 0xFF00A86B, 0xFFFFFFFF, 0xFF000000, 0x80FF0000, 0xFF7FD1B9, 0xFF123456)) {
            val argb = c.toInt()
            assertEquals(argb, fromHsv(toHsv(argb), argb), hex(argb))
        }
    }

    @Test
    fun hexKeepsOrSetsAlpha() {
        assertEquals("#202124", hex(0xFF202124.toInt()))
        assertEquals("#00000000", hex(0))
        assertEquals(0x80ABCDEF.toInt(), parseHex("abcdef", 0x80000000.toInt()))
        assertEquals(0x12345678, parseHex("#12345678", -1))
        assertNull(parseHex("12345", -1))
        assertNull(parseHex("GG0000", -1))
    }

    @Test
    fun linearAngleRoundTrips() {
        val stops = listOf(ColorStop(0f, -1), ColorStop(1f, 0))
        for (a in listOf(0f, 45f, 90f, 135f, 270f, 359f)) assertEquals(a, angleOf(linear(a, stops)), 0.01f)
    }

    /** Spec 4.5: lengths in 48-grid units read as a multiple of the original Arcticons line. */
    @Test
    fun unitsPeopleCanPicture() {
        assertEquals("1.0×", times(1f))
        assertEquals("1.75×", times(1.75f))
        assertEquals("0.25×", times(0.25f))
        assertEquals("0.5×", times(0.5f))
    }

    /** Ruling 4: icon size reads as the share of the tile, so 100% touches the edges, and the default stays put. */
    @Test
    fun iconSizeReachesTheEdges() {
        assertEquals("100%", iconSize(dev.iconoclast.render.MAX_ICON_SCALE))
        assertEquals("49%", iconSize(dev.iconoclast.render.ShapeParams().iconScale))
        assertEquals(1f, dev.iconoclast.render.IconRenderer.unitScale(dev.iconoclast.render.ShapeParams(iconScale = dev.iconoclast.render.MAX_ICON_SCALE), 48), 1e-5f)
    }

    /** Every control has help text; the info tip alone is filled in per page. */
    @Test
    fun everyTipExplainsItself() {
        val tips = Tips::class.java.declaredFields.filter { it.type == Tip::class.java }.map { it.isAccessible = true; it.get(Tips) as Tip }
        assertTrue(tips.size > 100)
        for (t in tips) if (t !== Tips.INFO) assertTrue(t.help.isNotBlank(), t.label)
        // One name per setting: the per-icon sheet uses the editor's names.
        assertEquals(Tips.LINE_THICKNESS.label, Tips.ICON_LINE_THICKNESS.label)
        assertEquals(Tips.FILL_STYLE.label, Tips.ICON_FILL_STYLE.label)
        assertEquals(Tips.CLOSE_SMALL_GAPS.label, Tips.ICON_CLOSE_GAPS.label)
    }
}
