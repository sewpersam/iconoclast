package dev.icondesigner.buildlogic.arcticons

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 2D affine map: x' = a*x + c*y + e, y' = b*x + d*y + f, as in SVG `matrix(a b c d e f)`. */
data class Affine(
    val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0,
    val d: Double = 1.0, val e: Double = 0.0, val f: Double = 0.0,
) {
    /** This map applied after [inner]: this(inner(p)). */
    fun times(inner: Affine) = Affine(
        a * inner.a + c * inner.b, b * inner.a + d * inner.b,
        a * inner.c + c * inner.d, b * inner.c + d * inner.d,
        a * inner.e + c * inner.f + e, b * inner.e + d * inner.f + f,
    )

    fun x(x: Double, y: Double) = a * x + c * y + e
    fun y(x: Double, y: Double) = b * x + d * y + f

    /** Factor that scales a stroke width: the geometric mean of the axis scales. */
    val strokeScale: Double get() = sqrt(abs(a * d - b * c))

    /** True when circles map to circles, so a scaled stroke width stays exact. */
    val isConformal: Boolean get() = abs(a - d) < 1e-6 && abs(b + c) < 1e-6 || abs(a + d) < 1e-6 && abs(b - c) < 1e-6

    companion object {
        val IDENTITY = Affine()
        fun translate(tx: Double, ty: Double) = Affine(e = tx, f = ty)
        fun scale(sx: Double, sy: Double) = Affine(a = sx, d = sy)
        fun rotate(deg: Double): Affine {
            val r = Math.toRadians(deg)
            return Affine(cos(r), sin(r), -sin(r), cos(r))
        }

        private val ITEM = Regex("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)")

        /** Parses an SVG transform list. Returns null if any part is unparseable. */
        fun parse(text: String): Affine? {
            var m = IDENTITY
            var consumed = 0
            for (item in ITEM.findAll(text)) {
                if (text.substring(consumed, item.range.first).any { !it.isWhitespace() && it != ',' }) return null
                consumed = item.range.last + 1
                val v = NumberScanner(item.groupValues[2]).all() ?: return null
                val t = when (item.groupValues[1]) {
                    "matrix" -> if (v.size == 6) Affine(v[0], v[1], v[2], v[3], v[4], v[5]) else return null
                    "translate" -> when (v.size) {
                        1 -> translate(v[0], 0.0)
                        2 -> translate(v[0], v[1])
                        else -> return null
                    }
                    "scale" -> when (v.size) {
                        1 -> scale(v[0], v[0])
                        2 -> scale(v[0], v[1])
                        else -> return null
                    }
                    "rotate" -> when (v.size) {
                        1 -> rotate(v[0])
                        3 -> translate(v[1], v[2]).times(rotate(v[0])).times(translate(-v[1], -v[2]))
                        else -> return null
                    }
                    "skewX" -> if (v.size == 1) Affine(c = tan(Math.toRadians(v[0]))) else return null
                    "skewY" -> if (v.size == 1) Affine(b = tan(Math.toRadians(v[0]))) else return null
                    else -> return null
                }
                m = m.times(t)
            }
            if (text.substring(consumed).any { !it.isWhitespace() && it != ',' }) return null
            return m
        }
    }
}
