package dev.icondesigner.render.raster

import dev.icondesigner.render.LineCap
import dev.icondesigner.render.LineJoin
import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.format.Primitive
import dev.icondesigner.render.geom.Affine
import dev.icondesigner.render.geom.PathFlattener
import dev.icondesigner.render.geom.Polyline
import dev.icondesigner.render.geom.Stroker

/** Curve and arc flattening tolerance, in pixels. */
const val FLATTEN_TOLERANCE = 0.05f

/** Fill-only primitives no wider or taller than this, in canvas units, are dots. */
const val DOT_MAX_EXTENT = 2.5f

/** How to draw an icon's line art. Null style fields keep each primitive's own value. */
class GlyphSpec(
    /** Canvas units to pixels. */
    val transform: Affine,
    /** Multiplies every source stroke width. */
    val strokeScale: Float = 1f,
    /** Canvas units added to every stroke width. Filled primitives get an outline this wide. */
    val extraWidth: Float = 0f,
    val cap: LineCap? = null,
    val join: LineJoin? = null,
    val miterLimit: Float? = null,
    /** Scales each dot about its centre. */
    val dotScale: Float = 1f,
)

/**
 * Draws [icon] into a [size] x [size] coverage mask, values 0 to 1. Each fill and each
 * stroke is its own pass, combined source-over as SVG paints them, so overlapping
 * primitives match an SVG renderer's edge coverage. Source-over of coverage is
 * order-independent. A stroke's own pieces fill nonzero, an exact union.
 */
fun rasterizeGlyph(icon: Icon, spec: GlyphSpec, size: Int): FloatArray {
    val target = FloatArray(size * size)
    val strokes = EdgeList()
    val fill = EdgeList()
    for (p in icon.primitives) {
        strokes.clear()
        val m = if (spec.dotScale != 1f && isDot(p)) dotTransform(p, spec) else spec.transform
        val lines = PathFlattener.flatten(p, m, FLATTEN_TOLERANCE)
        if (p.isFilled) {
            fill.clear()
            for (l in lines) fill.addPolygon(l.xy)
            fillCoverage(fill, p.isEvenOdd, size, size, target)
            if (spec.extraWidth > 0f) {
                val outline = Stroker(spec.extraWidth * m.scale / 2, LineCap.ROUND, LineJoin.ROUND, 4f, FLATTEN_TOLERANCE)
                for (l in lines) outline.stroke(Polyline(l.xy, closed = true), strokes)
            }
        }
        if (p.isStroked) {
            val width = (p.strokeWidth * spec.strokeScale + spec.extraWidth) * m.scale
            val stroker = Stroker(
                width / 2,
                spec.cap ?: capOf(p),
                spec.join ?: joinOf(p),
                spec.miterLimit ?: maxOf(1f, p.miterLimit),
                FLATTEN_TOLERANCE,
            )
            for (l in lines) stroker.stroke(l, strokes)
        }
        fillCoverage(strokes, false, size, size, target)
    }
    return target
}

/** A small fill-only primitive: Arcticons draws its dots this way. */
fun isDot(p: Primitive): Boolean {
    if (!p.isFilled || p.isStroked || p.coords.isEmpty()) return false
    val b = bounds(p)
    return b[2] - b[0] <= DOT_MAX_EXTENT && b[3] - b[1] <= DOT_MAX_EXTENT
}

private fun dotTransform(p: Primitive, spec: GlyphSpec): Affine {
    val b = bounds(p)
    val m = spec.transform
    return m.thenScaleAbout(spec.dotScale, m.x((b[0] + b[2]) / 2), m.y((b[1] + b[3]) / 2))
}

/** minX, minY, maxX, maxY of the control points. */
private fun bounds(p: Primitive): FloatArray {
    val c = p.coords
    val b = floatArrayOf(c[0], c[1], c[0], c[1])
    for (i in c.indices step 2) {
        b[0] = minOf(b[0], c[i]); b[2] = maxOf(b[2], c[i])
        b[1] = minOf(b[1], c[i + 1]); b[3] = maxOf(b[3], c[i + 1])
    }
    return b
}

private fun capOf(p: Primitive) = when (p.cap) {
    Primitive.CAP_ROUND -> LineCap.ROUND
    Primitive.CAP_SQUARE -> LineCap.SQUARE
    else -> LineCap.BUTT
}

private fun joinOf(p: Primitive) = when (p.join) {
    Primitive.JOIN_ROUND -> LineJoin.ROUND
    Primitive.JOIN_BEVEL -> LineJoin.BEVEL
    else -> LineJoin.MITER
}

/** Rounds coverage 0 to 1 to bytes 0 to 255. */
fun toBytes(coverage: FloatArray): ByteArray =
    ByteArray(coverage.size) { (coverage[it] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() }
