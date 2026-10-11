package dev.iconoclast.render.raster

import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor
import kotlin.math.max
import kotlin.math.min

/** Vertical samples per pixel row. Horizontal coverage is exact within each sample row. */
const val SUB_ROWS = 16

/**
 * Line edges in pixel space, the input to [fillCoverage]. Each edge carries a winding
 * contribution of +1 or -1. Horizontal edges are dropped, they cross no sample row.
 */
class EdgeList {
    internal var x0 = FloatArray(64); private set
    internal var y0 = FloatArray(64); private set
    internal var x1 = FloatArray(64); private set
    internal var y1 = FloatArray(64); private set
    internal var dir = IntArray(64); private set
    var count = 0; private set
    var minX = Float.POSITIVE_INFINITY; private set
    var minY = Float.POSITIVE_INFINITY; private set
    var maxX = Float.NEGATIVE_INFINITY; private set
    var maxY = Float.NEGATIVE_INFINITY; private set

    val isEmpty: Boolean get() = count == 0

    /** Adds the edge from (ax, ay) to (bx, by). [sign] flips its winding. */
    fun addLine(ax: Float, ay: Float, bx: Float, by: Float, sign: Int = 1) {
        if (ay == by) return
        if (count == x0.size) grow()
        // Store with y0 < y1; dir records the original direction.
        if (ay < by) {
            x0[count] = ax; y0[count] = ay; x1[count] = bx; y1[count] = by; dir[count] = sign
        } else {
            x0[count] = bx; y0[count] = by; x1[count] = ax; y1[count] = ay; dir[count] = -sign
        }
        count++
        minX = min(minX, min(ax, bx)); maxX = max(maxX, max(ax, bx))
        minY = min(minY, min(ay, by)); maxY = max(maxY, max(ay, by))
    }

    /** Adds a closed polygon from [n] points in [xy] as x,y pairs. */
    fun addPolygon(xy: FloatArray, n: Int = xy.size / 2, sign: Int = 1) {
        if (n < 2) return
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            addLine(xy[2 * i], xy[2 * i + 1], xy[2 * j], xy[2 * j + 1], sign)
        }
    }

    /**
     * Adds a closed polygon oriented so its interior winds +1, whatever the order of its
     * points. Stroke pieces go in this way, so a nonzero fill of all of them is their union.
     */
    fun addPositive(xy: FloatArray, n: Int = xy.size / 2) {
        val area = signedArea(xy, n)
        if (area == 0.0) return
        addPolygon(xy, n, if (area > 0) 1 else -1)
    }

    fun clear() {
        count = 0
        minX = Float.POSITIVE_INFINITY; minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY; maxY = Float.NEGATIVE_INFINITY
    }

    private fun grow() {
        val n = x0.size * 2
        x0 = x0.copyOf(n); y0 = y0.copyOf(n); x1 = x1.copyOf(n); y1 = y1.copyOf(n); dir = dir.copyOf(n)
    }
}

/**
 * Twice the signed area of a polygon, positive for counter-clockwise in y-up axes. Only
 * the sign is used.
 */
internal fun signedArea(xy: FloatArray, n: Int): Double {
    var a = 0.0
    for (i in 0 until n) {
        val j = if (i + 1 == n) 0 else i + 1
        a += xy[2 * i].toDouble() * xy[2 * j + 1] - xy[2 * j].toDouble() * xy[2 * i + 1]
    }
    return a
}

/**
 * Scan-converts [edges] and composites the coverage into [target], a [width] x [height]
 * row-major mask of values 0 to 1, with source-over: t + c - t * c.
 *
 * Each pixel row is sampled at [SUB_ROWS] evenly spaced rows. On each sample row the
 * crossings are sorted, wound with the nonzero or even-odd rule, and every inside span
 * adds its exact horizontal overlap with each pixel. Single precision IEEE arithmetic
 * only, in a fixed order, so the result is the same on every JVM and on ART.
 */
fun fillCoverage(edges: EdgeList, evenOdd: Boolean, width: Int, height: Int, target: FloatArray) {
    if (edges.isEmpty) return
    val rowStart = max(0, floor(edges.minY).toInt())
    val rowEnd = min(height, ceil(edges.maxY).toInt())
    if (rowStart >= rowEnd) return
    val colStart = max(0, floor(edges.minX).toInt())
    val colEnd = min(width, ceil(edges.maxX).toInt())
    if (colStart >= colEnd) return

    // Edges sorted by top y. Ties keep insertion order, so the walk is deterministic.
    val n = edges.count
    val order = (0 until n).sortedWith(compareBy<Int> { edges.y0[it] }).toIntArray()
    val active = IntArray(n)
    var activeCount = 0
    var next = 0
    val xs = FloatArray(n)
    val ws = IntArray(n)
    // Difference array over the column range; prefix sums give the pixel coverage.
    val diff = FloatArray(colEnd - colStart + 2)
    val weight = 1f / SUB_ROWS

    for (row in rowStart until rowEnd) {
        diff.fill(0f)
        var any = false
        for (s in 0 until SUB_ROWS) {
            val sy = row + (s + 0.5f) / SUB_ROWS
            while (next < n && edges.y0[order[next]] <= sy) active[activeCount++] = order[next++]
            // Collect crossings and drop finished edges, keeping the active order stable.
            var k = 0
            var m = 0
            for (a in 0 until activeCount) {
                val e = active[a]
                if (edges.y1[e] <= sy) continue
                active[k++] = e
                if (edges.y0[e] > sy) continue
                val t = (sy - edges.y0[e]) / (edges.y1[e] - edges.y0[e])
                val x = edges.x0[e] + t * (edges.x1[e] - edges.x0[e])
                // Insertion sort by x.
                var p = m
                while (p > 0 && xs[p - 1] > x) { xs[p] = xs[p - 1]; ws[p] = ws[p - 1]; p-- }
                xs[p] = x; ws[p] = edges.dir[e]
                m++
            }
            activeCount = k
            if (m < 2) continue
            var wind = 0
            for (c in 0 until m - 1) {
                wind += ws[c]
                val inside = if (evenOdd) (wind and 1) != 0 else wind != 0
                if (inside && xs[c + 1] > xs[c]) {
                    span(diff, xs[c] - colStart, xs[c + 1] - colStart, weight)
                    any = true
                }
            }
        }
        if (!any) continue
        var acc = 0f
        val base = row * width + colStart
        for (i in 0 until colEnd - colStart) {
            acc += diff[i]
            val c = if (acc <= 0f) 0f else if (acc >= 1f) 1f else acc
            if (c == 0f) continue
            val t = target[base + i]
            target[base + i] = t + c - t * c
        }
    }
}

/** Adds coverage [w] over [a, b) to the difference array [d], clipped to its range. */
private fun span(d: FloatArray, a: Float, b: Float, w: Float) {
    halfLine(d, a, w)
    halfLine(d, b, -w)
}

/** Adds coverage [w] over [a, infinity). */
private fun halfLine(d: FloatArray, a: Float, w: Float) {
    val limit = d.size - 2
    if (a <= 0f) { d[0] += w; return }
    if (a >= limit) return
    val i = a.toInt()
    val f = a - i
    d[i] += (1f - f) * w
    d[i + 1] += f * w
}
