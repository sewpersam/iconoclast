package dev.iconoclast.render.texture

import dev.iconoclast.render.layer.Noise

/**
 * The brush textures that ship with the renderer, for streak breakup, spec 2.3. Each is
 * generated here from seeded noise, so no image file ships: [texture] builds the pixels
 * on first use. A brush is named by the SHA-256 of its canonical PNG like any texture,
 * so themes refer to it the same way; the hashes are constants, and BuiltInBrushesTest
 * regenerates every brush, encodes it canonically and checks its hash. A built-in brush
 * is always present: the store answers for it without a file, and a theme export does not
 * carry it.
 *
 * The x axis runs along the streak: Breakup stretches it there. All are opaque grey,
 * [SIDE] px square, and tile.
 */
object BuiltInBrushes {
    const val SIDE = 256

    /** A built-in brush: its name for the UI and its hash. */
    class Brush internal constructor(val name: String, val sha256: String, internal val pixels: () -> ByteArray)

    /** Fine bristle lines across the streak, each with its own strength. */
    val BRISTLE = Brush("Bristle", "edd5479ea2f30e4f82555c5337e7b12b68bc462d98cce2c86568fa87ac5196cc") { bristle() }

    /** Bristles that skip: lines broken along their length where the brush ran dry. */
    val DRY = Brush("Dry brush", "529541caaef2a89958423ffa5e19474d7e8ef76a41c142e0bfcbb6834dd3bf30") { dry() }

    /** Soft, blotchy chalk: noise at two scales, no direction. */
    val CHALK = Brush("Chalk", "01efda6942f1af4e3de7310633581f8e06357ebc32dfbadcc12d2c53fc38bdb2") { chalk() }

    val ALL: List<Brush> = listOf(BRISTLE, DRY, CHALK)

    private val byHash: Map<String, Brush> = ALL.associateBy { it.sha256 }

    fun isBuiltIn(hash: String): Boolean = hash in byHash

    private val textures = HashMap<String, Texture>()

    /** The decoded brush, built on first use and kept; null for any other hash. */
    fun texture(hash: String): Texture? {
        val brush = byHash[hash] ?: return null
        synchronized(textures) {
            return textures.getOrPut(hash) { Texture(SIDE, SIDE, brush.pixels()) }
        }
    }

    /** The canonical PNG of a brush: what its hash is of. */
    fun png(hash: String): ByteArray? = texture(hash)?.let { TextureStore.encode(it.width, it.height, it.premultiplied) }

    /** Grey levels, 0 to 1, as opaque RGBA. */
    private fun grey(v: (Int, Int) -> Float): ByteArray {
        val px = ByteArray(SIDE * SIDE * 4)
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val g = (v(x, y).coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            val o = (y * SIDE + x) * 4
            px[o] = g; px[o + 1] = g; px[o + 2] = g; px[o + 3] = -1
        }
        return px
    }

    /**
     * A row's bristle strength: value noise across the brush at 64 and 16 cells, the
     * lattice wrapped every [SIDE] px so the brush tiles. Along the row it drifts slowly.
     */
    private fun bristles(x: Int, y: Int, seed: Int): Float {
        val across = 0.7f * wrapped(y / 4f, 64, seed) + 0.3f * wrapped(y / 16f, 16, seed + 1)
        val along = wrapped(x / 64f + y * 0.375f, 4, seed + 2)
        val s = across * (0.75f + 0.25f * along)
        return s * s * 1.6f
    }

    private fun bristle() = grey { x, y -> bristles(x, y, 11) }

    private fun dry() = grey { x, y ->
        // Each 4 px band of bristles skips at its own phase along the stroke.
        val phase = Noise.lattice(y / 4, 0, 29) * 8f
        val skip = wrapped(x / 32f + phase, 8, 23) * 0.7f + wrapped(x / 8f + phase * 4f, 32, 31) * 0.3f
        bristles(x, y, 17) * if (skip > 0.45f) 1f else (skip / 0.45f).let { it * it * it }
    }

    private fun chalk() = grey { x, y ->
        0.55f * wrapped2(x / 32f, y / 32f, 8, 41) + 0.3f * wrapped2(x / 8f, y / 8f, 32, 43) + 0.15f * (Noise.lattice(x, y, 47))
    }

    /** 1D value noise with the lattice wrapped every [period] cells. */
    private fun wrapped(t: Float, period: Int, seed: Int): Float = wrapped2(t, 0f, period, seed)

    /** 2D value noise with both lattice axes wrapped every [period] cells. */
    private fun wrapped2(x: Float, y: Float, period: Int, seed: Int): Float {
        val ix = x.toInt()
        val iy = y.toInt()
        val fx = fade(x - ix)
        val fy = fade(y - iy)
        fun l(i: Int, j: Int) = Noise.lattice(i % period, j % period, seed)
        val top = l(ix, iy) + (l(ix + 1, iy) - l(ix, iy)) * fx
        val bottom = l(ix, iy + 1) + (l(ix + 1, iy + 1) - l(ix, iy + 1)) * fx
        return top + (bottom - top) * fy
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)
}
