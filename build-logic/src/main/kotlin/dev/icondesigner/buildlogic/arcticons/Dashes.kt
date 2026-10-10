package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.Verb
import kotlin.math.hypot

/** Line segments per cubic when flattening for dashing. 48-unit icons need no more. */
private const val CUBIC_STEPS = 32

/**
 * Bakes a dash pattern into geometry: returns open subpaths, one per dash, as verbs and
 * coords. The pattern restarts at each subpath, as SVG specifies. Curves are flattened.
 * A zero-length dash becomes a zero-length segment, which round or square caps draw as a dot.
 */
fun applyDashes(verbs: ByteArray, coords: FloatArray, dashes: FloatArray, offset: Float): Pair<ByteArray, FloatArray> {
    val outVerbs = ArrayList<Byte>()
    val outCoords = ArrayList<Float>()
    for (poly in flatten(verbs, coords)) dashPolyline(poly, dashes, offset, outVerbs, outCoords)
    return outVerbs.toByteArray() to outCoords.toFloatArray()
}

/** Each subpath as a flat x,y list. A closed subpath ends with its start point repeated. */
private fun flatten(verbs: ByteArray, coords: FloatArray): List<FloatArray> {
    val polys = ArrayList<FloatArray>()
    var cur = ArrayList<Float>()
    var i = 0
    var sx = 0f; var sy = 0f
    fun flush() {
        if (cur.size >= 4) polys += cur.toFloatArray()
        cur = ArrayList()
    }
    for (v in verbs) {
        when (v) {
            Verb.MOVE -> {
                flush()
                sx = coords[i]; sy = coords[i + 1]
                cur += sx; cur += sy
                i += 2
            }
            Verb.LINE -> {
                cur += coords[i]; cur += coords[i + 1]
                i += 2
            }
            Verb.CUBIC -> {
                val x0 = cur[cur.size - 2]; val y0 = cur[cur.size - 1]
                for (s in 1..CUBIC_STEPS) {
                    val t = s.toFloat() / CUBIC_STEPS
                    val u = 1 - t
                    val a = u * u * u; val b = 3 * u * u * t; val c = 3 * u * t * t; val d = t * t * t
                    cur += a * x0 + b * coords[i] + c * coords[i + 2] + d * coords[i + 4]
                    cur += a * y0 + b * coords[i + 1] + c * coords[i + 3] + d * coords[i + 5]
                }
                i += 6
            }
            Verb.CLOSE -> {
                cur += sx; cur += sy
                flush()
                cur += sx; cur += sy
            }
        }
    }
    flush()
    return polys
}

private fun dashPolyline(p: FloatArray, dashes: FloatArray, offset: Float, verbs: MutableList<Byte>, coords: MutableList<Float>) {
    val period = dashes.sum()
    // Find the dash index and the distance left in it at the path start.
    var phase = ((offset % period) + period) % period
    var idx = 0
    while (phase > 0f && phase >= dashes[idx]) {
        phase -= dashes[idx]
        idx = (idx + 1) % dashes.size
    }
    var left = dashes[idx] - phase
    var on = idx % 2 == 0
    if (on) { verbs += Verb.MOVE; coords += p[0]; coords += p[1] }
    for (k in 0 until p.size / 2 - 1) {
        val x0 = p[2 * k]; val y0 = p[2 * k + 1]
        val x1 = p[2 * k + 2]; val y1 = p[2 * k + 3]
        val len = hypot(x1 - x0, y1 - y0)
        var pos = 0f
        while (len - pos > left) {
            pos += left
            val t = pos / len
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            if (on) { verbs += Verb.LINE; coords += x; coords += y } else { verbs += Verb.MOVE; coords += x; coords += y }
            on = !on
            idx = (idx + 1) % dashes.size
            left = dashes[idx]
        }
        left -= len - pos
        if (on) { verbs += Verb.LINE; coords += x1; coords += y1 }
    }
}
