package dev.iconoclast.render.layer

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
 * - [grain]: one hashed value per lattice cell, no blending: film grain at cell size.
 */
internal object Noise {

    fun value(x: Float, y: Float, seed: Int): Float {
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val tx = fade(x - fx)
        val ty = fade(y - fy)
        val a = lattice(ix, iy, seed)
        val b = lattice(ix + 1, iy, seed)
        val c = lattice(ix, iy + 1, seed)
        val d = lattice(ix + 1, iy + 1, seed)
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

    /** A lattice point's value, 24 bits in [0, 1). */
    fun lattice(ix: Int, iy: Int, seed: Int): Float =
        (lowbias32(ix + lowbias32(iy + lowbias32(seed))) ushr 8).toFloat() / (1 shl 24)

    /** Chris Wellons' lowbias32 (2018), an invertible 32-bit integer hash. */
    fun lowbias32(v: Int): Int {
        var x = v
        x = x xor (x ushr 16)
        x *= 0x7feb352d
        x = x xor (x ushr 15)
        x *= -0x7b935975 // 0x846ca68b
        return x xor (x ushr 16)
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    /** Decorrelates octaves: the golden-ratio constant as a 32-bit int. */
    private const val OCTAVE_SALT = -0x61c88647 // 0x9e3779b9
}
