package dev.iconoclast.render.math

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M5 readiness 8: every per-pixel path uses StrictMath or plain arithmetic, and every
 * random source is seeded. Checked on the source text, so a new file cannot slip past.
 */
class DeterminismRulesTest {

    @Test
    fun splitMix64MatchesTheReference() {
        // The reference implementation's first outputs for seed 0, and a pinned seed.
        val zero = SplitMix64(0)
        assertEquals(0xe220a8397b1dcdafuL.toLong(), zero.nextLong())
        assertEquals(0x6e789e6aa1b965f4uL.toLong(), zero.nextLong())
        assertEquals(0x06c45d188009454fuL.toLong(), zero.nextLong())
        val pinned = SplitMix64(1234567)
        assertEquals(listOf(6457827717110365317L, 3203168211198807973L, -8629252141511181193L), List(3) { pinned.nextLong() })
        val f = SplitMix64(9).let { r -> List(1000) { r.nextFloat() } }
        assertTrue(f.all { it >= 0f && it < 1f })
    }

    /** The arithmetic floor and ceil give StrictMath's bits, signed zeros and halves included. */
    @Test
    fun arithmeticFloorAndCeilMatchStrictMath() {
        val r = SplitMix64(77)
        val cases = mutableListOf(0f, -0f, 0.5f, -0.5f, 1f, -1f, 1e-30f, -1e-30f, 2.1e9f, -2.1e9f, 3e9f, -3e9f, 16777217f, -8388607.5f,
            Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        repeat(200_000) { cases += (r.nextFloat() - 0.5f) * (1 shl (it % 32)) }
        for (x in cases) {
            assertEquals(StrictMath.floor(x.toDouble()).toFloat().toRawBits(), floor(x).toRawBits(), "floor $x")
            assertEquals(StrictMath.ceil(x.toDouble()).toFloat().toRawBits(), ceil(x).toRawBits(), "ceil $x")
        }
        assertTrue(floor(Float.NaN).isNaN() && ceil(Float.NaN).isNaN())
    }

    @Test
    fun mainSourcesUseOnlyStrictMathAndSeededRandomness() {
        val root = File("src/main/kotlin")
        val files = root.walk().filter { it.extension == "kt" }.toList()
        assertTrue(files.size > 10, "found the sources at ${root.absolutePath}")
        val allowed = setOf("abs", "min", "max", "PI")
        val bad = mutableListOf<String>()
        for (f in files) for ((n, line) in f.readLines().withIndex()) {
            val where = "${f.relativeTo(root)}:${n + 1}: ${line.trim()}"
            Regex("""kotlin\.math\.(\w+)""").findAll(line).forEach { if (it.groupValues[1] !in allowed) bad += where }
            if (Regex("""(?<![A-Za-z])Math\.""").containsMatchIn(line)) bad += where
            if (Regex("""java\.util\.Random|kotlin\.random|SecureRandom|nanoTime|currentTimeMillis|hashCode\(\)""").containsMatchIn(line)) bad += where
            if (line.contains("android.")) bad += where
        }
        assertEquals(emptyList(), bad)
    }
}
