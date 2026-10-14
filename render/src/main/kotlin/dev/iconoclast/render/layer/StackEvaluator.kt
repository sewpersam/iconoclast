package dev.iconoclast.render.layer

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.EffectKey
import dev.iconoclast.render.Mask
import dev.iconoclast.render.MaskCache
import dev.iconoclast.render.NoiseKey
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.background.Paints
import dev.iconoclast.render.effect.EffectPaint
import dev.iconoclast.render.fill.GlyphMasks
import dev.iconoclast.render.mask.Blur
import dev.iconoclast.render.texture.TextureStore

/** What the stack reads for one icon at one size. Masks come from the cache, read-only. */
internal class StackInput(
    /**
     * The icon's one per-icon offset: texture and noise layers, the streak breakup and grain
     * all read it, nothing derives its own. From the slot's seed name unless overridden.
     */
    val offset: TextureOffset,
    val size: Int,
    /** Grain cells across the canvas at grain scale 1: the pack's pixels, OutputResolution.px. */
    val grainCells: Int,
    val masks: GlyphMasks,
    /** Body union lines, computed on first use. */
    val icon: () -> Mask?,
    /** The background shape's coverage; null when the background is off. */
    val shape: Mask?,
    /** The border's coverage and premultiplied colour, drawn at the effects layer. */
    val border: Mask?,
    val borderColor: FloatArray?,
    val textures: TextureStore,
    /** Where effects cache what they derive from the icon mask, under [iconKey] and a kind. */
    val cache: MaskCache = MaskCache(),
    val iconKey: EffectKey? = null,
    /**
     * Whether noise paints read their whole-canvas field from [cache]. True at preview
     * sizes, where the same icon is drawn again on every change; at pack size each icon is
     * drawn once, so the field is evaluated only where the layer can show.
     */
    val cacheNoise: Boolean = false,
    /** IconSeed of the slot's seed name: a mapping's per-icon draws, layer/Mappings.place. */
    val iconSeed: Long = 0L,
    /** Whether [offset] is pinned by a per-icon override rather than drawn from the seed name. */
    val pinned: Boolean = false,
) {
    /** [mapping] placed for this icon. */
    fun place(mapping: Mapping): Placement = Mappings.place(mapping, offset, iconSeed, pinned)
}

/**
 * Step 4 of spec 3.3: paints the layers bottom to top into a premultiplied RGBA buffer.
 * Per pixel a layer's coverage is its clip target (or the inverse) times its matte times
 * its opacity; the source scaled by that coverage is composited with the layer's blend
 * mode, layer/BlendModes. An enabled effect layer paints its outputs (effect/EffectPaint)
 * the same way at its opacity, and adds them to the EFFECTS clip target. Masks are only
 * read; effects copy before they write.
 */
internal object StackEvaluator {

    fun paint(buf: FloatArray, layers: List<Layer>, input: StackInput, pass: Pass = Pass(input.size * input.size)) {
        for (layer in layers) when (layer) {
            is EffectsLayer -> input.border?.let { over(buf, it, input.borderColor!!) }
            is PaintLayer -> paint(buf, layer, input, pass)
            is EffectLayer -> if (layer.enabled && layer.opacity > 0f) {
                val e = layer.effect
                if (e is Effect.Blur) blur(buf, e, layer.opacity, input, pass)
                else for (out in EffectPaint.outputs(layer, layers, input)) paintEffect(buf, out, layer.opacity, pass)
            }
        }
    }

    /**
     * State carried up the stack. [effects] is the EFFECTS clip target: the union,
     * `e + c - e c`, of every enabled effect output painted so far, each output's coverage
     * times its layer's opacity. Null until an effect paints.
     */
    class Pass(val pixels: Int) {
        var effects: FloatArray? = null
    }

    /**
     * What an effect paints: per-pixel [coverage], 0 to 1 (clamped there), and a premultiplied colour,
     * one ([solid]) or one per pixel ([colors]), composited with [blend].
     */
    class EffectOutput(
        val coverage: FloatArray, val solid: FloatArray?, val colors: FloatArray?, val blend: BlendMode,
        /**
         * Source-atop, Porter-Duff: `co = ab x + (1 - as) cb`, `ao = ab`, x the blended
         * source, so the output lands only on what is already painted and keeps its alpha.
         * Over an opaque backdrop that is source-over; over a transparent one, nothing.
         * Grain uses it.
         */
        val atop: Boolean = false,
    )

    /**
     * Composites [out] at [opacity] into [buf] and adds it to the EFFECTS clip target. The
     * colour times coverage times opacity goes through the blend, as a paint layer's does.
     */
    fun paintEffect(buf: FloatArray, out: EffectOutput, opacity: Float, pass: Pass) {
        val acc = pass.effects ?: FloatArray(pass.pixels).also { pass.effects = it }
        val scratch = FloatArray(3)
        val cv = out.coverage
        for (i in 0 until pass.pixels) {
            // Blurs by running sums can leave a rounding error either side of [0, 1].
            var cov = cv[i]
            if (cov <= 0f) continue
            if (cov > 1f) cov = 1f
            if (opacity != 1f) cov *= opacity
            val o = 4 * i
            val ab = buf[o + 3]
            val partial = out.atop && ab < 1f
            if (out.atop && ab == 0f) continue
            // Bookkeeping for exact atop: the backdrop before the composite.
            val br = buf[o]
            val bg = buf[o + 1]
            val bb = buf[o + 2]
            val e = acc[i]
            val shown = if (out.atop) cov * ab else cov
            acc[i] = e + shown - e * shown
            val s = out.solid
            val sa: Float
            if (s != null) {
                sa = s[3] * cov
                BlendModes.composite(out.blend, s[0] * cov, s[1] * cov, s[2] * cov, sa, buf, o, scratch)
            } else {
                val c = out.colors!!
                sa = c[o + 3] * cov
                BlendModes.composite(out.blend, c[o] * cov, c[o + 1] * cov, c[o + 2] * cov, sa, buf, o, scratch)
            }
            if (partial) {
                // Porter-Duff atop on premultiplied values, W3C Compositing 9.1.8 with the
                // blend: co = ab x' + (1 - as) cb, ao = ab, where x' = co_over - (1 - as) cb
                // is the blended source the source-over composite above just added.
                val keep = 1f - sa
                buf[o] = (ab * (buf[o] - keep * br) + keep * br).coerceIn(0f, ab)
                buf[o + 1] = (ab * (buf[o + 1] - keep * bg) + keep * bg).coerceIn(0f, ab)
                buf[o + 2] = (ab * (buf[o + 2] - keep * bb) + keep * bb).coerceIn(0f, ab)
                buf[o + 3] = ab
            }
        }
    }

    private fun paint(buf: FloatArray, layer: PaintLayer, input: StackInput, pass: Pass) {
        if (!layer.visible || layer.opacity == 0f) return
        val clip = clip(layer, input, pass)
        if (clip.empty) return
        val n = input.size * input.size
        // A feathered matte reads its neighbours, so then the source is needed everywhere.
        val feathered = layer.matte?.let { it.feather * input.size >= 0.5f } ?: false
        val src = source(layer.source, layer, input, needLuminance = layer.matte != null, need = if (feathered) null else clip.need())
        val matte = layer.matte?.let { Mattes.apply(it, src.luminance!!, input.size) }
        val opacity = layer.opacity
        val solid = src.solid
        val colors = src.colors
        val scratch = FloatArray(3)
        for (i in 0 until n) {
            var cov = clip.at(i)
            if (opacity != 1f) cov *= opacity
            if (matte != null) cov *= matte[i]
            if (cov == 0f) continue
            val o = 4 * i
            if (solid != null) {
                BlendModes.composite(layer.blend, solid[0] * cov, solid[1] * cov, solid[2] * cov, solid[3] * cov, buf, o, scratch)
            } else {
                val c = colors!!
                BlendModes.composite(layer.blend, c[o] * cov, c[o + 1] * cov, c[o + 2] * cov, c[o + 3] * cov, buf, o, scratch)
            }
        }
    }

    /**
     * A layer's source: one premultiplied colour, or one per pixel. [luminance] is what a
     * matte reads, taken from the source before any gradient map: the premultiplied
     * texels' Lum for a texture (so transparent reads as black), the raw value for noise,
     * the colour's Lum for solids and gradients. Present only when asked for.
     */
    private class Source(val solid: FloatArray?, val colors: FloatArray?, val luminance: FloatArray?)

    /** [need]: when given, only pixels where it is not 0 are evaluated; the rest are left 0. */
    private fun source(paint: Paint, layer: PaintLayer, input: StackInput, needLuminance: Boolean, need: FloatArray? = null): Source {
        val n = input.size * input.size
        return when (paint) {
            is Paint.Solid -> {
                val c = FloatArray(4).also { Paints.premultiply(paint.argb, it) }
                Source(c, null, if (needLuminance) FloatArray(n) { BlendModes.lum(c[0], c[1], c[2]) } else null)
            }
            is Paint.LinearGradient, is Paint.RadialGradient, is Paint.AngularGradient -> {
                val px = Paints.fill(paint, input.size)
                Source(null, px, if (needLuminance) lumOf(px, n) else null)
            }
            is Paint.Texture -> {
                val tex = checkNotNull(input.textures[paint.sha256]) { "texture ${paint.sha256} is not in the store" }
                val px = Mappings.texture(tex, input.place(layer.mapping), input.size, need)
                val lum = if (needLuminance) lumOf(px, n) else null
                paint.stops?.let { stops ->
                    val c = FloatArray(4)
                    for (o in px.indices step 4) {
                        val a = px[o + 3]
                        if (a == 0f) continue
                        Paints.sample(stops, BlendModes.lum(px[o], px[o + 1], px[o + 2]) / a, c)
                        for (k in 0..3) px[o + k] = c[k] * a
                    }
                }
                Source(null, px, lum)
            }
            is Paint.Noise -> {
                // The cached field is the whole canvas; outside [need] nothing reads it, so the
                // pixels are the same either way. Read-only: the gradient map writes to px.
                val placed = input.place(layer.mapping)
                val v = if (input.cacheNoise) {
                    input.cache.noise(NoiseKey(paint.kind, paint.octaves, paint.seed, placed, input.size, input.grainCells)) {
                        Mappings.noise(paint.kind, paint.octaves, paint.seed, placed, input.size, input.grainCells)
                    }
                } else {
                    Mappings.noise(paint.kind, paint.octaves, paint.seed, placed, input.size, input.grainCells, need)
                }
                val px = FloatArray(v.size * 4)
                val c = FloatArray(4)
                for (i in v.indices) {
                    if (need != null && need[i] == 0f) continue
                    Paints.sample(paint.stops, v[i], c)
                    c.copyInto(px, 4 * i)
                }
                Source(null, px, if (needLuminance) v else null)
            }
        }
    }

    /**
     * [layer]'s source at every pixel, premultiplied RGBA: under its mapping and the icon's
     * per-icon offset, gradient-mapped through its stops, and not clipped, matted or faded
     * by its opacity. What a layer paints on the whole canvas at full opacity. Streaks
     * take their paint from here. With [need], texture and noise skip the pixels where it
     * is 0 and leave them 0.
     */
    fun sourceColors(layer: PaintLayer, input: StackInput, need: FloatArray? = null): FloatArray {
        val src = source(layer.source, layer, input, needLuminance = false, need = need)
        src.colors?.let { return it }
        val c = src.solid!!
        val n = input.size * input.size
        return FloatArray(4 * n) { c[it and 3] }
    }

    private fun lumOf(px: FloatArray, n: Int) = FloatArray(n) { BlendModes.lum(px[4 * it], px[4 * it + 1], px[4 * it + 2]) }

    /** A clip target resolved to per-pixel coverage. */
    private class Clip(private val values: FloatArray?, private val full: Boolean, private val support: Boolean, private val inverse: Boolean) {
        val empty: Boolean get() = values == null && full == inverse

        /** Where the clip can be non-zero, as a mask, or null for everywhere. */
        fun need(): FloatArray? = if (inverse) null else values

        fun at(i: Int): Float {
            val base = when {
                values == null -> if (full) 1f else 0f
                support -> if (values[i] > 0f) 1f else 0f
                else -> values[i]
            }
            return if (inverse) 1f - base else base
        }
    }

    /**
     * [Effect.Blur]: the stack so far, blurred with sigma half the radius, mixed back in by
     * the clip times [opacity]. Each premultiplied channel is blurred with the edges read as
     * zero and divided by the blur of an all-ones plane, which is the kernel weight that
     * fell inside the tile: so the rim keeps its strength, as if the tile continued.
     */
    private fun blur(buf: FloatArray, e: Effect.Blur, opacity: Float, input: StackInput, pass: Pass) {
        val size = input.size
        val sigma = e.radius * size / 2f
        if (sigma < MIN_SIGMA) return
        val clip = clip(e.clip, e.clipInverse, input, pass)
        if (clip.empty) return
        val n = size * size
        val plane = FloatArray(n)
        val weight = Blur.gaussian(FloatArray(n) { 1f }, size, size, sigma)
        val blurred = FloatArray(4 * n)
        for (c in 0..3) {
            for (i in 0 until n) plane[i] = buf[4 * i + c]
            val b = Blur.gaussian(plane, size, size, sigma)
            for (i in 0 until n) blurred[4 * i + c] = b[i] / weight[i]
        }
        for (i in 0 until n) {
            var cov = clip.at(i)
            if (opacity != 1f) cov *= opacity
            if (cov == 0f) continue
            val o = 4 * i
            // Premultiplied channels stay within their alpha: clamp the rounding of the sums.
            val a = (buf[o + 3] + (blurred[o + 3].coerceIn(0f, 1f) - buf[o + 3]) * cov).coerceIn(0f, 1f)
            for (k in 0..2) buf[o + k] = (buf[o + k] + (blurred[o + k].coerceIn(0f, 1f) - buf[o + k]) * cov).coerceIn(0f, a)
            buf[o + 3] = a
        }
    }

    /** Below this sigma in pixels the blur moves nothing visibly. */
    private const val MIN_SIGMA = 0.2f

    private fun clip(layer: PaintLayer, input: StackInput, pass: Pass): Clip = clip(layer.clip, layer.clipInverse, input, pass)

    private fun clip(target: ClipTarget, inv: Boolean, input: StackInput, pass: Pass): Clip {
        fun of(m: Mask?) = Clip(m?.values, false, false, inv)
        return when (target) {
            ClipTarget.CANVAS -> Clip(null, true, false, inv)
            // The shape's anti-aliased edge is applied once, by step 5.
            ClipTarget.BACKGROUND -> Clip(input.shape?.values, false, true, inv)
            ClipTarget.ICON -> of(input.icon())
            ClipTarget.ICON_FILL -> of(input.masks.body)
            ClipTarget.ICON_LINES -> of(input.masks.lines)
            ClipTarget.EFFECTS -> Clip(pass.effects, false, false, inv)
        }
    }

    /** Source-over of premultiplied colour [c] through coverage [mask]: the border. */
    private fun over(buf: FloatArray, mask: Mask, c: FloatArray) {
        val v = mask.values
        val scratch = FloatArray(3)
        for (i in v.indices) {
            val m = v[i]
            if (m == 0f) continue
            BlendModes.composite(BlendMode.NORMAL, c[0] * m, c[1] * m, c[2] * m, c[3] * m, buf, 4 * i, scratch)
        }
    }
}
