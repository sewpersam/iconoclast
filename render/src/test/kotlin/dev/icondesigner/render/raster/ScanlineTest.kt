package dev.icondesigner.render.raster

import kotlin.test.Test
import kotlin.test.assertEquals

class ScanlineTest {

    private fun fill(evenOdd: Boolean, size: Int, vararg polys: FloatArray): FloatArray {
        val e = EdgeList()
        polys.forEach { e.addPolygon(it) }
        return FloatArray(size * size).also { fillCoverage(e, evenOdd, size, size, it) }
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    @Test
    fun rectangleHasExactCoverage() {
        // Vertical edges anywhere, horizontal edges on sample-row boundaries: exact.
        val m = fill(false, 10, rect(2.25f, 1.5f, 7.75f, 4.25f))
        assertEquals(0.75f * 0.5f, m[1 * 10 + 2], 1e-6f)
        assertEquals(0.5f, m[1 * 10 + 4], 1e-6f)
        assertEquals(1f, m[2 * 10 + 4], 1e-6f)
        assertEquals(0.25f, m[4 * 10 + 5], 1e-6f)
        assertEquals(0.75f * 0.25f, m[4 * 10 + 7], 1e-6f)
        assertEquals(0f, m[5 * 10 + 5])
        assertEquals(5.5f * 2.75f, m.sum(), 1e-4f)
    }

    @Test
    fun windingRules() {
        val outer = rect(1f, 1f, 9f, 9f)
        val inner = rect(3f, 3f, 7f, 7f)
        assertEquals(64f, fill(false, 10, outer, inner).sum(), 1e-4f)
        assertEquals(48f, fill(true, 10, outer, inner).sum(), 1e-4f)
        // Opposite orientation cancels under nonzero too.
        val reversed = floatArrayOf(3f, 3f, 3f, 7f, 7f, 7f, 7f, 3f)
        assertEquals(48f, fill(false, 10, outer, reversed).sum(), 1e-4f)
    }

    @Test
    fun positivePiecesUnion() {
        val e = EdgeList()
        e.addPositive(rect(1f, 1f, 6f, 6f))
        e.addPositive(floatArrayOf(4f, 4f, 4f, 9f, 9f, 9f, 9f, 4f)) // clockwise, overlaps
        val m = FloatArray(100).also { fillCoverage(e, false, 10, 10, it) }
        assertEquals(25f + 25f - 4f, m.sum(), 1e-4f)
        assertEquals(1f, m[4 * 10 + 4])
    }

    @Test
    fun clipsToCanvasAndCompositesSourceOver() {
        val m = fill(false, 4, rect(-5f, -5f, 2f, 9f))
        assertEquals(8f, m.sum(), 1e-4f)
        val half = FloatArray(16) { 0.5f }
        val e = EdgeList().apply { addPolygon(rect(0f, 0f, 0.5f, 4f)) }
        fillCoverage(e, false, 4, 4, half)
        assertEquals(0.75f, half[0], 1e-6f)
        assertEquals(0.5f, half[1], 1e-6f)
    }

    @Test
    fun boxDownAverages() {
        val big = FloatArray(16) { if (it % 4 < 2) 1f else 0f }
        val small = boxDown(big, 4, 2)
        assertEquals(listOf(1f, 0f, 1f, 0f), small.toList())
    }
}
