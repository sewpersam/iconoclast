package dev.icondesigner.render.verify

import dev.icondesigner.render.IconRasterizer
import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.format.Primitive
import dev.icondesigner.render.format.Verb
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/**
 * JVM-only rasterizer for the conversion check: java.awt strokes at the original width,
 * caps, joins and miter limit, fills with the recorded fill rule, each fill and stroke
 * painted source-over in order. It draws [supersample] times larger and box-filters down,
 * the same treatment the inkscape reference gets, so both sides carry near-exact coverage.
 */
class AwtRasterizer(private val supersample: Int = SUPERSAMPLE) : IconRasterizer {

    override fun rasterize(icon: Icon, size: Int): ByteArray {
        val big = size * supersample
        val image = BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.WHITE
        val toPixels = AffineTransform.getScaleInstance(big / 48.0, big / 48.0)
        for (p in icon.primitives) {
            val path = toPath(p)
            if (p.isFilled) g.fill(toPixels.createTransformedShape(path))
            if (p.isStroked) {
                val stroke = BasicStroke(p.strokeWidth, cap(p.cap), join(p.join), maxOf(1f, p.miterLimit))
                // Java2D's offset curves for tight cubics are off by up to a pixel. Stroking a
                // finely flattened copy leaves only line segments and joins, which it does exactly.
                val flat = Path2D.Float()
                flat.append(path.getPathIterator(null, FLATNESS), false)
                g.fill(toPixels.createTransformedShape(stroke.createStrokedShape(flat)))
            }
        }
        g.dispose()
        val argb = image.getRGB(0, 0, big, big, null, 0, big)
        return downsample(ByteArray(argb.size) { (argb[it] ushr 24).toByte() }, big, supersample)
    }

    private fun toPath(p: Primitive): Path2D.Float {
        val path = Path2D.Float(if (p.isEvenOdd) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO)
        val c = p.coords
        var i = 0
        for (v in p.verbs) {
            when (v) {
                Verb.MOVE -> { path.moveTo(c[i], c[i + 1]); i += 2 }
                Verb.LINE -> { path.lineTo(c[i], c[i + 1]); i += 2 }
                Verb.CUBIC -> { path.curveTo(c[i], c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5]); i += 6 }
                Verb.CLOSE -> path.closePath()
            }
        }
        return path
    }

    private fun cap(cap: Int) = when (cap) {
        Primitive.CAP_ROUND -> BasicStroke.CAP_ROUND
        Primitive.CAP_SQUARE -> BasicStroke.CAP_SQUARE
        else -> BasicStroke.CAP_BUTT
    }

    private fun join(join: Int) = when (join) {
        Primitive.JOIN_ROUND -> BasicStroke.JOIN_ROUND
        Primitive.JOIN_BEVEL -> BasicStroke.JOIN_BEVEL
        else -> BasicStroke.JOIN_MITER
    }
}

/** Maximum distance, in canvas units, between a curve and its flattened stroke input. */
private const val FLATNESS = 0.002

/** Render scale for both sides of the comparison. */
const val SUPERSAMPLE = 4

/** Box-filters a [big] x [big] coverage mask down by [factor] on each axis. */
fun downsample(alpha: ByteArray, big: Int, factor: Int): ByteArray {
    val size = big / factor
    val n = factor * factor
    val out = ByteArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        var sum = 0
        for (dy in 0 until factor) for (dx in 0 until factor) {
            sum += alpha[(y * factor + dy) * big + x * factor + dx].toInt() and 0xFF
        }
        out[y * size + x] = ((sum + n / 2) / n).toByte()
    }
    return out
}
