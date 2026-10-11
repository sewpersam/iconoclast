package dev.iconoclast.render.mask

import dev.iconoclast.render.math.sqrt

/**
 * Signed distance to the edge of a coverage mask, in pixels, positive inside. The bevel
 * reads its surface from it, so it must be smooth: lit by a low light, every step in the
 * field shows as a band.
 *
 * [binary] is the plain construction, the inside EDT minus the outside EDT of the mask
 * thresholded at one half. Its edge is the pixel staircase, so near the edge the field
 * moves in steps of up to a pixel and its gradient turns in jumps; SignedDistanceTest
 * measures the banding. It is kept as that reference.
 *
 * [of] is the anti-aliased Euclidean distance transform after Gustavson and Strand, "Anti-
 * aliased Euclidean distance transform", Pattern Recognition Letters 32 (2011). Each edge
 * pixel, one with partial coverage or a hard 0 to 1 step to a neighbour, gets the
 * point where the edge crosses it: the coverage gradient (their Sobel-like operator with
 * sqrt 2 weights) gives the edge's normal, and their `edgedf` gives its distance from the
 * pixel centre for that coverage, treating the edge as straight within the pixel. Every
 * pixel then takes its distance to the nearest such edge point. Nearest is found in two
 * steps: Felzenszwalb's exact EDT with the argmin carried along picks the nearest edge
 * pixel, then the edge points of every edge pixel within two pixels of it are tried as
 * well: along a curve, the pixel whose centre is nearest is often a faint one whose edge
 * point lies deeper, and a neighbour along the edge holds the nearer point. Plain float arithmetic and StrictMath sqrt.
 */
object SignedDistance {

    /** Distance given where the mask has no edge at all: everything inside or everything out. */
    const val NO_EDGE = 1e6f

    /** The reference: inside EDT minus outside EDT on the mask thresholded at 0.5, each a half pixel to the boundary. */
    fun binary(cov: FloatArray, w: Int, h: Int): FloatArray {
        val inside = BooleanArray(w * h) { cov[it] >= 0.5f }
        val toOutside = squaredEdt(BooleanArray(w * h) { !inside[it] }, w, h)
        val toInside = squaredEdt(inside, w, h)
        return FloatArray(w * h) {
            if (inside[it]) {
                if (toOutside[it] >= FAR) NO_EDGE else sqrt(toOutside[it]) - 0.5f
            } else {
                if (toInside[it] >= FAR) -NO_EDGE else 0.5f - sqrt(toInside[it])
            }
        }
    }

    /** The anti-aliased signed distance of [cov], [w] x [h], positive inside. */
    fun of(cov: FloatArray, w: Int, h: Int): FloatArray {
        val n = w * h
        // Edge points: offsets (ex, ey) from each edge pixel's centre to where the edge crosses it.
        val edge = BooleanArray(n)
        val ex = FloatArray(n)
        val ey = FloatArray(n)
        var any = false
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = cov[i]
            if (!isEdge(cov, w, h, x, y, a)) continue
            any = true
            edge[i] = true
            var gx = -at(cov, w, h, x - 1, y - 1) - SQRT2 * at(cov, w, h, x - 1, y) - at(cov, w, h, x - 1, y + 1) +
                at(cov, w, h, x + 1, y - 1) + SQRT2 * at(cov, w, h, x + 1, y) + at(cov, w, h, x + 1, y + 1)
            var gy = -at(cov, w, h, x - 1, y - 1) - SQRT2 * at(cov, w, h, x, y - 1) - at(cov, w, h, x + 1, y - 1) +
                at(cov, w, h, x - 1, y + 1) + SQRT2 * at(cov, w, h, x, y + 1) + at(cov, w, h, x + 1, y + 1)
            val g = sqrt(gx * gx + gy * gy)
            if (g == 0f) continue // no direction: the centre is the best guess
            gx /= g
            gy /= g
            val df = edgeDf(gx, gy, a)
            ex[i] = gx * df
            ey[i] = gy * df
        }
        if (!any) return FloatArray(n) { if (cov[it] >= 0.5f) NO_EDGE else -NO_EDGE }

        val nearest = nearestFeature(edge, w, h)
        val out = FloatArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            var best = Float.MAX_VALUE
            val p = nearest[y * w + x]
            val px0 = p % w
            val py0 = p / w
            for (qy in maxOf(0, py0 - REACH)..minOf(h - 1, py0 + REACH)) for (qx in maxOf(0, px0 - REACH)..minOf(w - 1, px0 + REACH)) {
                val q = qy * w + qx
                if (!edge[q]) continue
                val ddx = qx + ex[q] - x
                val ddy = qy + ey[q] - y
                val d2 = ddx * ddx + ddy * ddy
                if (d2 < best) best = d2
            }
            val d = sqrt(best)
            out[y * w + x] = if (cov[y * w + x] >= 0.5f) d else -d
        }
        return out
    }

    /**
     * Partial coverage; or full coverage beside empty, or empty beside full: a hard edge
     * on the pixel boundary with no partial pixel to place it.
     */
    private fun isEdge(cov: FloatArray, w: Int, h: Int, x: Int, y: Int, a: Float): Boolean {
        if (a > 0f && a < 1f) return true
        val other = 1f - a
        return (x > 0 && cov[y * w + x - 1] == other) || (x < w - 1 && cov[y * w + x + 1] == other) ||
            (y > 0 && cov[(y - 1) * w + x] == other) || (y < h - 1 && cov[(y + 1) * w + x] == other)
    }

    /** Coverage with the grid's edges clamped. */
    private fun at(cov: FloatArray, w: Int, h: Int, x: Int, y: Int): Float =
        cov[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]

    /**
     * Gustavson and Strand's edgedf: the distance from a pixel centre with coverage [a] to
     * a straight edge with unit normal (gx, gy) pointing inward, positive when the centre
     * is outside. Exact for a straight edge through a square pixel.
     */
    private fun edgeDf(gxIn: Float, gyIn: Float, a: Float): Float {
        if (gxIn == 0f || gyIn == 0f) return 0.5f - a
        var gx = kotlin.math.abs(gxIn)
        var gy = kotlin.math.abs(gyIn)
        if (gx < gy) { val t = gx; gx = gy; gy = t }
        val a1 = 0.5f * gy / gx
        return when {
            a < a1 -> 0.5f * (gx + gy) - sqrt(2f * gx * gy * a)
            a < 1f - a1 -> (0.5f - a) * gx
            else -> -0.5f * (gx + gy) + sqrt(2f * gx * gy * (1f - a))
        }
    }

    /**
     * For every pixel, the index of the nearest [feature] pixel by Euclidean distance between
     * centres: Felzenszwalb and Huttenlocher's transform with the parabola roots kept. At
     * least one pixel must be a feature.
     */
    internal fun nearestFeature(feature: BooleanArray, w: Int, h: Int): IntArray {
        val m = maxOf(w, h)
        val f = FloatArray(m)
        val d = FloatArray(m)
        val arg = IntArray(m)
        val v = IntArray(m)
        val z = FloatArray(m + 1)
        // Columns: the nearest feature row in each column, and its squared distance.
        val g = FloatArray(w * h)
        val row = IntArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) f[y] = if (feature[y * w + x]) 0f else FAR
            envelope(f, h, d, arg, v, z)
            for (y in 0 until h) {
                g[y * w + x] = d[y]
                row[y * w + x] = arg[y]
            }
        }
        // Rows: the nearest column, whose own nearest row completes the feature.
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) f[x] = g[y * w + x]
            envelope(f, w, d, arg, v, z)
            for (x in 0 until w) {
                val col = arg[x]
                out[y * w + x] = row[y * w + col] * w + col
            }
        }
        return out
    }

    /** Lower envelope of parabolas rooted at (q, f[q]): [d] the minimum, [arg] its root, at 0 until [n]. */
    private fun envelope(f: FloatArray, n: Int, d: FloatArray, arg: IntArray, v: IntArray, z: FloatArray) {
        var k = 0
        v[0] = 0
        z[0] = Float.NEGATIVE_INFINITY
        z[1] = Float.POSITIVE_INFINITY
        for (q in 1 until n) {
            var s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
            while (s <= z[k]) {
                k--
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.POSITIVE_INFINITY
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val p = v[k]
            arg[q] = p
            d[q] = (q - p) * (q - p) + f[p]
        }
    }

    private const val SQRT2 = 1.4142135f

    /** Edge pixels within this many pixels of the nearest one are tried for a nearer edge point. */
    private const val REACH = 2
}
