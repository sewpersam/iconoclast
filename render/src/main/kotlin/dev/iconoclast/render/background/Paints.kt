package dev.iconoclast.render.background

import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Paint
import kotlin.math.sqrt

/**
 * Evaluates a [Paint] at every pixel centre into premultiplied RGBA floats, 0 to 1.
 * Gradients interpolate premultiplied colours, so a fade to transparent does not darken.
 */
object Paints {

    fun fill(paint: Paint, size: Int): FloatArray {
        val out = FloatArray(size * size * 4)
        val c = FloatArray(4)
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + 0.5f) / size
            val v = (y + 0.5f) / size
            when (paint) {
                is Paint.Solid -> premultiply(paint.argb, c)
                is Paint.LinearGradient -> {
                    val dx = paint.x1 - paint.x0
                    val dy = paint.y1 - paint.y0
                    val len2 = dx * dx + dy * dy
                    val t = if (len2 == 0f) 0f else ((u - paint.x0) * dx + (v - paint.y0) * dy) / len2
                    sample(paint.stops, t, c)
                }
                is Paint.RadialGradient -> {
                    val dx = u - paint.cx
                    val dy = v - paint.cy
                    sample(paint.stops, sqrt(dx * dx + dy * dy) / paint.radius, c)
                }
            }
            c.copyInto(out, (y * size + x) * 4)
        }
        return out
    }

    /** Straight 0xAARRGGBB to premultiplied r, g, b, a. */
    fun premultiply(argb: Int, out: FloatArray, opacity: Float = 1f) {
        val a = (argb ushr 24) / 255f * opacity
        out[0] = (argb shr 16 and 0xFF) / 255f * a
        out[1] = (argb shr 8 and 0xFF) / 255f * a
        out[2] = (argb and 0xFF) / 255f * a
        out[3] = a
    }

    /** Clamped, piecewise linear between stops. */
    private fun sample(stops: List<ColorStop>, t: Float, out: FloatArray) {
        if (t <= stops.first().offset) return premultiply(stops.first().argb, out)
        if (t >= stops.last().offset) return premultiply(stops.last().argb, out)
        var i = 0
        while (stops[i + 1].offset < t) i++
        val a = stops[i]
        val b = stops[i + 1]
        val span = b.offset - a.offset
        val f = if (span == 0f) 1f else (t - a.offset) / span
        val ca = FloatArray(4).also { premultiply(a.argb, it) }
        val cb = FloatArray(4).also { premultiply(b.argb, it) }
        for (k in 0..3) out[k] = ca[k] + (cb[k] - ca[k]) * f
    }
}
