package dev.iconoclast.app.editor

import dev.iconoclast.render.ColorStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RampTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private fun offsets(s: List<ColorStop>) = s.map { it.offset }

    @Test
    fun addingToAnEvenRampKeepsItEven() {
        var s = listOf(ColorStop(0f, black), ColorStop(1f, white))
        val (three, at) = Ramp.add(s, 0.8f)
        assertEquals(listOf(0f, 0.5f, 1f), offsets(three))
        assertEquals(1, at)
        assertEquals(0xFFCCCCCC.toInt(), three[1].argb)
        s = Ramp.add(three, 0.1f).first
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), offsets(s))
        assertTrue(Ramp.isEven(s))
    }

    @Test
    fun aMovedStopStaysAndTheRestSpreadAroundIt() {
        val s = listOf(ColorStop(0f, black), ColorStop(0.2f, 0xFFFF0000.toInt()), ColorStop(1f, white))
        assertFalse(Ramp.isEven(s))
        val (out, at) = Ramp.add(s, 0.9f)
        assertEquals(2, at)
        assertEquals(listOf(0f, 0.2f, 0.6f, 1f), offsets(out))
    }

    @Test
    fun moveHoldsBetweenNeighboursAndRemoveKeepsTwo() {
        val s = listOf(ColorStop(0f, black), ColorStop(0.5f, 0xFFFF0000.toInt()), ColorStop(1f, white))
        assertEquals(0.5f, Ramp.move(s, 0, 0.9f)[0].offset)
        assertEquals(0.25f, Ramp.move(s, 1, 0.25f)[1].offset)
        val two = Ramp.remove(s, 1)
        assertEquals(2, two.size)
        assertEquals(two, Ramp.remove(two, 0))
        assertEquals(listOf(0f, 0.5f, 1f), offsets(Ramp.even(listOf(ColorStop(0f, 1), ColorStop(0.9f, 2), ColorStop(1f, 3)))))
    }
}
