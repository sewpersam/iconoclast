package dev.iconoclast.render.layer

import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor

/**
 * Procedural noise, all in [0, 1]. Plain float and integer arithmetic, so a seed gives the
 * same field everywhere.
 *
 * - [value]: value noise. Each integer lattice point gets a hashed value; between them the
 *   values are blended with Perlin's quintic fade, 6t^5 - 15t^4 + 10t^3 ("Improving Noise",
 *   SIGGRAPH 2002). The lattice hash is Chris Wellons' lowbias32 integer mix, chained over
 *   x, y and the seed.
 * - [fbm]: fractional Brownian motion, Musgrave's sum of octaves (Ebert et al., "Texturing
 *   and Modeling: A Procedural Approach", 3rd ed., ch. 16): lacunarity 2, gain 0.5,
 *   normalised by the sum of the amplitudes.
 * - [ridged]: the same sum over ridges, (1 - |2n - 1|)^2 per octave, a simplified
 *   ridged multifractal without Musgrave's weight feedback. Veins where the noise
 *   crosses its middle.
 * - [grain]: one hashed value per lattice cell, no blending: film grain at cell size;
 *   [grainBox] its area average over a pixel's footprint, how it is sampled.
 */
internal object Noise {

    fun value(x: Float, y: Float, seed: Int): Float {
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val tx = fade(x - fx)
        val ty = fade(y - fy)
        // lattice() chains the hash over seed, y, x; the seed and row hashes are shared.
        val sh = lowbias32(seed)
        val h0 = lowbias32(iy + sh)
        val h1 = lowbias32(iy + 1 + sh)
        val a = cell(ix, h0)
        val b = cell(ix + 1, h0)
        val c = cell(ix, h1)
        val d = cell(ix + 1, h1)
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    fun fbm(x: Float, y: Float, octaves: Int, seed: Int): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var f = 1f
        for (o in 0 until octaves) {
            sum += amp * value(x * f, y * f, seed + o * OCTAVE_SALT)
            norm += amp
            amp *= 0.5f
            f *= 2f
        }
        return sum / norm
    }

    fun ridged(x: Float, y: Float, octaves: Int, seed: Int): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var f = 1f
        for (o in 0 until octaves) {
            val n = value(x * f, y * f, seed + o * OCTAVE_SALT)
            val r = 1f - kotlin.math.abs(2f * n - 1f)
            sum += amp * r * r
            norm += amp
            amp *= 0.5f
            f *= 2f
        }
        return sum / norm
    }

    fun grain(x: Float, y: Float, seed: Int): Float = lattice(floor(x).toInt(), floor(y).toInt(), seed)

    /**
     * The area average of [grain] over the box [x0, x1] x [y0, y1]: each cell's value
     * weighted by the fraction of the box it covers. Separable, so the weights are per
     * column and per row. A box inside one cell gives that cell's value exactly.
     */
    fun grainBox(x0: Float, x1: Float, y0: Float, y1: Float, seed: Int): Float {
        // Cells the box overlaps by more than [SLIVER]: float rounding puts a box that
        // spans exactly one cell a hair over its edges, and slivers that thin are dropped.
        val i0 = floor(x0 + SLIVER).toInt()
        val i1 = maxOf(i0, ceil(x1 - SLIVER).toInt() - 1)
        val j0 = floor(y0 + SLIVER).toInt()
        val j1 = maxOf(j0, ceil(y1 - SLIVER).toInt() - 1)
        if (i0 == i1 && j0 == j1) return lattice(i0, j0, seed)
        // Weights over the kept cells, normalised to sum 1.
        val w = minOf(x1, (i1 + 1).toFloat()) - maxOf(x0, i0.toFloat())
        val h = minOf(y1, (j1 + 1).toFloat()) - maxOf(y0, j0.toFloat())
        val sh = lowbias32(seed)
        var sum = 0f
        for (j in j0..j1) {
            val wy = (minOf(y1, (j + 1).toFloat()) - maxOf(y0, j.toFloat())) / h
            if (wy <= 0f) continue
            val rowHash = lowbias32(j + sh)
            var row = 0f
            for (i in i0..i1) {
                val wx = (minOf(x1, (i + 1).toFloat()) - maxOf(x0, i.toFloat())) / w
                if (wx <= 0f) continue
                row += wx * cell(i, rowHash)
            }
            sum += wy * row
        }
        return sum
    }

    /** A lattice point's value, 24 bits in [0, 1). */
    fun lattice(ix: Int, iy: Int, seed: Int): Float = cell(ix, lowbias32(iy + lowbias32(seed)))

    /** [lattice] with the row's hash, `lowbias32(iy + lowbias32(seed))`, already taken. */
    private fun cell(ix: Int, row: Int): Float = (lowbias32(ix + row) ushr 8).toFloat() / (1 shl 24)

    /** Chris Wellons' lowbias32 (2018), an invertible 32-bit integer hash. */
    fun lowbias32(v: Int): Int {
        var x = v
        x = x xor (x ushr 16)
        x *= 0x7feb352d
        x = x xor (x ushr 15)
        x *= -0x7b935975 // 0x846ca68b
        return x xor (x ushr 16)
    }

    /** A cell overlap thinner than this, in cells, is float rounding, not area. */
    private const val SLIVER = 1e-3f

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    /** Decorrelates octaves: the golden-ratio constant as a 32-bit int. */
    const val OCTAVE_SALT = -0x61c88647 // 0x9e3779b9
}
