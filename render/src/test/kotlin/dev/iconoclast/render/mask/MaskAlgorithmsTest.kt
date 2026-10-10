package dev.iconoclast.render.mask

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaskAlgorithmsTest {

    private fun grid(vararg rows: String): BooleanArray =
        BooleanArray(rows.size * rows[0].length) { rows[it / rows[0].length][it % rows[0].length] == '#' }

    @Test
    fun edtMatchesBruteForce() {
        val rnd = Random(7)
        val w = 23
        val h = 17
        repeat(5) { round ->
            val m = BooleanArray(w * h) { rnd.nextInt(40) == 0 }
            if (round == 0) m.fill(false)
            val d = squaredEdt(m, w, h)
            for (p in 0 until w * h) {
                var best = Float.MAX_VALUE
                for (q in 0 until w * h) if (m[q]) {
                    val dx = p % w - q % w
                    val dy = p / w - q / w
                    best = minOf(best, (dx * dx + dy * dy).toFloat())
                }
                if (best == Float.MAX_VALUE) assertTrue(d[p] >= FAR / 2) else assertEquals(best, d[p], "pixel $p")
            }
        }
    }

    @Test
    fun labelsUseFourConnectivity() {
        val m = grid(
            "##..#",
            "##...",
            "..#..",
            ".....",
            "#...#",
        )
        val l = label4(m, 5, 5)
        // Top-left block, the diagonal pixel is its own, plus three single corners.
        assertEquals(5, l.count)
        assertEquals(1, l.labels[0]); assertEquals(1, l.labels[6])
        assertEquals(2, l.labels[4])
        assertEquals(3, l.labels[12])
        val border = touchesBorder(l, 5, 5)
        assertTrue(border[1]); assertTrue(border[2]); assertFalse(border[3])
    }

    @Test
    fun dilationIsARoundDisk() {
        val m = BooleanArray(121).also { it[60] = true }
        assertEquals(13, Morphology.dilate(m, 11, 11, 2f).count { it })
        assertEquals(29, Morphology.dilate(m, 11, 11, 3f).count { it })
    }
}
