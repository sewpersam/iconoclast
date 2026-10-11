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
}
