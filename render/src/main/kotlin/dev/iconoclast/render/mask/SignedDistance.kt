package dev.iconoclast.render.mask

import dev.iconoclast.render.math.sqrt

/**
 * Signed distance to the edge of a coverage mask, in pixels, positive inside. The bevel
 * reads its surface from it, so it must be smooth: lit by a low light, every step in the
 * field shows as a band.
 *
 * [binary] is the plain construction, the inside EDT minus the outside EDT of the mask
 * thresholded at one half. Its edge is the pixel staircase, so near the edge the field
 * moves in steps of up to a pixel and its gradient turns in jumps; SignedDistanceTest
 * measures the banding. It is kept as that reference.
 */
object SignedDistance {

    /** Distance given where the mask has no edge at all: everything inside or everything out. */
    const val NO_EDGE = 1e6f

    /** The reference: inside EDT minus outside EDT on the mask thresholded at 0.5, each a half pixel to the boundary. */
    fun binary(cov: FloatArray, w: Int, h: Int): FloatArray {
        val inside = BooleanArray(w * h) { cov[it] >= 0.5f }
        val toOutside = squaredEdt(BooleanArray(w * h) { !inside[it] }, w, h)
        val toInside = squaredEdt(inside, w, h)
        return FloatArray(w * h) {
            if (inside[it]) {
                if (toOutside[it] >= FAR) NO_EDGE else sqrt(toOutside[it]) - 0.5f
            } else {
                if (toInside[it] >= FAR) -NO_EDGE else 0.5f - sqrt(toInside[it])
            }
        }
    }
}
