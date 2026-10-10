package dev.iconoclast.render.mask

/**
 * Binary morphology with a true disk of radius r pixels, by thresholding an exact distance
 * transform. One transform per operation, whatever the radius, and round where iterated
 * 3x3 dilation would be square. Gap closing in [dev.iconoclast.render.fill.FillModes]
 * is built from dilations only.
 */
object Morphology {

    /** Pixels within [r] of the mask. */
    fun dilate(mask: BooleanArray, w: Int, h: Int, r: Float): BooleanArray {
        if (r <= 0f) return mask.copyOf()
        val d = squaredEdt(mask, w, h)
        val r2 = r * r
        return BooleanArray(w * h) { d[it] <= r2 }
    }
}
