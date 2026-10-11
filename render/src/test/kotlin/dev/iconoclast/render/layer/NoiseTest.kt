package dev.iconoclast.render.layer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NoiseTest {

    /** Hash and lattice values pinned by an independent Python implementation. */
    @Test
    fun hashMatchesTheReference() {
        assertEquals(1753845952, Noise.lowbias32(1))
        assertEquals(-1859191561, Noise.lowbias32(12345))
        assertEquals(-1489134645, Noise.lowbias32(-7))
        assertEquals(0.44158077239990234f, Noise.lattice(3, -2, 42))
    }

    @Test
    fun sameSeedSameFieldOtherSeedOtherField() {
        val pts = (0 until 200).map { (it * 0.37f) to (it * 0.11f - 5f) }
        for (sample in listOf<(Float, Float, Int) -> Float>(
            { x, y, s -> Noise.value(x, y, s) },
            { x, y, s -> Noise.fbm(x, y, 5, s) },
            { x, y, s -> Noise.ridged(x, y, 5, s) },
            { x, y, s -> Noise.grain(x, y, s) },
        )) {
            val a = pts.map { (x, y) -> sample(x, y, 7) }
            assertEquals(a, pts.map { (x, y) -> sample(x, y, 7) })
            assertNotEquals(a, pts.map { (x, y) -> sample(x, y, 8) })
            assertTrue(a.all { it in 0f..1f }, "in [0, 1]")
            assertTrue(a.toSet().size > 60, "varies")
        }
    }

    /** Value noise passes through the lattice and is continuous between lattice points. */
    @Test
    fun valueNoiseInterpolatesTheLattice() {
        assertEquals(Noise.lattice(3, -2, 42), Noise.value(3f, -2f, 42))
        var prev = Noise.value(0f, 0.5f, 1)
        for (k in 1..400) {
            val v = Noise.value(k / 100f, 0.5f, 1)
            assertTrue(kotlin.math.abs(v - prev) < 0.05f, "jump at ${k / 100f}")
            prev = v
        }
    }
    /** The farthest lattice coordinate, top octave at the smallest scale, keeps floats 1/256 cell apart. */
    @Test
    fun noisePeriodKeepsTheTopOctaveSmooth() {
        val reach = (Mappings.NOISE_PERIOD + Mappings.NOISE_CELLS / 0.25f) * (1 shl 7)
        assertTrue(Math.ulp(reach) <= 1f / 256, "float spacing ${Math.ulp(reach)} at $reach")
    }
}
