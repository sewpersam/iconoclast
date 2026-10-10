package dev.iconoclast.buildlogic.arcticons

/** Bezier handle length for a quarter circle of radius 1. */
private const val KAPPA = 0.5522847498307936

val SHAPE_TAGS = setOf("path", "line", "rect", "circle", "ellipse", "polyline", "polygon")

/**
 * Appends the outline of a basic shape element as absolute path commands.
 * Returns false when the element's geometry is missing or invalid, which disables rendering.
 */
fun buildShape(node: SvgNode, out: PathBuilder): Boolean {
    fun num(name: String, default: Double? = 0.0): Double? =
        node.attrs[name]?.trim()?.removeSuffix("px")?.toDoubleOrNull() ?: if (node.attrs[name] == null) default else null

    return when (node.tag) {
        "path" -> parsePathData(node.attrs["d"] ?: return false, out)
        "line" -> {
            out.moveTo(num("x1") ?: return false, num("y1") ?: return false)
            out.lineTo(num("x2") ?: return false, num("y2") ?: return false)
            true
        }
        "polyline", "polygon" -> {
            val v = NumberScanner(node.attrs["points"] ?: return false).all() ?: return false
            if (v.size < 4) return false
            out.moveTo(v[0], v[1])
            // An odd trailing coordinate is an error: render the points before it.
            for (i in 2 until v.size - 1 step 2) out.lineTo(v[i], v[i + 1])
            if (node.tag == "polygon") out.close()
            true
        }
        "circle" -> {
            val r = num("r") ?: return false
            if (r <= 0) return false
            ellipse(out, num("cx") ?: return false, num("cy") ?: return false, r, r)
            true
        }
        "ellipse" -> {
            val rx = num("rx", null)
            val ry = num("ry", null)
            // SVG 2: a missing radius takes the value of the other one.
            val ex = rx ?: ry ?: return false
            val ey = ry ?: rx ?: return false
            if (ex <= 0 || ey <= 0) return false
            ellipse(out, num("cx") ?: return false, num("cy") ?: return false, ex, ey)
            true
        }
        "rect" -> rect(out, node, ::num)
        else -> false
    }
}

private fun ellipse(out: PathBuilder, cx: Double, cy: Double, rx: Double, ry: Double) {
    val kx = rx * KAPPA
    val ky = ry * KAPPA
    out.moveTo(cx + rx, cy)
    out.cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
    out.cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
    out.cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
    out.cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
    out.close()
}

private fun rect(out: PathBuilder, node: SvgNode, num: (String, Double?) -> Double?): Boolean {
    val x = num("x", 0.0) ?: return false
    val y = num("y", 0.0) ?: return false
    val w = num("width", 0.0) ?: return false
    val h = num("height", 0.0) ?: return false
    if (w <= 0 || h <= 0) return false
    // A missing or negative radius takes the other one; both are clamped to half the side.
    val rxIn = num("rx", null)?.takeIf { it >= 0 }
    val ryIn = num("ry", null)?.takeIf { it >= 0 }
    val rx = minOf(rxIn ?: ryIn ?: 0.0, w / 2)
    val ry = minOf(ryIn ?: rxIn ?: 0.0, h / 2)
    if (rx == 0.0 || ry == 0.0) {
        out.moveTo(x, y)
        out.lineTo(x + w, y)
        out.lineTo(x + w, y + h)
        out.lineTo(x, y + h)
        out.close()
        return true
    }
    val kx = rx * KAPPA
    val ky = ry * KAPPA
    out.moveTo(x + rx, y)
    out.lineTo(x + w - rx, y)
    out.cubicTo(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry)
    out.lineTo(x + w, y + h - ry)
    out.cubicTo(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h)
    out.lineTo(x + rx, y + h)
    out.cubicTo(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry)
    out.lineTo(x, y + ry)
    out.cubicTo(x, y + ry - ky, x + rx - kx, y, x + rx, y)
    out.close()
    return true
}
