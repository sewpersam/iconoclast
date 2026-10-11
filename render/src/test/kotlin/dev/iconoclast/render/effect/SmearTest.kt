package dev.iconoclast.render.effect

import dev.iconoclast.render.Decay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Readiness item 9: the smear's two decay curves and the frame turn. */
class SmearTest {

    /** Prefix sums and the recursion against the plain weighted sums they replace. */
    @Test
    fun fastFormsEqualDirectSums() {
        val side = 64
        val rnd = dev.iconoclast.render.math.SplitMix64(5)
        val frame = FloatArray(side * side) { if (rnd.nextFloat() < 0.3f) rnd.nextFloat() else 0f }
        for (length in listOf(1f, 2.5f, 7f, 40f, 100f)) {
            val lin = Smear.smear(frame, side, length, Decay.LINEAR)
            val exp = Smear.smear(frame, side, length, Decay.EXPONENTIAL)
            val l = kotlin.math.ceil(length).toInt()
            val r = StrictMath.exp(-3.0 / length)
            for (row in listOf(0, 17, 63)) for (i in 0 until side) {
                var s = 0.0
                var e = 0.0
                for (t in 0..i) {
                    val x = frame[row * side + i - t].toDouble()
                    if (t < l) s += (l - t) * x
                    e += (1 - r) * StrictMath.pow(r, t.toDouble()) * x
                }
                assertEquals(s / (l * (l + 1) / 2.0), lin[row * side + i].toDouble(), 1e-5, "linear $length at $row,$i")
                assertEquals(e, exp[row * side + i].toDouble(), 1e-5, "exponential $length at $row,$i")
            }
        }
    }

    /** One bright pixel trails toward the streak: a straight fade or a long tail. */
    @Test
    fun curvesHaveTheirShape() {
        val side = 32
        val frame = FloatArray(side * side).also { it[5 * side + 4] = 1f }
        val lin = Smear.smear(frame, side, 10f, Decay.LINEAR)
        for (t in 0 until 10) assertEquals((10 - t) / 55f, lin[5 * side + 4 + t], 1e-6f)
        assertEquals(0f, lin[5 * side + 14], "ends at the length")
        assertEquals(0f, lin[5 * side + 3], "nothing behind")
        val exp = Smear.smear(frame, side, 10f, Decay.EXPONENTIAL)
        assertEquals(0.05f, exp[5 * side + 14] / exp[5 * side + 4], 0.001f, "5% left at the length")
        // A solid run stays solid inside.
        val solid = FloatArray(side * side) { 1f }
        assertTrue(Smear.smear(solid, side, 10f, Decay.LINEAR).drop(side * 3 + 12).take(10).all { kotlin.math.abs(it - 1f) < 1e-6f })
    }

    /** Angle 0 trails right, 90 up, 180 left, 270 down; the frame turn is exact at those angles. */
    @Test
    fun anglesPointTheTrail() {
        val size = 48
        val m = FloatArray(size * size).also { for (y in 20..27) for (x in 20..27) it[y * size + x] = 1f }
        for ((angle, step) in listOf(0f to (1 to 0), 90f to (0 to -1), 180f to (-1 to 0), 270f to (0 to 1))) {
            val f = Smear.Frame(size, angle)
            val back = f.fromFrame(f.toFrame(m))
            for (i in m.indices) assertEquals(m[i], back[i], 1e-5f, "round trip at $angle")
            val s = f.fromFrame(Smear.smear(f.toFrame(m), f.side, 12f, Decay.LINEAR))
            val (sx, sy) = step
            // 7 px past the square's far side the trail is still there; 7 px before its near side nothing.
            val outside = s[(24 + 10 * sy) * size + 24 + 10 * sx]
            assertTrue(outside > 0.15f, "trail at $angle: $outside")
            assertEquals(0f, s[(24 - 10 * sy) * size + 24 - 10 * sx], "nothing behind the square at $angle")
        }
    }
}
