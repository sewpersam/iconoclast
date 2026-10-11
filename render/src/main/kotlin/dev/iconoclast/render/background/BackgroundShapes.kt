package dev.iconoclast.render.background

import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.geom.FloatList
import dev.iconoclast.render.geom.Polyline
import dev.iconoclast.render.geom.Stroker
import dev.iconoclast.render.raster.EdgeList
import dev.iconoclast.render.raster.FLATTEN_TOLERANCE
import dev.iconoclast.render.raster.fillCoverage
import kotlin.math.PI
import dev.iconoclast.render.math.ceil
import kotlin.math.max

/**
 * Background shapes as polygons in pixel space, centred on the canvas, rasterized by the
 * same scanline filler as the icons. The shape is baked into the PNG; launcher masking is
 * not relied on.
 */
object BackgroundShapes {

    /** Coverage of [shape] with side [fraction] of a [size] canvas. */
    fun mask(shape: BackgroundShape, fraction: Float, size: Int): FloatArray {
        val e = EdgeList().apply { addPolygon(outline(shape, fraction, size)) }
        return FloatArray(size * size).also { fillCoverage(e, false, size, size, it) }
    }

    /** A border [width] wide, as a fraction of the canvas, inside the shape's edge. */
    fun border(shape: BackgroundShape, fraction: Float, width: Float, size: Int, shapeMask: FloatArray): FloatArray {
        val ring = EdgeList()
        Stroker(width * size, LineCap.BUTT, LineJoin.ROUND, 4f, FLATTEN_TOLERANCE)
            .stroke(Polyline(outline(shape, fraction, size), closed = true), ring)
        val cov = FloatArray(size * size).also { fillCoverage(ring, false, size, size, it) }
        // The stroke straddles the edge; the shape clips off its outer half.
        return FloatArray(cov.size) { minOf(cov[it], shapeMask[it]) }
    }

    /** The outline as x,y pairs. */
    fun outline(shape: BackgroundShape, fraction: Float, size: Int): FloatArray {
        val side = size * fraction
        val x0 = (size - side) / 2
        val y0 = x0
        return when (shape) {
            BackgroundShape.Square -> roundedRect(x0, y0, side, 0f, 0f, 0f, 0f)
            BackgroundShape.Circle -> roundedRect(x0, y0, side, 0.5f, 0.5f, 0.5f, 0.5f)
            BackgroundShape.Teardrop -> roundedRect(x0, y0, side, 0.5f, 0.5f, TEARDROP_CORNER, 0.5f)
            is BackgroundShape.RoundedSquare -> {
                val r = shape.cornerRadius
                roundedRect(x0, y0, side, r, r, r, r)
            }
            is BackgroundShape.Squircle -> squircle(x0, y0, side, shape.exponent)
        }
    }

    /**
     * A square with per-corner radii as fractions of [side]: top left, top right, bottom
     * right, bottom left. Clockwise on screen.
     */
    private fun roundedRect(x0: Float, y0: Float, side: Float, tl: Float, tr: Float, br: Float, bl: Float): FloatArray {
        val out = FloatList()
        val x1 = x0 + side
        val y1 = y0 + side
        corner(out, x0 + tl * side, y0 + tl * side, tl * side, PI)
        corner(out, x1 - tr * side, y0 + tr * side, tr * side, 1.5 * PI)
        corner(out, x1 - br * side, y1 - br * side, br * side, 0.0)
        corner(out, x0 + bl * side, y1 - bl * side, bl * side, 0.5 * PI)
        return out.toArray()
    }

    /** A quarter arc of radius [r] about (cx, cy) from angle [from], y down. */
    private fun corner(out: FloatList, cx: Float, cy: Float, r: Float, from: Double) {
        if (r <= 0f) {
            out.add(cx, cy)
            return
        }
        val steps = arcSteps(r, PI / 2)
        // Inner points sit slightly outside the circle so the polygon keeps the arc's area.
        val step = PI / 2 / steps
        val ro = r * StrictMath.sqrt(step / StrictMath.sin(step))
        for (i in 0..steps) {
            val a = from + PI / 2 * i / steps
            val rr = if (i == 0 || i == steps) r.toDouble() else ro
            out.add(cx + (rr * StrictMath.cos(a)).toFloat(), cy + (rr * StrictMath.sin(a)).toFloat())
        }
    }

    /** |x|^n + |y|^n = 1, sampled finely enough at the canvas size. */
    private fun squircle(x0: Float, y0: Float, side: Float, n: Float): FloatArray {
        val out = FloatList()
        val a = side / 2.0
        val cx = x0 + a
        val cy = y0 + a
        val count = 4 * max(64, ceil(side / 2).toInt())
        val e = 2.0 / n
        for (i in 0 until count) {
            val t = 2 * PI * i / count
            val c = StrictMath.cos(t)
            val s = StrictMath.sin(t)
            val x = StrictMath.copySign(StrictMath.pow(StrictMath.abs(c), e), c)
            val y = StrictMath.copySign(StrictMath.pow(StrictMath.abs(s), e), s)
            out.add((cx + a * x).toFloat(), (cy + a * y).toFloat())
        }
        return out.toArray()
    }

    private fun arcSteps(r: Float, sweep: Double): Int {
        if (r <= FLATTEN_TOLERANCE) return 1
        val step = 2 * StrictMath.acos(1.0 - FLATTEN_TOLERANCE / r)
        return ceil(sweep / step).toInt().coerceIn(1, 1024)
    }

    /** AOSP's teardrop path rounds its bottom-right corner to 15% of the side. */
    private const val TEARDROP_CORNER = 0.15f
}
