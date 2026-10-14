package dev.iconoclast.render

/**
 * One effect of spec 2.3 "Effects" at its own place in the stack. Like a [PaintLayer] it
 * has an [opacity] and a [blend] mode, and it can be switched off without losing its
 * settings. Effects read the icon mask, fill body union lines, never the body alone.
 *
 * The [EffectsLayer] marker stays: it anchors the background border and is where a new
 * effect goes by default ([Theme.withEffect]). Effects that paint outside the icon
 * (streaks, drop shadow, outer glow) belong below the icon layers; effects that paint on
 * the icon (bevel, inner shadow, inner glow) only show above them.
 *
 * Lengths are fractions of the canvas, so a 128 px preview and the 432 px pack agree.
 * Angles are degrees counterclockwise from the positive x axis, y up as on screen: 0 is
 * right, 90 up, 180 left, 270 down.
 */
data class EffectLayer(
    override val id: String,
    val effect: Effect,
    val enabled: Boolean = true,
    /** Not used by [Effect.Bevel], whose highlight and shadow carry their own blends. */
    val blend: BlendMode = BlendMode.NORMAL,
    /** 0 to 1. */
    val opacity: Float = 1f,
) : Layer {
    init {
        require(id.isNotBlank()) { "blank layer id" }
        require(opacity in 0f..1f) { "opacity $opacity" }
    }
}

/**
 * How a streak fades along its length, effect/Smear. The streak holds the strongest paint
 * dragged onto each pixel, so thin lines streak as strongly as solid bodies.
 */
enum class Decay {
    /** Held paint loses 1 / length per pixel: a straight fade to nothing at the full length. */
    LINEAR,

    /** Held paint times exp(-3 / length) per pixel: 5% left at the full length, a faint tail beyond. */
    EXPONENTIAL,
}

/** Which way [Effect.BrushStrokes] run. */
enum class StrokeDirection {
    /** Out from the icon's edge, along the flow of its outline, as schema 8 drew them. */
    OUTWARD,

    /** Parallel, along the effect's angle, in a band across the tile: paint swipes behind the icon. */
    PARALLEL,
}

/** How each [Effect.BrushStrokes] mark is drawn. */
enum class StrokeStyle {
    /** Schema 8's procedural mark. */
    CLASSIC,

    /**
     * Real paint: pressure at the start and lift at the end, a wobbling width, ragged
     * edges, uneven bristle streaks that run dry toward the tail, darker where strokes overlap.
     */
    PAINTED,
}

/** [Effect.Bevel]'s profile. */
enum class BevelStyle {
    /** Rises inside the icon from its edge over [Effect.Bevel.size]. */
    INNER,

    /** Rises across the edge, half outside and half inside, the icon standing out of the background. */
    EMBOSS,
}

/** The farthest a shadow sits from its icon, a fraction of the canvas, schema 9. */
const val MAX_SHADOW_DISTANCE = 0.25f

/** The effects of spec 2.3, each with its parameters and ranges. */
sealed interface Effect {

    /**
     * Brush streaks: the icon mask smeared along [angle] for [length], fading by [decay],
     * broken up by [breakup], painted with the source of the layer [paintLayer] names, or
     * [color] when it names no paint layer. The streak trails from the icon toward
     * [angle].
     */
    data class Streaks(
        /** 0 to 360 degrees: the direction the streaks run from the icon. */
        val angle: Float = 300f,
        /** 0 to 0.6 of the canvas. */
        val length: Float = 0.25f,
        val decay: Decay = Decay.EXPONENTIAL,
        /** Null: an unbroken smear. */
        val breakup: Breakup? = Breakup(),
        /**
         * Id of a [PaintLayer] whose source, under its own mapping and per-icon offset but
         * not its clip, matte or opacity, paints the streaks. Null, or an id that names no
         * paint layer: [color].
         */
        val paintLayer: String? = null,
        /** Straight ARGB. */
        val color: Int = 0xFFFFFFFF.toInt(),
    ) : Effect {
        init {
            require(angle in 0f..360f) { "streak angle $angle" }
            require(length in 0f..0.6f) { "streak length $length" }
        }
    }

    /**
     * Painterly brush strokes flicking off the icon, schema 5: [count] tapered, bristled
     * strokes that start on the icon's edge and travel outward along the flow of the
     * blurred icon mask, curving as they go, fading and blurring with distance. After
     * Hertzmann, "Painterly Rendering with Curved Brush Strokes of Multiple Sizes",
     * SIGGRAPH 1998: each stroke is a curve that follows a direction field, takes one
     * colour from the source at its start point, and is painted over the strokes before
     * it. Code: effect/BrushStrokes.
     *
     * Every random choice derives from the icon's one per-icon offset and [seed], so an
     * icon's strokes are the same at every size and on every device.
     */
    data class BrushStrokes(
        /** 1 to 400 strokes. */
        val count: Int = 90,
        /** 0.02 to 1.2 of the canvas, schema 8 up to 0.5: how far a stroke reaches. */
        val length: Float = 0.2f,
        /** 0.002 to 0.2 of the canvas, schema 8 up to 0.08: a stroke's width at its base. */
        val width: Float = 0.02f,
        /** 0 to 1: from crisp edges and bristles to soft ones. */
        val softness: Float = 0.3f,
        /** 0 to 1: how much the strokes blur with distance from the icon. */
        val blur: Float = 0.3f,
        /** 0 to 1: how much a stroke's paint runs out along its length. */
        val fade: Float = 0.6f,
        /** 0 to 1: variation of each stroke's direction, length, width, wobble and tone. */
        val randomness: Float = 0.5f,
        /** -1 to 1: strokes curl counterclockwise (positive) or clockwise as they travel. */
        val bend: Float = 0f,
        /** 0 to 1: dry-brush bristle marks, which thin out toward the tip. */
        val bristles: Float = 0.6f,
        /** 0 to 1: how far the strokes lean toward [angle] instead of straight off the edge. */
        val drift: Float = 0f,
        /** 0 to 360 degrees: the direction [drift] leans toward. */
        val angle: Float = 300f,
        /** As [Streaks.paintLayer]: the source paints each stroke with its colour at the stroke's start. */
        val paintLayer: String? = null,
        /** Straight ARGB. */
        val color: Int = 0xFFFFFFFF.toInt(),
        val seed: Int = 0,
        /** Schema 9: outward from the icon at [originX], [originY], or all one way along [angle]. */
        val direction: StrokeDirection = StrokeDirection.OUTWARD,
        /**
         * 0 to 1 of the canvas, y down. Outward: where the burst sits; the icon's own outline
         * moved there, so the centre is schema 8's strokes. All one way: the point the band
         * of strokes crosses.
         */
        val originX: Float = 0.5f,
        val originY: Float = 0.5f,
        /** Schema 9: how a stroke is drawn. Classic is schema 8's mark; Painted reads as real paint. */
        val style: StrokeStyle = StrokeStyle.CLASSIC,
    ) : Effect {
        init {
            require(originX in 0f..1f && originY in 0f..1f) { "brush stroke origin $originX,$originY" }
            require(count in 1..400) { "brush stroke count $count" }
            require(length in 0.02f..1.2f) { "brush stroke length $length" }
            require(width in 0.002f..0.2f) { "brush stroke width $width" }
            require(softness in 0f..1f) { "brush stroke softness $softness" }
            require(blur in 0f..1f) { "brush stroke blur $blur" }
            require(fade in 0f..1f) { "brush stroke fade $fade" }
            require(randomness in 0f..1f) { "brush stroke randomness $randomness" }
            require(bend in -1f..1f) { "brush stroke bend $bend" }
            require(bristles in 0f..1f) { "brush stroke bristles $bristles" }
            require(drift in 0f..1f) { "brush stroke drift $drift" }
            require(angle in 0f..360f) { "brush stroke angle $angle" }
        }
    }

    /**
     * Bevel and emboss from a signed sub-pixel distance field of the icon mask. The light
     * comes from [lightAngle] at a fixed 30 degree altitude.
     */
    data class Bevel(
        val style: BevelStyle = BevelStyle.INNER,
        /** 0 to 360 degrees: where the light comes from. */
        val lightAngle: Float = 120f,
        /** 0 to 4: steepness; 1 rises as far as the bevel is wide. */
        val depth: Float = 1f,
        /** 0.001 to 0.05 of the canvas: the bevel's width. */
        val size: Float = 0.01f,
        /** 0 to 1: blurs the surface over this fraction of [size], rounding the bevel. */
        val softness: Float = 0f,
        /** Straight ARGB; its alpha is the highlight's strength. */
        val highlight: Int = 0xBFFFFFFF.toInt(),
        val highlightBlend: BlendMode = BlendMode.SCREEN,
        /** Straight ARGB; its alpha is the shadow's strength. */
        val shadow: Int = 0xBF000000.toInt(),
        val shadowBlend: BlendMode = BlendMode.MULTIPLY,
    ) : Effect {
        init {
            require(lightAngle in 0f..360f) { "bevel light angle $lightAngle" }
            require(depth in 0f..4f) { "bevel depth $depth" }
            require(size in 0.001f..0.05f) { "bevel size $size" }
            require(softness in 0f..1f) { "bevel softness $softness" }
        }
    }

    /**
     * The icon's shadow on what lies below, away from the light at [angle], [distance]
     * off, blurred by [blur]. Knocked out under the icon itself.
     */
    data class DropShadow(
        /** 0 to 360 degrees: where the light comes from; the shadow falls the other way. */
        val angle: Float = 120f,
        /** 0 to [MAX_SHADOW_DISTANCE] of the canvas; schema 8 allowed 0.1. */
        val distance: Float = 0.01f,
        /** 0 to 0.1 of the canvas: Gaussian radius, sigma half of it. */
        val blur: Float = 0.01f,
        /** Straight ARGB. */
        val color: Int = 0xFF000000.toInt(),
    ) : Effect {
        init {
            require(angle in 0f..360f) { "shadow angle $angle" }
            require(distance in 0f..MAX_SHADOW_DISTANCE) { "shadow distance $distance" }
            require(blur in 0f..0.1f) { "shadow blur $blur" }
        }
    }

    /** A shadow inside the icon, along the edges facing the light. Same parameters as [DropShadow]. */
    data class InnerShadow(
        val angle: Float = 120f,
        val distance: Float = 0.01f,
        val blur: Float = 0.01f,
        val color: Int = 0xFF000000.toInt(),
    ) : Effect {
        init {
            require(angle in 0f..360f) { "shadow angle $angle" }
            require(distance in 0f..MAX_SHADOW_DISTANCE) { "shadow distance $distance" }
            require(blur in 0f..0.1f) { "shadow blur $blur" }
        }
    }

    /**
     * A glow around the icon: the mask blurred by [size], made more solid by [spread],
     * outside the icon only.
     */
    data class OuterGlow(
        /** 0.001 to 0.1 of the canvas: Gaussian radius, sigma half of it. */
        val size: Float = 0.02f,
        /** 0 to 1: the blur divided by `1 - 0.99 spread`, clamped; 0 is the plain blur, 1 solid out to where it fades. */
        val spread: Float = 0f,
        /** Straight ARGB. */
        val color: Int = 0xFFFFFFFF.toInt(),
    ) : Effect {
        init {
            require(size in 0.001f..0.1f) { "glow size $size" }
            require(spread in 0f..1f) { "glow spread $spread" }
        }
    }

    /** A glow inside the icon from its edges inward. Same parameters as [OuterGlow]. */
    data class InnerGlow(
        val size: Float = 0.02f,
        val spread: Float = 0f,
        val color: Int = 0xFFFFFFFF.toInt(),
    ) : Effect {
        init {
            require(size in 0.001f..0.1f) { "glow size $size" }
            require(spread in 0f..1f) { "glow spread $spread" }
        }
    }

    /**
     * A Gaussian blur of everything painted below it in the stack, schema 8: the icon tile
     * as drawn so far, softened, then mixed back in through [clip] (or its inverse) times
     * the layer's opacity. It blurs only this icon's own layers; a pack icon is a still
     * image and cannot see or blur the wallpaper behind it. The edges of the tile are
     * read as if they continued, so a full background does not fade at its rim. No blend
     * mode: it replaces what is below, as a mix. It adds nothing to the EFFECTS target.
     */
    data class Blur(
        /** 0.001 to 0.1 of the canvas: Gaussian radius, sigma half of it. */
        val radius: Float = 0.02f,
        /** Where the blur shows, as a paint layer's clip. */
        val clip: ClipTarget = ClipTarget.CANVAS,
        val clipInverse: Boolean = false,
    ) : Effect {
        init {
            require(radius in 0.001f..0.1f) { "blur radius $radius" }
        }
    }

    /**
     * Monochrome noise over the whole canvas: grey around mid-grey, so a contrast blend
     * such as overlay or soft light leaves the average alone. Each icon gets its own
     * patch from its one per-icon offset, [TextureOffset], so grain follows a texture
     * offset override.
     */
    data class Grain(
        /** 0 to 1: how far the grey swings from mid-grey. */
        val amount: Float = 0.5f,
        /** 1 to 4: a grain cell's side in pack pixels, 1 / OutputResolution.px of the canvas each. */
        val size: Float = 1f,
        val seed: Int = 0,
    ) : Effect {
        init {
            require(amount in 0f..1f) { "grain amount $amount" }
            require(size in 1f..4f) { "grain size $size" }
        }
    }
}

/**
 * What breaks a streak into brush marks: a texture, or noise when [texture] is null,
 * stretched [stretch] times along the streak and sampled in the streak's frame. Its
 * luminance t becomes `clamp((t - (1 - density)) * gain + 0.5)`, gain `1 / (1 - 0.95
 * contrast)`, and multiplies the smear.
 */
data class Breakup(
    /** A texture hash: a built-in brush, texture/BuiltInBrushes, or an imported texture. Null: fBm noise. */
    val texture: String? = null,
    /** 0.25 to 4: above 1 zooms in, as [Mapping.scale]. */
    val scale: Float = 1f,
    /** 1 to 32: stretch along the streak. */
    val stretch: Float = 8f,
    /** 0 to 1: how much of the breakup lets the streak through. */
    val density: Float = 0.5f,
    /** 0 to 1: from the source's own ramp to near-hard marks. */
    val contrast: Float = 0.5f,
    /** Noise seed, when [texture] is null. */
    val seed: Int = 0,
) {
    init {
        texture?.let { require(HASH.matches(it)) { "breakup texture hash $it" } }
        require(scale in 0.25f..4f) { "breakup scale $scale" }
        require(stretch in 1f..32f) { "breakup stretch $stretch" }
        require(density in 0f..1f) { "breakup density $density" }
        require(contrast in 0f..1f) { "breakup contrast $contrast" }
    }

    private companion object {
        val HASH = Regex("[0-9a-f]{64}")
    }
}
