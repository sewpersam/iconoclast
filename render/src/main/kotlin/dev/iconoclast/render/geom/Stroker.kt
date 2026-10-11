package dev.iconoclast.render.geom

import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.raster.EdgeList
import kotlin.math.PI
import kotlin.math.abs
import dev.iconoclast.render.math.ceil
import kotlin.math.max
import dev.iconoclast.render.math.sqrt

/**
 * Strokes polylines as a union of convex pieces: a quad per segment, a wedge per join on
 * the outer side, and a cap at each open end. Every piece is added winding +1, so a
 * nonzero fill of the [EdgeList] paints exactly their union.
 *
 * Trigonometry goes through [StrictMath], which is bit-exact on every JVM and on ART.
 */
class Stroker(
    private val halfWidth: Float,
    private val cap: LineCap,
    private val join: LineJoin,
    /** SVG's limit on the ratio of miter length to stroke width. */
    private val miterLimit: Float,
    /** Maximum distance between a round piece and its polygon, in output units. */
    private val tolerance: Float,
) {
    private val buf = FloatArray(2 * (MAX_ARC_STEPS + 4))

    fun stroke(line: Polyline, out: EdgeList) {
        if (halfWidth <= 0f) return
        val pts = dedupe(line)
        val n = pts.size / 2
        if (n == 0) return
        if (n == 1) { dot(pts[0], pts[1], out); return }
        val closed = line.closed && n >= 3
        val segs = if (closed) n else n - 1
        for (i in 0 until segs) {
            val j = (i + 1) % n
            segment(pts[2 * i], pts[2 * i + 1], pts[2 * j], pts[2 * j + 1], out)
        }
        if (closed) {
            for (i in 0 until n) joinAt(pts, (i + n - 1) % n, i, (i + 1) % n, out)
        } else {
            for (i in 1 until n - 1) joinAt(pts, i - 1, i, i + 1, out)
            capAt(pts[2], pts[3], pts[0], pts[1], out)
            capAt(pts[2 * n - 4], pts[2 * n - 3], pts[2 * n - 2], pts[2 * n - 1], out)
        }
    }

    /** Drops repeated points. Returns one point when the whole line has zero length. */
    private fun dedupe(line: Polyline): FloatArray {
        val xy = line.xy
        val list = FloatList()
        var px = Float.NaN
        var py = Float.NaN
        for (i in 0 until line.size) {
            val x = xy[2 * i]
            val y = xy[2 * i + 1]
            if (x == px && y == py) continue
            list.add(x, y); px = x; py = y
        }
        var a = list.toArray()
        // A closed line that ends on its start has that point once.
        if (line.closed && a.size >= 4 && a[0] == a[a.size - 2] && a[1] == a[a.size - 1]) a = a.copyOf(a.size - 2)
        return a
    }

    private fun segment(ax: Float, ay: Float, bx: Float, by: Float, out: EdgeList) {
        val dx = bx - ax
        val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        val nx = -dy / len * halfWidth
        val ny = dx / len * halfWidth
        quad(ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny, out)
    }

    private fun joinAt(pts: FloatArray, a: Int, b: Int, c: Int, out: EdgeList) {
        val vx = pts[2 * b]
        val vy = pts[2 * b + 1]
        var d0x = vx - pts[2 * a]
        var d0y = vy - pts[2 * a + 1]
        var d1x = pts[2 * c] - vx
        var d1y = pts[2 * c + 1] - vy
        val l0 = sqrt(d0x * d0x + d0y * d0y)
        val l1 = sqrt(d1x * d1x + d1y * d1y)
        d0x /= l0; d0y /= l0; d1x /= l1; d1y /= l1
        val cross = d0x * d1y - d0y * d1x
        val dot = d0x * d1x + d0y * d1y
        if (cross == 0f && dot > 0f) return
        // The outer side is opposite the turn.
        val o = if (cross >= 0f) -1f else 1f
        val n0x = -d0y * halfWidth * o
        val n0y = d0x * halfWidth * o
        val n1x = -d1y * halfWidth * o
        val n1y = d1x * halfWidth * o
        when (join) {
            LineJoin.ROUND -> {
                // A full reversal has cross 0 or -0; either way the wedge goes forward.
                val sweep = if (cross == 0f) PI else StrictMath.atan2(cross.toDouble(), dot.toDouble())
                wedge(vx, vy, n0x, n0y, sweep, out)
            }
            LineJoin.BEVEL -> triangle(vx, vy, vx + n0x, vy + n0y, vx + n1x, vy + n1y, out)
            LineJoin.MITER -> {
                // cos of half the turn angle; the miter ratio is its inverse.
                val mx = n0x + n1x
                val my = n0y + n1y
                val ml = sqrt(mx * mx + my * my)
                val cosHalf = ml / (2f * halfWidth)
                if (cosHalf > 0f && 1f / cosHalf <= miterLimit) {
                    val k = halfWidth / cosHalf / ml
                    quad(vx, vy, vx + n0x, vy + n0y, vx + mx * k, vy + my * k, vx + n1x, vy + n1y, out)
                } else {
                    triangle(vx, vy, vx + n0x, vy + n0y, vx + n1x, vy + n1y, out)
                }
            }
        }
    }

    /** Cap at end point (ex, ey) of a segment arriving from (fx, fy). */
    private fun capAt(fx: Float, fy: Float, ex: Float, ey: Float, out: EdgeList) {
        val dx = ex - fx
        val dy = ey - fy
        val len = sqrt(dx * dx + dy * dy)
        val ux = dx / len
        val uy = dy / len
        val nx = -uy * halfWidth
        val ny = ux * halfWidth
        when (cap) {
            LineCap.BUTT -> Unit
            // Rotating the left normal by -90 degrees points along the travel direction.
            LineCap.ROUND -> wedge(ex, ey, nx, ny, -PI, out)
            LineCap.SQUARE -> {
                val ex2 = ux * halfWidth
                val ey2 = uy * halfWidth
                quad(ex + nx, ey + ny, ex + nx + ex2, ey + ny + ey2, ex - nx + ex2, ey - ny + ey2, ex - nx, ey - ny, out)
            }
        }
    }

    /** A zero-length line: a disk for round caps, an axis-aligned square for square caps. */
    private fun dot(x: Float, y: Float, out: EdgeList) {
        val h = halfWidth
        when (cap) {
            LineCap.BUTT -> Unit
            LineCap.ROUND -> wedge(x, y, h, 0f, 2 * PI, out)
            LineCap.SQUARE -> quad(x - h, y - h, x + h, y - h, x + h, y + h, x - h, y + h, out)
        }
    }

    /**
     * A circular sector at (cx, cy) from the radius vector (sx, sy), turning by [sweep]
     * radians. A full turn leaves out the centre point.
     */
    private fun wedge(cx: Float, cy: Float, sx: Float, sy: Float, sweep: Double, out: EdgeList) {
        val r = sqrt(sx * sx + sy * sy)
        val steps = arcSteps(r, abs(sweep))
        val start = StrictMath.atan2(sy.toDouble(), sx.toDouble())
        val full = abs(sweep) >= 2 * PI
        // Inner points sit slightly outside the circle so the polygon keeps the disk's area.
        val step = abs(sweep) / steps
        val ro = r * StrictMath.sqrt(step / StrictMath.sin(step))
        var k = 0
        if (!full) { buf[k++] = cx; buf[k++] = cy }
        val last = if (full) steps - 1 else steps
        for (i in 0..last) {
            val a = start + sweep * i / steps
            // The end points use the exact input vector, so pieces meet without slivers.
            if (i == 0) { buf[k++] = cx + sx; buf[k++] = cy + sy; continue }
            buf[k++] = cx + (ro * StrictMath.cos(a)).toFloat()
            buf[k++] = cy + (ro * StrictMath.sin(a)).toFloat()
        }
        out.addPositive(buf, k / 2)
    }

    /** Arc subdivision so a chord's sagitta stays within the tolerance. */
    private fun arcSteps(r: Float, sweep: Double): Int {
        if (r <= tolerance) return max(3, ceil(sweep / (PI / 2)).toInt())
        val maxStep = 2 * StrictMath.acos(1.0 - tolerance / r)
        return ceil(sweep / maxStep).toInt().coerceIn(1, MAX_ARC_STEPS)
    }

    private fun triangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, out: EdgeList) {
        buf[0] = ax; buf[1] = ay; buf[2] = bx; buf[3] = by; buf[4] = cx; buf[5] = cy
        out.addPositive(buf, 3)
    }

    private fun quad(
        ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, out: EdgeList,
    ) {
        buf[0] = ax; buf[1] = ay; buf[2] = bx; buf[3] = by; buf[4] = cx; buf[5] = cy; buf[6] = dx; buf[7] = dy
        out.addPositive(buf, 4)
    }

    private companion object {
        const val MAX_ARC_STEPS = 512
    }
}
