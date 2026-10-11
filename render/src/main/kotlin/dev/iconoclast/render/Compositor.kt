package dev.iconoclast.render

import dev.iconoclast.render.background.BackgroundShapes
import dev.iconoclast.render.background.Paints
import dev.iconoclast.render.fill.FillInput
import dev.iconoclast.render.fill.FillModes
import dev.iconoclast.render.fill.GlyphMasks
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.geom.Affine
import dev.iconoclast.render.layer.StackEvaluator
import dev.iconoclast.render.layer.StackInput
import dev.iconoclast.render.png.PngEncoder
import dev.iconoclast.render.raster.GlyphSpec
import dev.iconoclast.render.raster.rasterizeGlyph
import dev.iconoclast.render.texture.TextureStore

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
 * 3. background: the shape's coverage and border; its paint is the background layer
 * 4. layers and effects in stack order, layer/StackEvaluator; the border is drawn at the
 *    effects layer
 * 5. the alpha of the background shape clips everything
 *
 * Texture layers read [textures]; a theme's textures must be in the store before it
 * renders. Textures never enter the mask cache.
 */
class IconRenderer(
    val cache: MaskCache = MaskCache(),
    val textures: TextureStore = TextureStore(),
    /** Below this size noise fields are cached per icon, [StackInput.cacheNoise]. 0 turns it off. */
    private val noiseCacheBelow: Int = OUTPUT_SIZE,
) {

    /**
     * Renders one icon. Two names identify it, both Arcticons drawable names, stable across
     * Arcticons updates where the bundle index is not:
     * - [geometryName] names the line art in [icon]. It alone keys the geometry, the fill
     *   masks and everything derived from them in [cache], so one name always means one
     *   geometry, whatever slot draws it.
     * - [seedName] names the slot the icon is drawn for. Through [IconSeed] it seeds every
     *   per-icon choice, the texture and noise offset among them.
     *
     * Unswapped, both are the same name. A per-icon swap draws another drawable's geometry
     * in this slot: [geometryName] is the swapped-in drawable, [seedName] stays the slot's,
     * so the swap shares the other drawable's cached masks without changing them.
     */
    fun render(geometryName: String, icon: Icon, seedName: String, theme: Theme, size: Int): RgbaImage {
        val buf = FloatArray(size * size * 4)
        val input = stackInput(geometryName, icon, seedName, theme, size)
        StackEvaluator.paint(buf, theme.layers, input)
        val clip = input.shape
        if (clip != null) for (i in 0 until clip.pixels) {
            val a = clip.values[i]
            if (a == 1f) continue
            for (k in 0..3) buf[4 * i + k] *= a
        }
        return RgbaImage(size, ByteArray(buf.size) { (buf[it] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte() })
    }

    /** An icon drawn in its own slot: [drawable] is both the geometry and the seed name. */
    fun render(drawable: String, icon: Icon, theme: Theme, size: Int): RgbaImage = render(drawable, icon, drawable, theme, size)

    /** What step 4 reads for one icon: steps 1 to 3, cached, and the keys effects derive from. */
    internal fun stackInput(geometryName: String, icon: Icon, seedName: String, theme: Theme, size: Int): StackInput {
        val masks = glyphMasks(geometryName, icon, theme, size)
        val bg = theme.background
        val shape = if (bg.enabled) backgroundMasks(bg, size) else null
        val border = shape?.second
        return StackInput(
            seedName, size, masks,
            icon = { iconMask(geometryName, theme, size, masks) },
            shape = shape?.first,
            border = border,
            borderColor = border?.let { FloatArray(4).also { c -> Paints.premultiply(bg.border!!.argb, c, bg.border.opacity) } },
            textures = textures,
            cache = cache,
            iconKey = iconKey(geometryName, theme, size, masks),
            cacheNoise = size < noiseCacheBelow,
        )
    }

    /** [render], encoded as PNG. */
    fun renderPng(geometryName: String, icon: Icon, seedName: String, theme: Theme, size: Int): ByteArray =
        PngEncoder.encode(render(geometryName, icon, seedName, theme, size))

    /** [render] of an icon in its own slot, encoded as PNG. */
    fun renderPng(drawable: String, icon: Icon, theme: Theme, size: Int): ByteArray = renderPng(drawable, icon, drawable, theme, size)

    /**
     * Steps 1 and 2, cached. The geometry, the fill masks and invert fill's lone lines are
     * cached apart, so the fallback threshold and the lone-lines switch reuse the masks.
     * Keyed by [geometryName] alone: no per-icon choice reaches these steps.
     */
    fun glyphMasks(geometryName: String, icon: Icon, theme: Theme, size: Int): GlyphMasks {
        val shape = theme.shape
        val fill = theme.fill
        val key = FillKey.of(geometryName, shape, fill, size)
        val geometry = { cache.geometry(GeometryKey(geometryName, shape, size)) { Mask(rasterizeGlyph(icon, glyphSpec(shape, size), size)) } }
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
    fun iconMask(geometryName: String, icon: Icon, theme: Theme, size: Int): Mask? =
        iconMask(geometryName, theme, size, glyphMasks(geometryName, icon, theme, size))

    private fun iconMask(geometryName: String, theme: Theme, size: Int, masks: GlyphMasks): Mask? {
        val b = masks.body
        val l = masks.lines
        if (b == null || l == null) return b ?: l
        return cache.effect(iconKey(geometryName, theme, size, masks)) {
            val bv = b.values
            val lv = l.values
            Mask(FloatArray(bv.size) { bv[it] + lv[it] - bv[it] * lv[it] })
        }
    }

    /** The key of the icon mask, which every effect's derived data extends with its kind. */
    private fun iconKey(geometryName: String, theme: Theme, size: Int, masks: GlyphMasks) =
        EffectKey.of(FillKey.of(geometryName, theme.shape, theme.fill, size), masks.fellBack, theme.fill.keepLoneLines)

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

    /** Step 3: the shape's coverage, for the background clip target and step 5, and the border's. */
    private fun backgroundMasks(bg: BackgroundParams, size: Int): Pair<Mask, Mask?> =
        cache.background(BackgroundKey(bg.shape, bg.size, bg.border?.width ?: 0f, size)) {
            val m = BackgroundShapes.mask(bg.shape, bg.size, size)
            Mask(m) to bg.border?.takeIf { it.width > 0f }?.let { Mask(BackgroundShapes.border(bg.shape, bg.size, it.width, size, m)) }
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
