package dev.icondesigner.render.geom

import dev.icondesigner.render.LineCap
import dev.icondesigner.render.LineJoin
import dev.icondesigner.render.raster.EdgeList
import dev.icondesigner.render.raster.fillCoverage
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokerTest {

    private fun area(
        line: Polyline, width: Float, cap: LineCap = LineCap.BUTT, join: LineJoin = LineJoin.MITER,
        miter: Float = 4f, size: Int = 40,
    ): Float {
        val e = EdgeList()
        Stroker(width / 2, cap, join, miter, 0.01f).stroke(line, e)
        return FloatArray(size * size).also { fillCoverage(e, false, size, size, it) }.sum()
    }

    private fun open(vararg p: Float) = Polyline(p, closed = false)

    @Test
    fun caps() {
        val l = open(10f, 20f, 30f, 20f)
        assertEquals(40f, area(l, 2f, LineCap.BUTT), 0.01f)
        assertEquals(40f + 4f, area(l, 2f, LineCap.SQUARE), 0.01f)
        assertEquals(40f + PI.toFloat(), area(l, 2f, LineCap.ROUND), 0.02f)
    }

    @Test
    fun diagonalSegment() {
        // A 3-4-5 segment: area is length times width wherever it lies.
        assertEquals(10f * 3f, area(open(10f, 10f, 16f, 18f), 3f), 0.02f)
    }

    @Test
    fun zeroLengthLineIsADot() {
        val dot = open(20f, 20f, 20f, 20f)
        assertEquals(0f, area(dot, 4f, LineCap.BUTT))
        assertEquals(16f, area(dot, 4f, LineCap.SQUARE), 0.01f)
        assertEquals((PI * 4).toFloat(), area(dot, 4f, LineCap.ROUND), 0.03f)
    }

    @Test
    fun rightAngleJoins() {
        // An L of two 10-long legs, width 2, butt caps. The legs share a 1x1 inner square.
        val l = open(10f, 10f, 20f, 10f, 20f, 20f)
        val legs = 20f + 20f - 1f
        assertEquals(legs + 1f, area(l, 2f, join = LineJoin.MITER), 0.01f)
        assertEquals(legs + 0.5f, area(l, 2f, join = LineJoin.BEVEL), 0.01f)
        assertEquals(legs + (PI / 4).toFloat(), area(l, 2f, join = LineJoin.ROUND), 0.01f)
        // Miter ratio at 90 degrees is sqrt 2: a limit of 1.4 bevels it.
        assertEquals(legs + 0.5f, area(l, 2f, join = LineJoin.MITER, miter = 1.4f), 0.01f)
        assertEquals(legs + 1f, area(l, 2f, join = LineJoin.MITER, miter = 1.42f), 0.01f)
    }

    @Test
    fun closedSquareHasNoCapsAndJoinsEveryCorner() {
        val sq = Polyline(floatArrayOf(10f, 10f, 30f, 10f, 30f, 30f, 10f, 30f), closed = true)
        // Outer 22x22 minus inner 18x18.
        assertEquals(22f * 22f - 18f * 18f, area(sq, 2f, LineCap.ROUND, LineJoin.MITER), 0.02f)
        // A repeated closing point changes nothing.
        val sq2 = Polyline(floatArrayOf(10f, 10f, 30f, 10f, 30f, 30f, 10f, 30f, 10f, 10f), closed = true)
        assertEquals(22f * 22f - 18f * 18f, area(sq2, 2f, LineCap.ROUND, LineJoin.MITER), 0.02f)
    }

    @Test
    fun reversalWithRoundJoinCapsTheFarEnd() {
        // Out and back: the round join at the turn must be a forward half-disk.
        val a = area(open(10f, 20f, 20f, 20f, 10f, 20f), 2f, LineCap.BUTT, LineJoin.ROUND)
        assertEquals(20f + (PI / 2).toFloat(), a, 0.02f)
    }

    @Test
    fun fullCircleRingArea() {
        val n = 256
        val xy = FloatArray(2 * n) { i ->
            val a = 2 * PI * (i / 2) / n
            (if (i % 2 == 0) 20 + 10 * StrictMath.cos(a) else 20 + 10 * StrictMath.sin(a)).toFloat()
        }
        val ring = area(Polyline(xy, closed = true), 2f, join = LineJoin.ROUND)
        assertTrue(kotlin.math.abs(ring - (2 * PI * 10 * 2).toFloat()) < 0.1f, "ring $ring")
    }
}
