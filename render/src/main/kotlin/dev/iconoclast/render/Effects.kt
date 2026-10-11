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

/** How a streak fades along its length, effect/Smear. */
enum class Decay {
    /** Weight 1 - t / length: a straight fade to nothing at the full length. */
    LINEAR,

    /** Weight exp(-3 t / length): 5% left at the full length, a long faint tail beyond. */
    EXPONENTIAL,
}

/** [Effect.Bevel]'s profile. */
enum class BevelStyle {
    /** Rises inside the icon from its edge over [Effect.Bevel.size]. */
    INNER,

    /** Rises across the edge, half outside and half inside, the icon standing out of the background. */
    EMBOSS,
}

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
        /** 0 to 0.1 of the canvas. */
        val distance: Float = 0.01f,
        /** 0 to 0.1 of the canvas: Gaussian radius, sigma half of it. */
        val blur: Float = 0.01f,
        /** Straight ARGB. */
        val color: Int = 0xFF000000.toInt(),
    ) : Effect {
        init {
            require(angle in 0f..360f) { "shadow angle $angle" }
            require(distance in 0f..0.1f) { "shadow distance $distance" }
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
            require(distance in 0f..0.1f) { "shadow distance $distance" }
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
     * Monochrome noise over the whole canvas: grey around mid-grey, so a contrast blend
     * such as overlay or soft light leaves the average alone. Each icon gets its own
     * patch, seeded by its drawable name.
     */
    data class Grain(
        /** 0 to 1: how far the grey swings from mid-grey. */
        val amount: Float = 0.5f,
        /** 1 to 4: a grain cell's side in 1/432 of the canvas, a pack pixel at 432 px. */
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
