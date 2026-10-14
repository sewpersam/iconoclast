package dev.iconoclast.render.layer

import dev.iconoclast.render.Crop
import dev.iconoclast.render.IconSeed
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.math.SplitMix64
import dev.iconoclast.render.math.floor
import dev.iconoclast.render.texture.Texture

/**
 * A [Mapping] resolved for one icon by [Mappings.place]: the scale, rotation and offset it
 * renders at, after its per-icon random shift, turn and zoom. A data class, so it keys the
 * noise cache: two icons that place alike share a field.
 */
data class Placement(val mode: MappingMode, val scale: Float, val rotation: Float, val offset: TextureOffset, val crop: Crop?)

/**
 * Texture mapping, spec 2.3: where each canvas pixel samples a texture or a noise field.
 *
 * A pixel centre (u, v), in canvas units 0 to 1, is taken about the canvas centre, turned by
 * the rotation and scaled by the texels per canvas unit, k:
 * - FIT: the turned texture's bounding box spans the canvas, k = max(box) / scale.
 * - FILL: the turned canvas fits inside the texture, k = min(W, H) / (|cos| + |sin|) / scale.
 * - TILE: the texture's short side spans the canvas, k = min(W, H) / scale, repeating.
 *
 * The per-icon offset (ox, oy) is the icon's one [TextureOffset], StackInput.offset, in
 * [0, 1): by default derived from the slot's seed name. Locked, both are 0.5, the centred
 * patch. FIT and FILL move the canvas window by (o - 0.5) times the room between the window
 * and the texture on each axis: FILL only where the texture is larger, so the patch never
 * leaves the texture; FIT either way, so a texture smaller than the canvas moves inside it
 * and stays whole, and a zoomed-in one shows its own patch. At scale 1 a square texture
 * leaves FILL no room, which is why a new texture layer starts at scale 2. TILE is a wrapped
 * translation by o times the texture size. Noise shifts by o times [NOISE_PERIOD] lattice cells.
 */
internal object Mappings {

    /** Lattice cells across the canvas at scale 1, fBm and ridged. Grain's are the pack's pixels, [noise]. */
    const val NOISE_CELLS = 8f
    /**
     * How far the per-icon offset moves the noise field, in lattice cells. At the smallest
     * scale, 0.25, the canvas spans 32 cells, so no pixel lies more than 32 cells from the
     * offset, turned or not. The top fBm octave (8, frequency 128) then reaches at most
     * (256 + 32) * 128 < 2^16, where floats are spaced 1/256 cell: smooth. At 4096 the
     * spacing was 1/16 cell, and the top octaves stair-stepped.
     */
    const val NOISE_PERIOD = 256f

    /**
     * [mapping] resolved for one icon: its per-icon shift, turn and zoom drawn and applied.
     *
     * The draws are four floats from SplitMix64: the first two the offset, the third the
     * turn, the fourth the zoom. Seeded with [iconSeed] alone when the mapping's seed is 0,
     * so the first two are the icon's own [TextureOffset.of], exactly [offset] when it is
     * not [pinned]; else with iconSeed xor mix64(seed), a new arrangement per seed. A
     * pinned offset, a per-icon override, is used as given. The shift then scales the
     * offset's distance from the centre: 1 keeps it, 0 is the centre, the old locked
     * mapping. Unused amounts leave the mapping's values untouched, so every schema 7
     * mapping places exactly as it did.
     */
    fun place(mapping: Mapping, offset: TextureOffset, iconSeed: Long, pinned: Boolean): Placement {
        val r = SplitMix64(if (mapping.seed == 0) iconSeed else iconSeed xor IconSeed.mix64(mapping.seed.toLong()))
        val ox = r.nextFloat()
        val oy = r.nextFloat()
        val base = if (pinned || mapping.seed == 0) offset else TextureOffset(ox, oy)
        val turn = r.nextFloat()
        val zoom = r.nextFloat()
        val o = when (val s = mapping.randomShift) {
            1f -> base
            0f -> TextureOffset.CENTRE
            else -> TextureOffset(shifted(base.x, s), shifted(base.y, s))
        }
        val rotation = if (mapping.randomRotation == 0f) mapping.rotation else mapping.rotation + (2f * turn - 1f) * mapping.randomRotation
        val scale = if (mapping.randomScale == 0f) mapping.scale
            else mapping.scale * StrictMath.pow(2.0, ((2f * zoom - 1f) * mapping.randomScale).toDouble()).toFloat()
        return Placement(mapping.mode, scale, rotation, o, mapping.crop)
    }

    /** The offset [mapping] uses for an icon whose offset is [offset], as given: its shift applied. */
    fun offset(mapping: Mapping, offset: TextureOffset): TextureOffset = place(mapping, offset, 0L, pinned = true).offset

    /** [texture] at [mapping] placed with [offset] as given, no per-icon turn or zoom drawn from a seed name. */
    fun texture(tex: Texture, mapping: Mapping, offset: TextureOffset, size: Int, need: FloatArray? = null): FloatArray =
        texture(tex, place(mapping, offset, 0L, pinned = true), size, need)

    /** [noise] at [mapping] placed with [offset] as given. */
    fun noise(kind: NoiseKind, octaves: Int, seed: Int, mapping: Mapping, offset: TextureOffset, size: Int, grainCells: Int, need: FloatArray? = null): FloatArray =
        noise(kind, octaves, seed, place(mapping, offset, 0L, pinned = true), size, grainCells, need)

    private fun shifted(o: Float, s: Float): Float = (0.5f + (o - 0.5f) * s).coerceIn(0f, LAST_BELOW_ONE)

    private const val LAST_BELOW_ONE = 0.99999994f

    /** Grain's placement: [scale] at the icon's own offset, unturned. */
    fun grain(scale: Float, offset: TextureOffset) = Placement(MappingMode.TILE, scale, 0f, offset, null)

    /**
     * The texture under every pixel, premultiplied RGBA floats, bilinear. With [need],
     * pixels where it is 0 are skipped and left transparent: nothing will show them.
     * A crop makes the texel rectangle it covers the texture: placed, tiled and clamped
     * as if it were the whole image.
     */
    fun texture(tex: Texture, p: Placement, size: Int, need: FloatArray? = null): FloatArray {
        val stride = tex.width
        val crop = p.crop
        val rx = crop?.let { (it.left * stride).let { v -> floor(v + 0.5f).toInt() }.coerceIn(0, stride - 1) } ?: 0
        val ry = crop?.let { (it.top * tex.height).let { v -> floor(v + 0.5f).toInt() }.coerceIn(0, tex.height - 1) } ?: 0
        val w = crop?.let { (it.right * stride).let { v -> floor(v + 0.5f).toInt() }.coerceIn(rx + 1, stride) - rx } ?: stride
        val h = crop?.let { (it.bottom * tex.height).let { v -> floor(v + 0.5f).toInt() }.coerceIn(ry + 1, tex.height) - ry } ?: tex.height
        val t = turn(p.rotation)
        val ac = kotlin.math.abs(t.c)
        val asn = kotlin.math.abs(t.s)
        val k = when (p.mode) {
            MappingMode.FIT -> maxOf(w * ac + h * asn, w * asn + h * ac) / p.scale
            MappingMode.FILL -> minOf(w, h) / (ac + asn) / p.scale
            MappingMode.TILE -> minOf(w, h) / p.scale
        }
        val (ox, oy) = p.offset
        var cx = w / 2f
        var cy = h / 2f
        when (p.mode) {
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
        val tile = p.mode == MappingMode.TILE
        val px = tex.premultiplied
        val out = FloatArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            if (need != null && need[y * size + x] == 0f) continue
            val u = (x + 0.5f) / size - 0.5f
            val v = (y + 0.5f) / size - 0.5f
            // Texel coordinates within the cropped rectangle.
            val tx = cx + (t.c * u + t.s * v) * k
            val ty = cy + (t.c * v - t.s * u) * k
            if (!tile && (tx < 0f || ty < 0f || tx >= w || ty >= h)) continue
            val fx = tx - 0.5f
            val fy = ty - 0.5f
            val x0f = floor(fx)
            val y0f = floor(fy)
            val wx = fx - x0f
            val wy = fy - y0f
            val x0 = rx + index(x0f.toInt(), w, tile)
            val x1 = rx + index(x0f.toInt() + 1, w, tile)
            val y0 = ry + index(y0f.toInt(), h, tile)
            val y1 = ry + index(y0f.toInt() + 1, h, tile)
            val o = (y * size + x) * 4
            for (c in 0..3) {
                val a = (px[(y0 * stride + x0) * 4 + c].toInt() and 0xFF).toFloat()
                val b = (px[(y0 * stride + x1) * 4 + c].toInt() and 0xFF).toFloat()
                val d = (px[(y1 * stride + x0) * 4 + c].toInt() and 0xFF).toFloat()
                val e = (px[(y1 * stride + x1) * 4 + c].toInt() and 0xFF).toFloat()
                val top = a + (b - a) * wx
                val bottom = d + (e - d) * wx
                out[o + c] = (top + (bottom - top) * wy) / 255f
            }
        }
        return out
    }

    /**
     * The noise field under every pixel, 0 to 1.
     *
     * Grain is one value per lattice cell, and its cells are a fixed fraction of the
     * canvas, 1 / [grainCells] at scale 1: one pack pixel at the output resolution,
     * dev.iconoclast.render.OutputResolution. Point-sampled, a 128 px preview would show
     * each pixel one random cell at full contrast, a coarser and harsher grain than the pack. So grain is box-filtered: each pixel is the area
     * average of the cells under its footprint, [Noise.grainBox], and its per-icon offset
     * is whole cells. Unturned, a pack-size render at scale 1 is then exactly one cell per
     * pixel, and any smaller render is exactly that render box-filtered down. With [need],
     * pixels where it is 0 are skipped and left 0.
     */
    fun noise(kind: NoiseKind, octaves: Int, seed: Int, p: Placement, size: Int, grainCells: Int, need: FloatArray? = null): FloatArray {
        val t = turn(p.rotation)
        val grain = kind == NoiseKind.GRAIN
        val cells = (if (grain) grainCells.toFloat() else NOISE_CELLS) / p.scale
        val (ox, oy) = p.offset
        val bx = if (grain) floor(ox * NOISE_PERIOD) else ox * NOISE_PERIOD
        val by = if (grain) floor(oy * NOISE_PERIOD) else oy * NOISE_PERIOD
        // Half a pixel's footprint, in cells.
        val half = cells / size / 2f
        val out = FloatArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            if (need != null && need[y * size + x] == 0f) continue
            val u = (x + 0.5f) / size - 0.5f
            val v = (y + 0.5f) / size - 0.5f
            val nx = bx + (t.c * u + t.s * v) * cells
            val ny = by + (t.c * v - t.s * u) * cells
            out[y * size + x] = when (kind) {
                NoiseKind.FBM -> Noise.fbm(nx, ny, octaves, seed)
                NoiseKind.RIDGED -> Noise.ridged(nx, ny, octaves, seed)
                NoiseKind.GRAIN -> Noise.grainBox(nx - half, nx + half, ny - half, ny + half, seed)
            }
        }
        return out
    }

    private class Turn(val c: Float, val s: Float)

    private fun turn(rotation: Float): Turn {
        val r = rotation.toDouble() * (StrictMath.PI / 180.0)
        return Turn(StrictMath.cos(r).toFloat(), StrictMath.sin(r).toFloat())
    }

    /** A texel index: wrapped when tiling, clamped to the edge otherwise. */
    private fun index(i: Int, n: Int, tile: Boolean): Int =
        if (tile) ((i % n) + n) % n else i.coerceIn(0, n - 1)
}
