package dev.iconoclast.render.effect

import dev.iconoclast.render.Decay
import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor

/**
 * The streak smear, readiness item 9. The mask is turned into a frame whose rows run
 * along the streak ([Frame.toFrame], bilinear, zero outside), each row is smeared
 * causally toward the streak's direction, and the result is turned back
 * ([Frame.fromFrame]). In the frame the smear is one dimensional, so it costs O(1) per
 * pixel at any length:
 *
 * - [Decay.LINEAR], weights `L - t` for t = 0 until L: two prefix sums per row, of x and
 *   of k x, give any window's weighted sum in O(1).
 * - [Decay.EXPONENTIAL], weights `r^t` with `r = exp(-3 / L)`: the one-tap recursion
 *   `y[i] = (1 - r) x[i] + r y[i - 1]`.
 *
 * Both are normalised to sum 1, so the inside of a solid region stays at full coverage
 * and the trail fades from it. Only these two curves are allowed: an arbitrary curve has
 * no recursion or prefix form and would cost L taps per pixel, about 260 at the longest
 * streak on a 432 px icon, which breaks the phone budget; the two cover a fade that ends
 * and one that tails off.
 */
internal object Smear {

    /** Smears every row of [frame], [side] x [side], over [length] pixels. A new array. */
    fun smear(frame: FloatArray, side: Int, length: Float, decay: Decay): FloatArray {
        val out = FloatArray(frame.size)
        if (length < 1f) {
            frame.copyInto(out)
            return out
        }
        when (decay) {
            Decay.LINEAR -> {
                val l = ceil(length).toInt()
                val p = DoubleArray(side + 1)
                val q = DoubleArray(side + 1)
                // Sum of (L - t) for t in 0 until L: the weight of a full window.
                val norm = l * (l + 1) / 2.0
                for (row in 0 until side) {
                    val base = row * side
                    for (k in 0 until side) {
                        val v = frame[base + k].toDouble()
                        p[k + 1] = p[k] + v
                        q[k + 1] = q[k] + k * v
                    }
                    for (i in 0 until side) {
                        // Sum over k in [i - L + 1, i] of (L - i + k) x[k].
                        val lo = maxOf(0, i - l + 1)
                        val s = (l - i) * (p[i + 1] - p[lo]) + (q[i + 1] - q[lo])
                        out[base + i] = (s / norm).toFloat()
                    }
                }
            }
            Decay.EXPONENTIAL -> {
                val r = StrictMath.exp(-3.0 / length)
                val gain = 1.0 - r
                for (row in 0 until side) {
                    val base = row * side
                    var y = 0.0
                    for (i in 0 until side) {
                        y = gain * frame[base + i] + r * y
                        out[base + i] = y.toFloat()
                    }
                }
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

        /** [m], [size] x [size], sampled into the frame. */
        fun toFrame(m: FloatArray): FloatArray {
            val out = FloatArray(side * side)
            for (j in 0 until side) for (i in 0 until side) {
                val u = i + 0.5f - half
                val v = j + 0.5f - half
                out[j * side + i] = bilinear(m, size, canvasX(u, v) - 0.5f, canvasY(u, v) - 0.5f)
            }
            return out
        }

        /** [f], [side] x [side], sampled back onto the canvas. */
        fun fromFrame(f: FloatArray): FloatArray {
            val out = FloatArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val px = x + 0.5f - centre
                val py = y + 0.5f - centre
                // Inverse of the turn: u along (dx, dy), v along (-dy, dx).
                val u = px * dx + py * dy
                val v = -px * dy + py * dx
                out[y * size + x] = bilinear(f, side, u + half - 0.5f, v + half - 0.5f)
            }
            return out
        }

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
