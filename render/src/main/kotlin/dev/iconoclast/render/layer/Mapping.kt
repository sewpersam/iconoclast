package dev.iconoclast.render.layer

import dev.iconoclast.render.IconSeed
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.math.SplitMix64
import dev.iconoclast.render.math.floor
import dev.iconoclast.render.texture.Texture

/**
 * Texture mapping, spec 2.3: where each canvas pixel samples a texture or a noise field.
 *
 * A pixel centre (u, v), in canvas units 0 to 1, is taken about the canvas centre, turned by
 * the rotation and scaled by the texels per canvas unit, k:
 * - FIT: the turned texture's bounding box spans the canvas, k = max(box) / scale.
 * - FILL: the turned canvas fits inside the texture, k = min(W, H) / (|cos| + |sin|) / scale.
 * - TILE: the texture's short side spans the canvas, k = min(W, H) / scale, repeating.
 *
 * The per-icon offset is two uniform numbers (ox, oy) in [0, 1): the first two outputs of
 * SplitMix64 seeded with [IconSeed.of] the drawable name. Locked, both are 0.5, the centred
 * patch. FIT and FILL move the canvas window by (o - 0.5) times the room between the window
 * and the texture on each axis: FILL only where the texture is larger, so the patch never
 * leaves the texture; FIT either way, so a texture smaller than the canvas moves inside it
 * and stays whole, and a zoomed-in one shows its own patch. At scale 1 a square texture
 * leaves FILL no room, which is why a new texture layer starts at scale 2. TILE is a wrapped
 * translation by o times the texture size. Noise shifts by o times [NOISE_PERIOD] lattice cells.
 */
internal object Mappings {

    /** Lattice cells across the canvas at scale 1: fBm and ridged, then grain. */
    const val NOISE_CELLS = 8f
    const val GRAIN_CELLS = 432f
    /**
     * How far the per-icon offset moves the noise field, in lattice cells. The top fBm
     * octave (8, frequency 128) at the smallest scale (0.25, 32 cells) reaches
     * (256 + 16) * 128 < 2^16, where floats are spaced 1/256 cell: smooth. At 4096 the
     * spacing was 1/16 cell, and the top octaves stair-stepped.
     */
    const val NOISE_PERIOD = 256f

    fun offset(mapping: Mapping, drawable: String): Pair<Float, Float> {
        if (mapping.lockOffset) return 0.5f to 0.5f
        val r = SplitMix64(IconSeed.of(drawable))
        return r.nextFloat() to r.nextFloat()
    }

    /** The texture under every pixel, premultiplied RGBA floats, bilinear. */
    fun texture(tex: Texture, mapping: Mapping, drawable: String, size: Int): FloatArray {
        val w = tex.width
        val h = tex.height
        val t = turn(mapping)
        val ac = kotlin.math.abs(t.c)
        val asn = kotlin.math.abs(t.s)
        val k = when (mapping.mode) {
            MappingMode.FIT -> maxOf(w * ac + h * asn, w * asn + h * ac) / mapping.scale
            MappingMode.FILL -> minOf(w, h) / (ac + asn) / mapping.scale
            MappingMode.TILE -> minOf(w, h) / mapping.scale
        }
        val (ox, oy) = offset(mapping, drawable)
        var cx = w / 2f
        var cy = h / 2f
        when (mapping.mode) {
            MappingMode.FIT -> {
                val extent = k * (ac + asn)
                cx += (ox - 0.5f) * (w - extent)
                cy += (oy - 0.5f) * (h - extent)
            }
            MappingMode.FILL -> {
                val extent = k * (ac + asn)
                cx += (ox - 0.5f) * maxOf(0f, w - extent)
                cy += (oy - 0.5f) * maxOf(0f, h - extent)
            }
            MappingMode.TILE -> {
                cx += ox * w
                cy += oy * h
            }
        }
        val tile = mapping.mode == MappingMode.TILE
        val px = tex.premultiplied
        val out = FloatArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + 0.5f) / size - 0.5f
            val v = (y + 0.5f) / size - 0.5f
            val tx = cx + (t.c * u + t.s * v) * k
            val ty = cy + (t.c * v - t.s * u) * k
            if (!tile && (tx < 0f || ty < 0f || tx >= w || ty >= h)) continue
            val fx = tx - 0.5f
            val fy = ty - 0.5f
            val x0f = floor(fx)
            val y0f = floor(fy)
            val wx = fx - x0f
            val wy = fy - y0f
            val x0 = index(x0f.toInt(), w, tile)
            val x1 = index(x0f.toInt() + 1, w, tile)
            val y0 = index(y0f.toInt(), h, tile)
            val y1 = index(y0f.toInt() + 1, h, tile)
            val o = (y * size + x) * 4
            for (c in 0..3) {
                val a = (px[(y0 * w + x0) * 4 + c].toInt() and 0xFF).toFloat()
                val b = (px[(y0 * w + x1) * 4 + c].toInt() and 0xFF).toFloat()
                val d = (px[(y1 * w + x0) * 4 + c].toInt() and 0xFF).toFloat()
                val e = (px[(y1 * w + x1) * 4 + c].toInt() and 0xFF).toFloat()
                val top = a + (b - a) * wx
                val bottom = d + (e - d) * wx
                out[o + c] = (top + (bottom - top) * wy) / 255f
            }
        }
        return out
    }

    /** The noise field under every pixel, 0 to 1. */
    fun noise(kind: NoiseKind, octaves: Int, seed: Int, mapping: Mapping, drawable: String, size: Int): FloatArray {
        val t = turn(mapping)
        val cells = (if (kind == NoiseKind.GRAIN) GRAIN_CELLS else NOISE_CELLS) / mapping.scale
        val (ox, oy) = offset(mapping, drawable)
        val bx = ox * NOISE_PERIOD
        val by = oy * NOISE_PERIOD
        val out = FloatArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + 0.5f) / size - 0.5f
            val v = (y + 0.5f) / size - 0.5f
            val nx = bx + (t.c * u + t.s * v) * cells
            val ny = by + (t.c * v - t.s * u) * cells
            out[y * size + x] = when (kind) {
                NoiseKind.FBM -> Noise.fbm(nx, ny, octaves, seed)
                NoiseKind.RIDGED -> Noise.ridged(nx, ny, octaves, seed)
                NoiseKind.GRAIN -> Noise.grain(nx, ny, seed)
            }
        }
        return out
    }

    private class Turn(val c: Float, val s: Float)

    private fun turn(mapping: Mapping): Turn {
        val r = mapping.rotation.toDouble() * (StrictMath.PI / 180.0)
        return Turn(StrictMath.cos(r).toFloat(), StrictMath.sin(r).toFloat())
    }

    /** A texel index: wrapped when tiling, clamped to the edge otherwise. */
    private fun index(i: Int, n: Int, tile: Boolean): Int =
        if (tile) ((i % n) + n) % n else i.coerceIn(0, n - 1)
}
