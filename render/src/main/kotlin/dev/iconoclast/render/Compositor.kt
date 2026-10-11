package dev.iconoclast.render

import dev.iconoclast.render.background.BackgroundShapes
import dev.iconoclast.render.background.Paints
import dev.iconoclast.render.fill.FillInput
import dev.iconoclast.render.fill.FillModes
import dev.iconoclast.render.fill.GlyphMasks
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.geom.Affine
import dev.iconoclast.render.png.PngEncoder
import dev.iconoclast.render.raster.GlyphSpec
import dev.iconoclast.render.raster.rasterizeGlyph

/** Premultiplied RGBA, 8 bits per channel, row-major, [size] x [size]. */
class RgbaImage(val size: Int, val pixels: ByteArray)

/** Pack icon size in pixels: the 108 dp adaptive canvas at xxxhdpi. See render/README.md. */
const val OUTPUT_SIZE = 432

/** The adaptive-icon safe zone: a 66 dp circle on the 108 dp canvas. */
const val SAFE_ZONE = 66f / 108f

/**
 * The renderer, spec 3.3. Pure and deterministic: the same icon, theme and size give the
 * same pixels and the same PNG bytes, on the JVM and on the phone. Thread-safe; share one
 * instance, and its [cache], across the app.
 *
 * Pipeline:
 * 1. geometry mask: the line art under the shape parameters
 * 2. fill mode mask: body and lines from the geometry
 * 3. background: paint and border, unclipped
 * 4. layers and effects: in M2 the body and line masks in their glyph paints
 * 5. the alpha of the background shape clips everything
 */
class IconRenderer(val cache: MaskCache = MaskCache()) {

    /**
     * Renders one drawable. [drawable] is its stable identity, the Arcticons drawable name:
     * it keys the mask cache and, through [IconSeed], seeds every per-icon choice, so the
     * same name must always mean the same icon. The bundle index is not stable across
     * Arcticons updates and is not used.
     */
    fun render(drawable: String, icon: Icon, theme: Theme, size: Int): RgbaImage {
        val masks = glyphMasks(drawable, icon, theme, size)
        val buf = FloatArray(size * size * 4)
        val bg = theme.background
        val clip = if (bg.enabled) drawBackground(buf, bg, size) else null
        drawGlyph(buf, masks, theme.glyph)
        if (clip != null) for (i in 0 until clip.pixels) {
            val a = clip.values[i]
            if (a == 1f) continue
            for (k in 0..3) buf[4 * i + k] *= a
        }
        return RgbaImage(size, ByteArray(buf.size) { (buf[it] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() })
    }

    /** [render], encoded as PNG. */
    fun renderPng(drawable: String, icon: Icon, theme: Theme, size: Int): ByteArray =
        PngEncoder.encode(render(drawable, icon, theme, size))

    /**
     * Steps 1 and 2, cached. The geometry, the fill masks and invert fill's lone lines are
     * cached apart, so the fallback threshold and the lone-lines switch reuse the masks.
     */
    fun glyphMasks(drawable: String, icon: Icon, theme: Theme, size: Int): GlyphMasks {
        val shape = theme.shape
        val fill = theme.fill
        val key = FillKey.of(drawable, shape, fill, size)
        val geometry = { cache.geometry(GeometryKey(drawable, shape, size)) { Mask(rasterizeGlyph(icon, glyphSpec(shape, size), size)) } }
        val input = { cut: FloatArray? -> fillInput(geometry().values, shape, fill, size, cut) }
        return when (fill.mode) {
            FillMode.OUTLINE -> GlyphMasks(null, geometry())
            FillMode.SILHOUETTE -> GlyphMasks(cache.fill(key) { Mask(FillModes.silhouette(input(null))) }, null)
            FillMode.OUTLINE_SILHOUETTE -> GlyphMasks(cache.fill(key) { Mask(FillModes.silhouette(input(null))) }, geometry())
            FillMode.INVERT -> {
                val body = cache.fill(key) {
                    val cut = rasterizeGlyph(
                        icon,
                        glyphSpec(shape, size, extraWidth = 2 * fill.gapWidth, cap = LineCap.ROUND, join = LineJoin.ROUND),
                        size,
                    )
                    Mask(FillModes.invertBody(input(cut)))
                }
                val g = geometry()
                FillModes.invertMasks(g, body, fill.fallbackThreshold) {
                    if (!fill.keepLoneLines) null
                    else cache.loneLines(LoneLinesKey(key)) {
                        Mask(FillModes.loneLines(g.values, body.values, fillInput(g.values, shape, fill, size, null)))
                    }
                }
            }
        }
    }

    /**
     * The icon clip target: body union lines, `b + l - b * l`, the coverage both masks give
     * painted in one opaque colour. Cached under [EffectKey]. Null when both are absent.
     */
    fun iconMask(drawable: String, icon: Icon, theme: Theme, size: Int): Mask? {
        val masks = glyphMasks(drawable, icon, theme, size)
        val b = masks.body
        val l = masks.lines
        if (b == null || l == null) return b ?: l
        val key = EffectKey.of(FillKey.of(drawable, theme.shape, theme.fill, size), masks.fellBack, theme.fill.keepLoneLines)
        return cache.effect(key) {
            val bv = b.values
            val lv = l.values
            Mask(FloatArray(bv.size) { bv[it] + lv[it] - bv[it] * lv[it] })
        }
    }

    private fun fillInput(geometry: FloatArray, shape: ShapeParams, fill: FillParams, size: Int, cut: FloatArray?): FillInput {
        val scale = unitScale(shape, size)
        return FillInput(
            geometry, size,
            strokePx = shape.strokeWeight * scale,
            gapPx = fill.gapWidth * scale,
            closingPx = fill.gapClosing * scale,
            cut = cut,
        )
    }

    /** Step 3. Returns the shape coverage for step 5. */
    private fun drawBackground(buf: FloatArray, bg: BackgroundParams, size: Int): Mask {
        val (shape, border) = cache.background(BackgroundKey(bg.shape, bg.size, bg.border?.width ?: 0f, size)) {
            val m = BackgroundShapes.mask(bg.shape, bg.size, size)
            Mask(m) to bg.border?.takeIf { it.width > 0f }?.let { Mask(BackgroundShapes.border(bg.shape, bg.size, it.width, size, m)) }
        }
        Paints.fill(bg.paint, size).copyInto(buf)
        if (border != null) {
            val c = FloatArray(4).also { Paints.premultiply(bg.border!!.argb, it, bg.border.opacity) }
            over(buf, border, c)
        }
        return shape
    }

    /** Step 4. Layers and effects take over here: they paint the same masks in stack order. */
    private fun drawGlyph(buf: FloatArray, masks: GlyphMasks, paint: GlyphPaint) {
        val c = FloatArray(4)
        masks.body?.let { Paints.premultiply(paint.body, c); over(buf, it, c) }
        masks.lines?.let { Paints.premultiply(paint.line, c); over(buf, it, c) }
    }

    /** Source-over of premultiplied colour [c] through coverage [mask]. */
    private fun over(buf: FloatArray, mask: Mask, c: FloatArray) {
        val v = mask.values
        for (i in v.indices) {
            val m = v[i]
            if (m == 0f) continue
            val inv = 1f - c[3] * m
            val o = 4 * i
            buf[o] = c[0] * m + buf[o] * inv
            buf[o + 1] = c[1] * m + buf[o + 1] * inv
            buf[o + 2] = c[2] * m + buf[o + 2] * inv
            buf[o + 3] = c[3] * m + buf[o + 3] * inv
        }
    }

    companion object {
        /** Pixels per 48-grid unit: the icon box is the safe zone times the icon scale. */
        fun unitScale(shape: ShapeParams, size: Int): Float = size * SAFE_ZONE * shape.iconScale / 48f

        fun glyphSpec(
            shape: ShapeParams, size: Int, extraWidth: Float = 0f,
            cap: LineCap = shape.cap, join: LineJoin = shape.join,
        ): GlyphSpec {
            val scale = unitScale(shape, size)
            val origin = (size - 48f * scale) / 2
            return GlyphSpec(
                Affine(scale, origin + shape.offsetX * size, origin + shape.offsetY * size),
                strokeScale = shape.strokeWeight,
                extraWidth = extraWidth,
                cap = cap,
                join = join,
                miterLimit = shape.miterLimit,
                dotScale = if (shape.dots == DotMode.SCALE_WITH_STROKE) shape.strokeWeight else 1f,
            )
        }
    }
}
