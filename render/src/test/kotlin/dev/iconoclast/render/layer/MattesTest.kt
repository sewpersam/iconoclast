package dev.iconoclast.render.layer

import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MattesTest {

    @Test
    fun levelsThresholdAndInvert() {
        val lum = floatArrayOf(0f, 0.2f, 0.5f, 0.8f)
        assertContentEquals(floatArrayOf(0f, 0f, 0.5f, 1f), Mattes.apply(Matte(black = 0.2f, white = 0.8f), lum, 2))
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 1f), Mattes.apply(Matte(mode = MatteMode.THRESHOLD), lum, 2))
        assertContentEquals(floatArrayOf(1f, 1f, 0f, 0f), Mattes.apply(Matte(mode = MatteMode.THRESHOLD, invert = true), lum, 2))
    }

    /** Feathering a hard edge ramps it symmetrically and keeps the far sides. */
    @Test
    fun featherSoftensAnEdge() {
        val size = 40
        val lum = FloatArray(size * size) { if (it % size < 20) 1f else 0f }
        val m = Mattes.apply(Matte(feather = 0.05f), lum, size)
        val row = 20 * size
        assertEquals(1f, m[row + 2], 1e-6f)
        assertEquals(0f, m[row + 37], 1e-6f)
        assertEquals(1f, m[row + 19] + m[row + 20], 1e-5f)
        assertTrue(m[row + 19] in 0.5f..0.99f)
    }
}
