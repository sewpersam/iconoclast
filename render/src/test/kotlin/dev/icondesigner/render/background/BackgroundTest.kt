package dev.icondesigner.render.background

import dev.icondesigner.render.BackgroundShape
import dev.icondesigner.render.ColorStop
import dev.icondesigner.render.Paint
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundTest {
    private val size = 200

    private fun area(shape: BackgroundShape, fraction: Float = 1f) = BackgroundShapes.mask(shape, fraction, size).sum()

    @Test
    fun shapeAreas() {
        assertEquals(200f * 200f, area(BackgroundShape.Square), 0.5f)
        assertEquals(160f * 160f, area(BackgroundShape.Square, 0.8f), 0.5f)
        assertEquals((PI * 100 * 100).toFloat(), area(BackgroundShape.Circle), 20f)
        val r = 0.2f * 200
        assertEquals(200f * 200f - (4 - PI.toFloat()) * r * r, area(BackgroundShape.RoundedSquare(0.2f)), 10f)
        // Teardrop: three quarter-circle corners of radius 100 and one of 30.
        val tear = 3 * (PI.toFloat() / 4) * 100 * 100 + (100 * 100 - (1 - PI.toFloat() / 4) * 30 * 30)
        assertEquals(tear, area(BackgroundShape.Teardrop), 20f)
        val sq = area(BackgroundShape.Squircle(4f))
        assertTrue(sq > area(BackgroundShape.Circle) && sq < 40000f)
        // |x|^4 + |y|^4 = 1 has area about 3.708 on the unit square.
        assertEquals(3.7081f * 100 * 100, sq, 30f)
    }

    @Test
    fun borderSitsInside() {
        val shape = BackgroundShape.Square
        val m = BackgroundShapes.mask(shape, 1f, size)
        val b = BackgroundShapes.border(shape, 1f, 0.05f, size, m)
        // A 10 px frame inside a 200 px square.
        assertEquals(200f * 200f - 180f * 180f, b.sum(), 2f)
        assertEquals(1f, b[5 * size + 100])
        assertEquals(0f, b[15 * size + 100])
    }

    @Test
    fun paints() {
        val red = 0xFFFF0000.toInt()
        val clear = 0x00FF0000
        val lin = Paints.fill(Paint.LinearGradient(0f, 0f, 1f, 0f, listOf(ColorStop(0f, red), ColorStop(1f, clear))), 10)
        // Pixel 4 centre is at u = 0.45: premultiplied red fades with alpha, no dark fringe.
        assertEquals(0.55f, lin[4 * 4 + 3], 1e-5f)
        assertEquals(0.55f, lin[4 * 4], 1e-5f)
        val rad = Paints.fill(Paint.RadialGradient(0.5f, 0.5f, 0.5f, listOf(ColorStop(0f, red), ColorStop(1f, clear))), 10)
        assertTrue(rad[(5 * 10 + 5) * 4 + 3] > 0.85f)
        assertEquals(0f, rad[3])
        val solid = Paints.fill(Paint.Solid(0x80FFFFFF.toInt()), 2)
        assertEquals(128f / 255f, solid[0], 1e-6f)
    }
}
