package dev.icondesigner.render.raster

import dev.icondesigner.render.IconRasterizer
import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.geom.Affine

/**
 * The production [IconRasterizer]: the icon's own strokes and fills, the 48-unit canvas
 * mapped onto the full square. Pure Kotlin, so the JVM and the phone give the same bytes.
 * Stateless and thread-safe.
 *
 * [supersample] draws that many times larger and box-filters down. The conversion gate
 * uses 4, the treatment its inkscape reference gets: an SVG renderer composites each
 * primitive source-over, so stacked primitives darken their shared edge pixels, and how
 * much depends on the pixel size. Matching the reference's pixel size isolates geometry.
 */
class ScanlineRasterizer(private val supersample: Int = 1) : IconRasterizer {
    override fun rasterize(icon: Icon, size: Int): ByteArray {
        val big = size * supersample
        val coverage = rasterizeGlyph(icon, GlyphSpec(Affine(big / 48f, 0f, 0f)), big)
        return toBytes(if (supersample == 1) coverage else boxDown(coverage, big, supersample))
    }
}

/** Averages [factor] x [factor] blocks of a [big] x [big] mask. */
fun boxDown(mask: FloatArray, big: Int, factor: Int): FloatArray {
    val size = big / factor
    val out = FloatArray(size * size)
    val inv = 1f / (factor * factor)
    for (y in 0 until size) for (x in 0 until size) {
        var sum = 0f
        for (dy in 0 until factor) {
            val row = (y * factor + dy) * big + x * factor
            for (dx in 0 until factor) sum += mask[row + dx]
        }
        out[y * size + x] = sum * inv
    }
    return out
}
