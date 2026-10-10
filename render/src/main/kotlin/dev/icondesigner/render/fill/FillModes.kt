package dev.icondesigner.render.fill

import dev.icondesigner.render.FillMode
import dev.icondesigner.render.mask.Morphology
import dev.icondesigner.render.mask.label4
import dev.icondesigner.render.mask.squaredEdt
import dev.icondesigner.render.mask.touchesBorder
import kotlin.math.sqrt

/**
 * The fill stage's output, coverage 0 to 1 per pixel. [body] is painted first, then
 * [lines] on top. Either may be absent. [fellBack] is true when invert fill gave way to
 * Outline under the fallback threshold.
 */
class GlyphMasks(val body: FloatArray?, val lines: FloatArray?, val fellBack: Boolean = false)

/** Fill-stage inputs in pixels. [cut] is the line art drawn [gapPx] wider on every side. */
class FillInput(
    val geometry: FloatArray,
    val size: Int,
    val strokePx: Float,
    val gapPx: Float,
    val closingPx: Float,
    val cut: FloatArray?,
)

/**
 * The four fill modes of spec 2.3 on a geometry mask. Invert fill follows spec 1.4:
 * close small gaps, label the non-stroke regions, keep those that do not touch the edge,
 * and take away the strokes widened by the gap.
 */
object FillModes {

    fun apply(mode: FillMode, input: FillInput, fallbackThreshold: Float, keepLoneLines: Boolean): GlyphMasks =
        when (mode) {
            FillMode.OUTLINE -> GlyphMasks(null, input.geometry)
            FillMode.SILHOUETTE -> GlyphMasks(silhouette(input), null)
            FillMode.OUTLINE_SILHOUETTE -> GlyphMasks(silhouette(input), input.geometry)
            FillMode.INVERT -> invert(input, fallbackThreshold, keepLoneLines)
        }

    /** Enclosed regions plus the strokes. */
    fun silhouette(input: FillInput): FloatArray {
        val g = input.geometry
        val inner = interior(input)
        return FloatArray(g.size) { if (inner[it]) 1f else g[it] }
    }

    /**
     * Enclosed regions minus the widened strokes, anti-aliased by the cut's own edge. Below
     * the fallback threshold the icon renders as Outline. Otherwise, with [keepLoneLines],
     * strokes that border no fill are returned as lines to draw on top.
     */
    fun invert(input: FillInput, fallbackThreshold: Float, keepLoneLines: Boolean): GlyphMasks {
        val g = input.geometry
        val cut = requireNotNull(input.cut) { "invert fill needs the cut mask" }
        val inner = interior(input)
        val fill = FloatArray(g.size) { if (inner[it]) 1f - cut[it] else 0f }
        if (fill.sum() < fallbackThreshold * g.sum()) return GlyphMasks(null, g, fellBack = true)
        return GlyphMasks(fill, if (keepLoneLines) loneLines(g, fill, input) else null)
    }

    /**
     * Stroke coverage beyond the reach of any fill. A stroke that borders fill lies within
     * a gap and a stroke of it, times root 2 where it wraps a right-angled fill corner, so
     * only unenclosed lines survive. The cut-off ramps over one pixel so the shortened
     * ends stay anti-aliased.
     */
    fun loneLines(g: FloatArray, fill: FloatArray, input: FillInput): FloatArray {
        val size = input.size
        val d2 = squaredEdt(BooleanArray(fill.size) { fill[it] >= 0.5f }, size, size)
        val reach = (input.gapPx + input.strokePx / 2) * SQRT2 + input.strokePx / 2 + 1f
        return FloatArray(g.size) {
            if (g[it] == 0f) 0f else g[it] * (sqrt(d2[it]) - reach).coerceIn(0f, 1f)
        }
    }

    private const val SQRT2 = 1.4142135f

    /**
     * Enclosed non-stroke pixels, grown by one pixel into the stroke so the fill meets the
     * stroke's anti-aliased edge without a seam.
     *
     * Gap closing is an opening of each enclosed region rather than a closing of the
     * strokes. The two agree away from gaps, but a pixel-grid closing reopens the gap
     * between two thin stroke ends: disks slide in from both sides and leave a seal
     * thinner than a pixel. Here the strokes are dilated by the closing radius, the
     * remaining regions labelled, and only the enclosed ones grown back by that radius,
     * so nothing from outside can reclaim the gap.
     */
    private fun interior(input: FillInput): BooleanArray {
        val size = input.size
        val g = input.geometry
        val n = g.size
        val stroke = BooleanArray(n) { g[it] >= 0.5f }
        val r = input.closingPx
        val barrier = if (r > 0f) Morphology.dilate(stroke, size, size, r) else stroke
        val labels = label4(BooleanArray(n) { !barrier[it] }, size, size)
        val outside = touchesBorder(labels, size, size)
        val l = labels.labels
        var enclosed = BooleanArray(n) { l[it] != 0 && !outside[l[it]] }
        if (r > 0f) {
            val grown = Morphology.dilate(enclosed, size, size, r)
            enclosed = BooleanArray(n) { grown[it] && !stroke[it] }
        }
        return BooleanArray(n) { p ->
            enclosed[p] || stroke[p] && (
                (p % size > 0 && enclosed[p - 1]) || (p % size < size - 1 && enclosed[p + 1]) ||
                    (p >= size && enclosed[p - size]) || (p < n - size && enclosed[p + size])
                )
        }
    }
}
