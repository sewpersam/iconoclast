package dev.iconoclast.buildlogic.arcticons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses SVG path data into absolute moves, lines and cubics on [out].
 * Quadratics are raised to cubics and arcs become cubics. On a syntax error it keeps what
 * was parsed so far, as SVG renderers do, and returns false.
 */
fun parsePathData(d: String, out: PathBuilder): Boolean {
    val sc = NumberScanner(d)
    var cmd = ' '
    var x = 0.0; var y = 0.0          // current point
    var sx = 0.0; var sy = 0.0        // subpath start
    var cx = 0.0; var cy = 0.0        // last control point, for S and T
    var prev = ' '
    var open = false                  // a subpath was closed and no move followed yet
    var first = true

    fun ensureStart() {
        if (open) {
            out.moveTo(x, y)
            open = false
        }
    }

    while (!sc.atEnd()) {
        val c = sc.peek()!!
        if (c.isLetter()) {
            cmd = c
            sc.pos++
            if (cmd == 'z' || cmd == 'Z') {
                if (first) return false
                out.close()
                x = sx; y = sy
                open = true
                prev = 'Z'
                continue
            }
        } else if (cmd == ' ' || cmd == 'z' || cmd == 'Z') {
            return false
        }
        if (first && cmd != 'M' && cmd != 'm') return false
        val rel = cmd.isLowerCase()
        val ox = if (rel) x else 0.0
        val oy = if (rel) y else 0.0
        when (cmd.uppercaseChar()) {
            'M' -> {
                val nx = (sc.number() ?: return false) + ox
                val ny = (sc.number() ?: return false) + oy
                out.moveTo(nx, ny)
                open = false
                x = nx; y = ny; sx = nx; sy = ny
                first = false
                // Further pairs after a move are implicit lines.
                cmd = if (rel) 'l' else 'L'
                prev = 'M'
                continue
            }
            'L' -> {
                val nx = (sc.number() ?: return false) + ox
                val ny = (sc.number() ?: return false) + oy
                ensureStart(); out.lineTo(nx, ny); x = nx; y = ny
            }
            'H' -> {
                val nx = (sc.number() ?: return false) + ox
                ensureStart(); out.lineTo(nx, y); x = nx
            }
            'V' -> {
                val ny = (sc.number() ?: return false) + oy
                ensureStart(); out.lineTo(x, ny); y = ny
            }
            'C' -> {
                val x1 = (sc.number() ?: return false) + ox; val y1 = (sc.number() ?: return false) + oy
                val x2 = (sc.number() ?: return false) + ox; val y2 = (sc.number() ?: return false) + oy
                val nx = (sc.number() ?: return false) + ox; val ny = (sc.number() ?: return false) + oy
                ensureStart(); out.cubicTo(x1, y1, x2, y2, nx, ny)
                cx = x2; cy = y2; x = nx; y = ny
            }
            'S' -> {
                val x1 = if (prev == 'C' || prev == 'S') 2 * x - cx else x
                val y1 = if (prev == 'C' || prev == 'S') 2 * y - cy else y
                val x2 = (sc.number() ?: return false) + ox; val y2 = (sc.number() ?: return false) + oy
                val nx = (sc.number() ?: return false) + ox; val ny = (sc.number() ?: return false) + oy
                ensureStart(); out.cubicTo(x1, y1, x2, y2, nx, ny)
                cx = x2; cy = y2; x = nx; y = ny
            }
            'Q' -> {
                val qx = (sc.number() ?: return false) + ox; val qy = (sc.number() ?: return false) + oy
                val nx = (sc.number() ?: return false) + ox; val ny = (sc.number() ?: return false) + oy
                ensureStart(); quadTo(out, x, y, qx, qy, nx, ny)
                cx = qx; cy = qy; x = nx; y = ny
            }
            'T' -> {
                val qx = if (prev == 'Q' || prev == 'T') 2 * x - cx else x
                val qy = if (prev == 'Q' || prev == 'T') 2 * y - cy else y
                val nx = (sc.number() ?: return false) + ox; val ny = (sc.number() ?: return false) + oy
                ensureStart(); quadTo(out, x, y, qx, qy, nx, ny)
                cx = qx; cy = qy; x = nx; y = ny
            }
            'A' -> {
                val rx = sc.number() ?: return false
                val ry = sc.number() ?: return false
                val rot = sc.number() ?: return false
                val large = sc.flag() ?: return false
                val sweep = sc.flag() ?: return false
                val nx = (sc.number() ?: return false) + ox; val ny = (sc.number() ?: return false) + oy
                ensureStart(); arcTo(out, x, y, rx, ry, rot, large, sweep, nx, ny)
                x = nx; y = ny
            }
            else -> return false
        }
        prev = cmd.uppercaseChar()
    }
    return true
}

private fun quadTo(out: PathBuilder, x0: Double, y0: Double, qx: Double, qy: Double, x: Double, y: Double) {
    out.cubicTo(
        x0 + 2.0 / 3.0 * (qx - x0), y0 + 2.0 / 3.0 * (qy - y0),
        x + 2.0 / 3.0 * (qx - x), y + 2.0 / 3.0 * (qy - y),
        x, y,
    )
}

/**
 * Appends an SVG elliptical arc as cubics, one per quarter turn or less.
 * Endpoint-to-center conversion follows SVG 1.1 appendix F.6.5, including radius correction.
 */
fun arcTo(
    out: PathBuilder, x1: Double, y1: Double, rxIn: Double, ryIn: Double, angleDeg: Double,
    largeArc: Boolean, sweep: Boolean, x2: Double, y2: Double,
) {
    if (x1 == x2 && y1 == y2) return
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    if (rx == 0.0 || ry == 0.0) {
        out.lineTo(x2, y2)
        return
    }
    val phi = Math.toRadians(angleDeg % 360.0)
    val cosP = cos(phi)
    val sinP = sin(phi)
    val dx = (x1 - x2) / 2
    val dy = (y1 - y2) / 2
    val x1p = cosP * dx + sinP * dy
    val y1p = -sinP * dx + cosP * dy
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1) {
        val s = sqrt(lambda)
        rx *= s; ry *= s
    }
    val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    var coef = sqrt(maxOf(0.0, num / den))
    if (largeArc == sweep) coef = -coef
    val cxp = coef * rx * y1p / ry
    val cyp = -coef * ry * x1p / rx
    val cx = cosP * cxp - sinP * cyp + (x1 + x2) / 2
    val cy = sinP * cxp + cosP * cyp + (y1 + y2) / 2

    val theta1 = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    var delta = atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - theta1
    if (sweep && delta < 0) delta += 2 * PI
    if (!sweep && delta > 0) delta -= 2 * PI

    val segments = maxOf(1, ceil(abs(delta) / (PI / 2) - 1e-9).toInt())
    val step = delta / segments
    val k = 4.0 / 3.0 * tan(step / 4)
    var t = theta1
    for (i in 0 until segments) {
        val t2 = t + step
        val c1 = cos(t); val s1 = sin(t)
        val c2 = cos(t2); val s2 = sin(t2)
        // Unit-circle control points, then scale by radii, rotate by phi, move to center.
        val p1x = c1 - k * s1; val p1y = s1 + k * c1
        val p2x = c2 + k * s2; val p2y = s2 - k * c2
        fun ex(ux: Double, uy: Double) = cx + rx * ux * cosP - ry * uy * sinP
        fun ey(ux: Double, uy: Double) = cy + rx * ux * sinP + ry * uy * cosP
        val endX = if (i == segments - 1) x2 else ex(c2, s2)
        val endY = if (i == segments - 1) y2 else ey(c2, s2)
        out.cubicTo(ex(p1x, p1y), ey(p1x, p1y), ex(p2x, p2y), ey(p2x, p2y), endX, endY)
        t = t2
    }
}
