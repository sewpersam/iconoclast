package dev.icondesigner.render.mask

/**
 * Binary morphology with a true disk of radius [r] pixels, by thresholding exact distance
 * transforms. One transform per operation, whatever the radius, and round where iterated
 * 3x3 dilation would be square.
 */
object Morphology {

    /** Pixels within [r] of the mask. */
    fun dilate(mask: BooleanArray, w: Int, h: Int, r: Float): BooleanArray {
        if (r <= 0f) return mask.copyOf()
        val d = squaredEdt(mask, w, h)
        val r2 = r * r
        return BooleanArray(w * h) { d[it] <= r2 }
    }

    /** Mask pixels farther than [r] from every non-mask pixel. Outside the grid does not erode. */
    fun erode(mask: BooleanArray, w: Int, h: Int, r: Float): BooleanArray {
        if (r <= 0f) return mask.copyOf()
        val d = squaredEdt(BooleanArray(w * h) { !mask[it] }, w, h)
        val r2 = r * r
        return BooleanArray(w * h) { d[it] > r2 }
    }

    /** Dilate then erode: seals gaps up to about 2r wide and leaves the rest. */
    fun close(mask: BooleanArray, w: Int, h: Int, r: Float): BooleanArray =
        if (r <= 0f) mask.copyOf() else erode(dilate(mask, w, h, r), w, h, r)
}
