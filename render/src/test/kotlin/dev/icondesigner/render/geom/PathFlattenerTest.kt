package dev.icondesigner.render.geom

import dev.icondesigner.render.format.Primitive
import dev.icondesigner.render.format.Verb
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathFlattenerTest {

    @Test
    fun subpathsCloseAndTransform() {
        val p = Primitive(1, 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.LINE, Verb.LINE, Verb.CLOSE, Verb.MOVE, Verb.LINE, Verb.MOVE),
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 5f, 5f, 5f, 5f, 9f, 9f))
        val lines = PathFlattener.flatten(p, Affine(2f, 10f, 20f), 0.1f)
        assertEquals(2, lines.size, "a bare trailing MOVE is dropped")
        assertTrue(lines[0].closed)
        assertEquals(listOf(10f, 20f, 12f, 20f, 12f, 22f), lines[0].xy.toList())
        assertEquals(listOf(20f, 30f, 20f, 30f), lines[1].xy.toList(), "zero-length LINE kept")
    }

    @Test
    fun cubicStaysWithinTolerance() {
        // A quarter circle as a cubic; every flattened point must lie near the true arc.
        val k = 0.5522848f * 10f
        val p = Primitive(1, 1f, 4f, byteArrayOf(Verb.MOVE, Verb.CUBIC),
            floatArrayOf(10f, 0f, 10f, k, k, 10f, 0f, 10f))
        val tol = 0.01f
        val xy = PathFlattener.flatten(p, Affine(1f, 0f, 0f), tol)[0].xy
        assertTrue(xy.size / 2 > 4)
        for (i in 0 until xy.size / 2 - 1) {
            val mx = (xy[2 * i] + xy[2 * i + 2]) / 2
            val my = (xy[2 * i + 1] + xy[2 * i + 3]) / 2
            // The cubic is within 0.0003 of the circle; chords sag inside it.
            assertTrue(10f - hypot(mx, my) < tol + 0.001f, "chord $i sags ${10f - hypot(mx, my)}")
        }
        assertEquals(0f, xy[xy.size - 2]); assertEquals(10f, xy[xy.size - 1])
    }

    @Test
    fun scaleAboutKeepsCentre() {
        val a = Affine(2f, 1f, 1f).thenScaleAbout(3f, 5f, 5f)
        assertEquals(5f, a.x(2f)); assertEquals(5f, a.y(2f))
        assertEquals(11f, a.x(3f))
    }
}
