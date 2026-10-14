package dev.iconoclast.render.layer

import dev.iconoclast.render.CellFeature
import dev.iconoclast.render.Pattern
import dev.iconoclast.render.WaveKind
import dev.iconoclast.render.math.floor
import dev.iconoclast.render.math.sqrt

/**
 * The three pattern generators of schema 9 at one point, 0 to 1, after Blender's Noise,
 * Voronoi and Wave textures (Blender manual, Shader nodes, Textures). Coordinates are in
 * cells: one unit is the pattern's size. Plain float arithmetic and StrictMath, so a seed
 * gives the same field everywhere.
 *
 * Noise at stretch 1, distortion 0, roughness 0.5 and whole detail d is, operation for
 * operation, schema 8's fBm (or ridged) of d + 1 octaves, layer/Noise: those themes keep
 * their bytes.
 */
internal object PatternField {

    fun noise(p: Pattern.Noise, x0: Float, y0: Float, seed: Int): Float {
        var x = x0
        var y = y0
        if (p.stretch != 1f) {
            // Pull the field along the stretch angle: that axis is read slower.
            val a = p.stretchAngle.toDouble() * (StrictMath.PI / 180.0)
            val c = StrictMath.cos(a).toFloat()
            val s = StrictMath.sin(a).toFloat()
            val along = (c * x + s * y) / p.stretch
            val across = c * y - s * x
            x = along
            y = across
        }
        if (p.distortion > 0f) {
            // Blender's distortion: the coordinates pushed by noise of themselves.
            val dx = (Noise.value(x + DX, y + DY, seed xor WARP_X) * 2f - 1f) * p.distortion
            val dy = (Noise.value(x - DY, y + DX, seed xor WARP_Y) * 2f - 1f) * p.distortion
            x += dx
            y += dy
        }
        return fractal(x, y, p.detail, p.roughness, p.ridges, seed)
    }

    /**
     * Octaves at lacunarity 2, gain [roughness], normalised by the sum of the amplitudes; a
     * fractional [detail] blends one more octave in, as Blender's fractal noise does.
     */
    fun fractal(x: Float, y: Float, detail: Float, roughness: Float, ridges: Boolean, seed: Int): Float {
        val whole = detail.toInt()
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var f = 1f
        for (o in 0..whole) {
            sum += amp * octave(x * f, y * f, seed + o * Noise.OCTAVE_SALT, ridges)
            norm += amp
            amp *= roughness
            f *= 2f
        }
        val rmd = detail - whole
        if (rmd == 0f) return sum / norm
        val more = sum + amp * octave(x * f, y * f, seed + (whole + 1) * Noise.OCTAVE_SALT, ridges)
        val a = sum / norm
        val b = if (norm + amp > 0f) more / (norm + amp) else a
        return a + (b - a) * rmd
    }

    /**
     * One octave: the value noise, or its ridge (1 - |2n - 1|)^2. Schema 8 summed amp r r;
     * amp is a power of two at roughness 0.5, so amp (r r) rounds the same.
     */
    private fun octave(x: Float, y: Float, seed: Int, ridges: Boolean): Float {
        val n = Noise.value(x, y, seed)
        if (!ridges) return n
        val r = 1f - kotlin.math.abs(2f * n - 1f)
        return r * r
    }

    /**
     * Voronoi F1 and F2 over the 3 x 3 cells around the point, Euclidean. Each cell's point
     * sits at its centre moved by up to half a cell times [Pattern.Cells.randomness].
     */
    fun cells(p: Pattern.Cells, x: Float, y: Float, seed: Int): Float {
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        var d1 = Float.MAX_VALUE
        var d2 = Float.MAX_VALUE
        var cx1 = 0
        var cy1 = 0
        for (dj in -1..1) for (di in -1..1) {
            val cx = ix + di
            val cy = iy + dj
            val px = cx + 0.5f + (Noise.lattice(cx, cy, seed xor POINT_X) - 0.5f) * p.randomness
            val py = cy + 0.5f + (Noise.lattice(cx, cy, seed xor POINT_Y) - 0.5f) * p.randomness
            val ex = px - x
            val ey = py - y
            val d = sqrt(ex * ex + ey * ey)
            if (d < d1) {
                d2 = d1
                d1 = d
                cx1 = cx
                cy1 = cy
            } else if (d < d2) {
                d2 = d
            }
        }
        return when (p.feature) {
            CellFeature.CELLS -> Noise.lattice(cx1, cy1, seed)
            // Half of F2 - F1 is about the distance to the border; a soft line of edgeWidth.
            CellFeature.EDGES -> 1f - smoothstep(p.edgeWidth * 0.5f, p.edgeWidth * 0.5f + EDGE_SOFT, (d2 - d1) * 0.5f)
            CellFeature.CRACKLE -> (d2 - d1).coerceIn(0f, 1f)
        }
    }

    /**
     * Blender's Wave texture, sine profile `0.5 - 0.5 cos(2 pi phase)`: the phase is x in
     * periods for bands, the distance from (0, 0) for rings, plus distortion times signed
     * fractal noise of [Pattern.Waves.detail].
     */
    fun waves(p: Pattern.Waves, x: Float, y: Float, seed: Int): Float {
        var phase = if (p.kind == WaveKind.BANDS) x else sqrt(x * x + y * y)
        if (p.distortion > 0f) phase += p.distortion * WAVE_BEND * (fractal(x * WAVE_NOISE, y * WAVE_NOISE, p.detail, 0.5f, false, seed) * 2f - 1f)
        return (0.5 - 0.5 * StrictMath.cos(2.0 * StrictMath.PI * phase)).toFloat()
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Distortion noise: offsets that decorrelate it from the field, and its seeds. */
    private const val DX = 5.2f
    private const val DY = 1.3f
    private const val WARP_X = 0x2545F491
    private const val WARP_Y = 0x68E31DA4
    private const val POINT_X = 0x1B873593
    private const val POINT_Y = 0x5BD1E995
    /** An edge line's anti-aliasing ramp, in cells. */
    private const val EDGE_SOFT = 0.03f
    /** Distortion noise cells per wave period, and periods of bend per unit of distortion. */
    private const val WAVE_NOISE = 0.35f
    private const val WAVE_BEND = 0.5f
}
