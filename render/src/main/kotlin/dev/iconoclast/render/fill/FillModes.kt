package dev.iconoclast.render.fill

import dev.iconoclast.render.FillMode
import dev.iconoclast.render.mask.Morphology
import dev.iconoclast.render.mask.label4
import dev.iconoclast.render.mask.squaredEdt
import dev.iconoclast.render.mask.touchesBorder
import kotlin.math.ceil
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

    /**
     * Enclosed regions plus the strokes: solid everywhere except the outside and the
     * stroke pixels on its edge, which keep their anti-aliased coverage.
     */
    fun silhouette(input: FillInput): FloatArray {
        val g = input.geometry
        val size = input.size
        val r = regions(input)
        val outside = BooleanArray(g.size) { !r.stroke[it] && !r.enclosed[it] }
        return FloatArray(g.size) {
            if (outside[it] || r.stroke[it] && touches(outside, it, size)) g[it] else 1f
        }
    }

    /**
     * Enclosed regions minus the widened strokes, anti-aliased by the cut's own edge. Below
     * the fallback threshold the icon renders as Outline. Otherwise, with [keepLoneLines],
     * strokes that border no fill are returned as lines to draw on top.
     */
    fun invert(input: FillInput, fallbackThreshold: Float, keepLoneLines: Boolean): GlyphMasks {
        val body = invertBody(input)
        return invertMasks(input.geometry, body, fallbackThreshold) { if (keepLoneLines) loneLines(input.geometry, body, input) else null }
    }

    /**
     * The invert fill before the fallback test: enclosed regions minus the cut. Depends on
     * the geometry, gap and closing only, so [IconRenderer][dev.iconoclast.render.IconRenderer]
     * caches it apart from the threshold and the lone-lines switch.
     */
    fun invertBody(input: FillInput): FloatArray {
        val g = input.geometry
        val cut = requireNotNull(input.cut) { "invert fill needs the cut mask" }
        val r = regions(input)
        val size = input.size
        // Grown one pixel into the stroke, so the fill meets the cut's edge without a seam.
        return FloatArray(g.size) {
            if (r.enclosed[it] || r.stroke[it] && touches(r.enclosed, it, size)) 1f - cut[it] else 0f
        }
    }

    /** Applies the fallback threshold to [body]. [lines] supplies the lone lines when the fill stands. */
    fun invertMasks(geometry: FloatArray, body: FloatArray, fallbackThreshold: Float, lines: () -> FloatArray?): GlyphMasks {
        if (body.sum() < fallbackThreshold * geometry.sum()) return GlyphMasks(null, geometry, fellBack = true)
        return GlyphMasks(body, lines())
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

    /** [stroke]: geometry at or above half coverage. [enclosed]: non-stroke pixels not outside. */
    private class Regions(val stroke: BooleanArray, val enclosed: BooleanArray)

    /**
     * Gap closing is built from dilations rather than a closing of the strokes. A
     * pixel-grid closing reopens the gap between two thin stroke ends: disks slide in from
     * both sides and leave a seal thinner than a pixel. Here the strokes are dilated by a
     * closing radius and the remaining regions labelled. Each side is then grown back by
     * that radius. Where both reach, as in a sealed gap, the enclosed side wins.
     *
     * Sealing is monotone in the closing: a region enclosed at a smaller radius stays
     * enclosed at a larger one. A wide closing can bury a sealed region under the dilated
     * strokes entirely, as the band inside signal's dashed ring at 2 units, so the regions
     * are labelled at every radius from 0 up to the closing in steps of at most a pixel.
     * An inside that vanishes between two steps is grown at the radius it last had and
     * kept. A region is enclosed when some radius
     * seals its openings while its core is still wider than the seal.
     *
     * Pixels no step reaches and the grown outside does not reach are pockets: corners
     * where strokes meet, channels narrower than the seal. A pocket belongs to the inside
     * when it borders a grown inside, or borders nothing reached at all, as a small closed
     * loop does. A pocket that borders only the grown outside is an exterior notch, such
     * as a channel whose mouth is as wide as itself: no radius seals it before it is
     * buried, and it stays outside.
     */
    private fun regions(input: FillInput): Regions {
        val size = input.size
        val g = input.geometry
        val n = g.size
        val stroke = BooleanArray(n) { g[it] >= 0.5f }
        val r = input.closingPx
        val strokeD2 = if (r > 0f) squaredEdt(stroke, size, size) else null
        val steps = ceil(r).toInt()
        val sealed = BooleanArray(n)
        var outsideGrown = BooleanArray(n)
        // An inside that survives to the next step is grown at the last one; only insides
        // that vanish between steps are grown at their own radius. Corners the last
        // growth misses are pockets beside an inside, so enclosed either way.
        var prev: IntArray? = null
        var prevInside: BooleanArray? = null
        var prevRadius = 0f
        for (k in 0..steps) {
            val rk = if (steps == 0) 0f else r * k / steps
            val r2 = rk * rk
            val barrier = if (strokeD2 == null || k == 0) stroke else BooleanArray(n) { strokeD2[it] <= r2 }
            val labels = label4(BooleanArray(n) { !barrier[it] }, size, size)
            val border = touchesBorder(labels, size, size)
            val l = labels.labels
            val inside = BooleanArray(n) { l[it] != 0 && !border[l[it]] }
            if (prev != null && prevInside != null) {
                val survives = BooleanArray(prev.max() + 1)
                for (i in 0 until n) if (prevInside[i] && inside[i]) survives[prev[i]] = true
                val dying = BooleanArray(n) { prevInside[it] && !survives[prev[it]] }
                if (dying.any { it }) or(sealed, Morphology.dilate(dying, size, size, prevRadius))
            }
            if (k == steps) {
                or(sealed, Morphology.dilate(inside, size, size, rk))
                outsideGrown = Morphology.dilate(BooleanArray(n) { l[it] != 0 && border[l[it]] }, size, size, rk)
            }
            prev = l
            prevInside = inside
            prevRadius = rk
        }
        val pocket = BooleanArray(n) { !stroke[it] && !sealed[it] && !outsideGrown[it] }
        val exterior = exteriorPockets(pocket, sealed, outsideGrown, stroke, size)
        val enclosed = BooleanArray(n) {
            !stroke[it] && (sealed[it] || pocket[it] && !exterior[it])
        }
        return Regions(stroke, enclosed)
    }

    /** The [pocket] pixels whose component borders reached outside and no reached inside. */
    private fun exteriorPockets(
        pocket: BooleanArray, insideGrown: BooleanArray, outsideGrown: BooleanArray, stroke: BooleanArray, size: Int,
    ): BooleanArray {
        val pl = label4(pocket, size, size)
        val l = pl.labels
        val nearInside = BooleanArray(pl.count + 1)
        val nearOutside = BooleanArray(pl.count + 1)
        for (p in l.indices) {
            val id = l[p]
            if (id == 0) continue
            val x = p % size
            for (q in intArrayOf(if (x > 0) p - 1 else -1, if (x < size - 1) p + 1 else -1, p - size, p + size)) {
                if (q < 0 || q >= l.size || stroke[q] || pocket[q]) continue
                if (insideGrown[q]) nearInside[id] = true else if (outsideGrown[q]) nearOutside[id] = true
            }
        }
        return BooleanArray(l.size) { l[it] != 0 && nearOutside[l[it]] && !nearInside[l[it]] }
    }

    private fun or(into: BooleanArray, from: BooleanArray) {
        for (i in into.indices) if (from[i]) into[i] = true
    }

    /** Whether a 4-neighbour of pixel [p] is set in [mask]. */
    private fun touches(mask: BooleanArray, p: Int, size: Int): Boolean =
        (p % size > 0 && mask[p - 1]) || (p % size < size - 1 && mask[p + 1]) ||
            (p >= size && mask[p - size]) || (p < mask.size - size && mask[p + size])
}
