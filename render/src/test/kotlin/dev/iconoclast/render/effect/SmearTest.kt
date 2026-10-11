package dev.iconoclast.render.effect

import dev.iconoclast.render.Decay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Readiness item 9 and the review's hold ruling: the smear's two decays and the frame turn. */
class SmearTest {

    /** The recursions against the plain maxima they replace: y[i] = max over t of x[i - t] decayed by t. */
    @Test
    fun recursionsEqualDirectMaxima() {
        val side = 64
        val rnd = dev.iconoclast.render.math.SplitMix64(5)
        val frame = FloatArray(side * side) { if (rnd.nextFloat() < 0.3f) rnd.nextFloat() else 0f }
        for (length in listOf(1f, 2.5f, 7f, 40f, 100f)) {
            val lin = Smear.smear(frame, side, length, Decay.LINEAR)
            val exp = Smear.smear(frame, side, length, Decay.EXPONENTIAL)
            val r = StrictMath.exp(-3.0 / length)
            for (row in listOf(0, 17, 63)) for (i in 0 until side) {
                var l = 0.0
                var e = 0.0
                for (t in 0..i) {
                    val x = frame[row * side + i - t].toDouble()
                    l = maxOf(l, x - t / length.toDouble())
                    e = maxOf(e, x * StrictMath.pow(r, t.toDouble()))
                }
                assertEquals(l, lin[row * side + i].toDouble(), 1e-4, "linear $length at $row,$i")
                assertEquals(e, exp[row * side + i].toDouble(), 1e-4, "exponential $length at $row,$i")
            }
        }
    }

    /** One bright pixel holds and fades toward the streak: a straight fade, or 5% left at the length. */
    @Test
    fun curvesHaveTheirShape() {
        val side = 32
        val frame = FloatArray(side * side).also { it[5 * side + 4] = 1f }
        val lin = Smear.smear(frame, side, 10f, Decay.LINEAR)
        for (t in 0..10) assertEquals(maxOf(0f, 1f - t / 10f), lin[5 * side + 4 + t], 1e-5f)
        assertEquals(0f, lin[5 * side + 3], "nothing behind")
        // The fractional length is honoured: 2.5 px reaches 0.2 after two steps.
        val short = Smear.smear(frame, side, 2.5f, Decay.LINEAR)
        assertEquals(0.2f, short[5 * side + 6], 1e-5f)
        assertEquals(0f, short[5 * side + 7])
        val exp = Smear.smear(frame, side, 10f, Decay.EXPONENTIAL)
        assertEquals(0.05f, exp[5 * side + 14] / exp[5 * side + 4], 0.001f, "5% left at the length")
        assertEquals(1f, exp[5 * side + 4], "the source pixel keeps its paint")
        // A one-pixel line streaks as strongly as a solid body: the hold, not a blur.
        val line = FloatArray(side * side).also { for (r in 0 until side) it[r * side + 4] = 1f }
        val held = Smear.smear(line, side, 10f, Decay.LINEAR)
        assertEquals(0.5f, held[8 * side + 9], 1e-5f)
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
