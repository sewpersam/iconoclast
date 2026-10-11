package dev.iconoclast.render.effect

import dev.iconoclast.render.Decay
import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor

/**
 * The streak smear, readiness item 9. The mask is turned into a frame whose rows run
 * along the streak ([Frame.toFrame], bilinear, zero outside), each row is smeared
 * causally toward the streak's direction, and the result is turned back
 * ([Frame.fromFrame]).
 *
 * The smear is a hold, not a blur: each pixel keeps the strongest paint dragged onto it,
 * `y[i] = max(x[i], decayed y[i - 1])`, so a thin line streaks as strongly as a solid
 * body, as a brush drags paint off any edge it crosses. A normalised motion blur, the
 * first model, left thin lines a faint trail of their width over the length. The
 * reference is a brush streak; the hold is the ruling (Task 9 review). Two decays, each
 * one recursion, O(1) per pixel at any length L:
 *
 * - [Decay.LINEAR]: `y[i] = max(x[i], y[i - 1] - 1 / L)`, a straight fade that ends at L.
 * - [Decay.EXPONENTIAL]: `y[i] = max(x[i], r y[i - 1])` with `r = exp(-3 / L)`, 5% left
 *   at L, a faint tail beyond.
 *
 * L is fractional in both. Only these two curves: an arbitrary curve has no recursion
 * and would cost L taps per pixel, about 260 at the longest streak on a 432 px icon,
 * which breaks the phone budget; the two cover a fade that ends and one that tails off.
 */
internal object Smear {

    /** Smears every row of [frame], [side] x [side], over [length] pixels. A new array. */
    fun smear(frame: FloatArray, side: Int, length: Float, decay: Decay): FloatArray {
        val out = FloatArray(frame.size)
        if (length < 1f) {
            frame.copyInto(out)
            return out
        }
        val step = 1f / length
        val r = StrictMath.exp(-3.0 / length).toFloat()
        for (row in 0 until side) {
            val base = row * side
            var y = 0f
            for (i in 0 until side) {
                val held = if (decay == Decay.LINEAR) y - step else r * y
                val x = frame[base + i]
                y = if (x > held) x else if (held > 0f) held else 0f
                out[base + i] = y
            }
        }
        return out
    }

    /**
     * A square frame turned to [angleDegrees] about the canvas centre, big enough to hold
     * the canvas at any angle. Frame x runs along the streak: angle 0 is canvas right, 90
     * canvas up. Its side minus the canvas side is even, so at multiples of 90 degrees
     * frame pixel centres land on canvas pixel centres.
     */
    class Frame(val size: Int, angleDegrees: Float) {
        val side: Int
        private val dx: Float
        private val dy: Float
        private val half: Float
        private val centre: Float

        init {
            val pad = ceil(size * 0.20710678f).toInt() + 1 // (sqrt 2 - 1) / 2
            side = size + 2 * pad
            val a = angleDegrees.toDouble() * (StrictMath.PI / 180.0)
            dx = StrictMath.cos(a).toFloat()
            dy = -StrictMath.sin(a).toFloat() // screen y runs down
            half = side / 2f
            centre = size / 2f
        }

        /** Canvas position of frame pixel (i, j)'s centre, in pixel units with centres at k + 0.5. */
        private fun canvasX(u: Float, v: Float) = centre + u * dx - v * dy
        private fun canvasY(u: Float, v: Float) = centre + u * dy + v * dx

        /**
         * [m], [size] x [size], sampled into the frame. Only frame pixels whose sample can
         * reach the non-zero part of [m] are sampled; the rest are 0, as sampling would give.
         */
        fun toFrame(m: FloatArray): FloatArray {
            val out = FloatArray(side * side)
            var x0 = size
            var x1 = -1
            var y0 = size
            var y1 = -1
            for (y in 0 until size) for (x in 0 until size) if (m[y * size + x] != 0f) {
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
            if (x1 < 0) return out
            // A bilinear sample at s reads floor(s) and floor(s) + 1: non-zero only for s in (x0 - 1, x1 + 1).
            val lox = x0 - 1f
            val hix = x1 + 1f
            val loy = y0 - 1f
            val hiy = y1 + 1f
            for (j in 0 until side) {
                val v = j + 0.5f - half
                // Sample position along the row: sx = ax + u dx, sy = ay + u dy.
                val ax = centre - v * dy - 0.5f
                val ay = centre + v * dx - 0.5f
                var ulo = -half
                var uhi = half
                fun clip(a: Float, d: Float, lo: Float, hi: Float) {
                    if (kotlin.math.abs(d) < 1e-6f) {
                        if (a <= lo - 1f || a >= hi + 1f) { ulo = 1f; uhi = 0f }
                        return
                    }
                    val t0 = (lo - a) / d
                    val t1 = (hi - a) / d
                    ulo = maxOf(ulo, minOf(t0, t1) - 1f)
                    uhi = minOf(uhi, maxOf(t0, t1) + 1f)
                }
                clip(ax, dx, lox, hix)
                clip(ay, dy, loy, hiy)
                if (ulo > uhi) continue
                val i0 = maxOf(0, (ulo + half - 0.5f).toInt() - 1)
                val i1 = minOf(side - 1, (uhi + half - 0.5f).toInt() + 1)
                for (i in i0..i1) {
                    val u = i + 0.5f - half
                    out[j * side + i] = bilinear(m, size, canvasX(u, v) - 0.5f, canvasY(u, v) - 0.5f)
                }
            }
            return out
        }

        /** [f], [side] x [side], sampled back onto the canvas, skipping samples that read only zeros. */
        fun fromFrame(f: FloatArray): FloatArray {
            // Each frame row's first and last non-zero column; lo > hi when it is all zero.
            val lo = IntArray(side) { side }
            val hi = IntArray(side) { -1 }
            for (j in 0 until side) {
                val row = j * side
                for (i in 0 until side) if (f[row + i] != 0f) { lo[j] = i; break }
                for (i in side - 1 downTo 0) if (f[row + i] != 0f) { hi[j] = i; break }
            }
            val out = FloatArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val px = x + 0.5f - centre
                val py = y + 0.5f - centre
                // Inverse of the turn: u along (dx, dy), v along (-dy, dx).
                val u = px * dx + py * dy
                val v = -px * dy + py * dx
                val sx = u + half - 0.5f
                val sy = v + half - 0.5f
                val i0 = floor(sx).toInt()
                val j0 = floor(sy).toInt()
                if (!reaches(lo, hi, j0, i0) && !reaches(lo, hi, j0 + 1, i0)) continue
                out[y * size + x] = bilinear(f, side, sx, sy)
            }
            return out
        }

        /** Whether frame row [j] has a non-zero value at column [i] or [i] + 1. */
        private fun reaches(lo: IntArray, hi: IntArray, j: Int, i: Int): Boolean =
            j in 0 until side && i + 1 >= lo[j] && i <= hi[j]

        /** Frame coordinates (u, v) of frame pixel centres, for sampling along the streak. */
        fun u(i: Int): Float = i + 0.5f - half
        fun v(j: Int): Float = j + 0.5f - half
    }

    /** Bilinear sample of [m], [n] x [n], at (x, y) in pixel-index coordinates, zero outside. */
    fun bilinear(m: FloatArray, n: Int, x: Float, y: Float): Float {
        val fx = floor(x)
        val fy = floor(y)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        if (x0 < -1 || y0 < -1 || x0 >= n || y0 >= n) return 0f
        val wx = x - fx
        val wy = y - fy
        fun at(i: Int, j: Int) = if (i < 0 || j < 0 || i >= n || j >= n) 0f else m[j * n + i]
        val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * wx
        val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * wx
        return top + (bottom - top) * wy
    }
}
