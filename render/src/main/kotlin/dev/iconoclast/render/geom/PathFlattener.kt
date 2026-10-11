package dev.iconoclast.render.geom

import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb
import kotlin.math.abs
import dev.iconoclast.render.math.ceil
import kotlin.math.max
import kotlin.math.min
import dev.iconoclast.render.math.sqrt

/** Uniform scale then translate: (x, y) to (x * scale + tx, y * scale + ty). */
data class Affine(val scale: Float, val tx: Float, val ty: Float) {
    fun x(x: Float): Float = x * scale + tx
    fun y(y: Float): Float = y * scale + ty

    /** This transform followed by a scale of [k] about the point (cx, cy) in output space. */
    fun thenScaleAbout(k: Float, cx: Float, cy: Float) =
        Affine(scale * k, (tx - cx) * k + cx, (ty - cy) * k + cy)
}

/**
 * One subpath as a list of points, x,y pairs. [closed] marks a CLOSE verb. Consecutive
 * duplicates are kept: a zero-length segment is real and caps draw it as a dot.
 */
class Polyline(val xy: FloatArray, val closed: Boolean) {
    val size: Int get() = xy.size / 2
}

/** Turns path verbs into polylines, cubics subdivided to within a tolerance. */
object PathFlattener {

    /**
     * Flattens [p] under [m] into output space, so [tolerance] is in output units. A
     * subpath with a MOVE and nothing else is dropped, as in SVG.
     */
    fun flatten(p: Primitive, m: Affine, tolerance: Float): List<Polyline> {
        val out = ArrayList<Polyline>()
        val c = p.coords
        var pts = FloatList()
        var i = 0
        var cx = 0f
        var cy = 0f
        fun finish(closed: Boolean) {
            if (pts.size >= 4) out += Polyline(pts.toArray(), closed)
            pts = FloatList()
        }
        for (v in p.verbs) {
            when (v) {
                Verb.MOVE -> {
                    finish(false)
                    cx = m.x(c[i]); cy = m.y(c[i + 1]); i += 2
                    pts.add(cx, cy)
                }
                Verb.LINE -> {
                    cx = m.x(c[i]); cy = m.y(c[i + 1]); i += 2
                    pts.add(cx, cy)
                }
                Verb.CUBIC -> {
                    val x1 = m.x(c[i]); val y1 = m.y(c[i + 1])
                    val x2 = m.x(c[i + 2]); val y2 = m.y(c[i + 3])
                    val x3 = m.x(c[i + 4]); val y3 = m.y(c[i + 5])
                    i += 6
                    cubic(pts, cx, cy, x1, y1, x2, y2, x3, y3, tolerance)
                    cx = x3; cy = y3
                }
                Verb.CLOSE -> finish(true)
            }
        }
        finish(false)
        return out
    }

    /**
     * Uniform subdivision. A cubic's second derivative is bounded by 6 times its largest
     * second difference dd, so n pieces deviate at most 0.75 * dd / n^2 from the curve.
     */
    internal fun cubic(
        pts: FloatList, x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float, tolerance: Float,
    ) {
        val ddx = max(abs(x0 - 2 * x1 + x2), abs(x1 - 2 * x2 + x3))
        val ddy = max(abs(y0 - 2 * y1 + y2), abs(y1 - 2 * y2 + y3))
        val dd = sqrt(ddx * ddx + ddy * ddy)
        val n = min(MAX_PIECES, max(1, ceil(sqrt(0.75f * dd / tolerance)).toInt()))
        for (k in 1 until n) {
            val t = k.toFloat() / n
            val u = 1f - t
            val a = u * u * u
            val b = 3f * u * u * t
            val cc = 3f * u * t * t
            val d = t * t * t
            pts.add(a * x0 + b * x1 + cc * x2 + d * x3, a * y0 + b * y1 + cc * y2 + d * y3)
        }
        pts.add(x3, y3)
    }

    private const val MAX_PIECES = 4096
}

/** Growable float array. */
internal class FloatList {
    private var a = FloatArray(32)
    var size = 0; private set

    fun add(x: Float, y: Float) {
        if (size + 2 > a.size) a = a.copyOf(a.size * 2)
        a[size++] = x
        a[size++] = y
    }

    fun toArray(): FloatArray = a.copyOf(size)
}
