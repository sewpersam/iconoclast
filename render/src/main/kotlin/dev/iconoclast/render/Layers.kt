package dev.iconoclast.render

/**
 * The layer stack, spec 2.3 "Layers": an ordered list painted bottom to top, step 4 of the
 * pipeline in spec 3.3. Data classes with checked ranges, like the rest of [Theme].
 */
sealed interface Layer {
    /** Stable for the layer's life: reordering, editing and saving keep it. */
    val id: String

    companion object {
        const val BACKGROUND_ID = "background"
        const val EFFECTS_ID = "effects"
        const val ICON_FILL_ID = "icon-fill"
        const val ICON_LINES_ID = "icon-lines"
    }
}

/**
 * The effects marker of the base stack `background -> effects -> icon`. Exactly one per
 * stack. It paints the background border, on top of the layers below, so the border
 * frames the background however many layers it has; and it is where a new [EffectLayer]
 * goes by default, [Theme.withEffect]. Each effect is its own [EffectLayer].
 */
data class EffectsLayer(override val id: String = Layer.EFFECTS_ID) : Layer

/**
 * A painted layer: [source] through the [clip] target (or its inverse), times the matte
 * from [matte] if any, times [opacity], composited with [blend].
 */
data class PaintLayer(
    override val id: String,
    val source: Paint,
    val clip: ClipTarget = ClipTarget.CANVAS,
    val clipInverse: Boolean = false,
    val blend: BlendMode = BlendMode.NORMAL,
    /** 0 to 1. */
    val opacity: Float = 1f,
    /** Placement of texture and noise sources; solid colours and gradients ignore it. */
    val mapping: Mapping = Mapping(),
    /** Turns the source's luminance into an alpha matte. Null: no matte. */
    val matte: Matte? = null,
) : Layer {
    init {
        require(id.isNotBlank()) { "blank layer id" }
        require(opacity in 0f..1f) { "opacity $opacity" }
    }
}

/**
 * What a layer is clipped to. The inverse of each is `1 - coverage`.
 * - [ICON]: the glyph, fill body union lines.
 * - [ICON_FILL], [ICON_LINES]: the fill stage's body and line masks alone.
 * - [BACKGROUND]: inside the background shape; its anti-aliased edge is applied once, by
 *   step 5, so here it is every pixel the shape touches. Empty when the background is off.
 * - [EFFECTS]: the effects' output so far: the union, `e + c - e c`, of the coverage
 *   times opacity of every enabled [EffectLayer] below this layer. Empty below the first.
 * - [CANVAS]: everything.
 */
enum class ClipTarget { CANVAS, BACKGROUND, ICON, ICON_FILL, ICON_LINES, EFFECTS }

/**
 * Spec 2.3's blend modes, in its order. Formulas are W3C Compositing and Blending Level 1;
 * see layer/BlendModes. The app maps them to the platform BlendMode names for the UI:
 * NORMAL is SRC_OVER and ADD is PLUS, the rest share their names.
 */
enum class BlendMode {
    NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT,
    SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY, ADD,
}

/**
 * How a texture covers the canvas. [FIT] shows all of it, [FILL] covers the canvas and
 * crops, [TILE] repeats it with its short side spanning the canvas. Both FIT and FILL
 * leave transparent whatever falls outside the texture.
 */
enum class MappingMode { FIT, FILL, TILE }

/**
 * Texture and noise placement. [scale] above 1 zooms in. [rotation] in degrees turns the
 * source about the canvas centre. Every icon gets its own patch of the source, picked by
 * a seed from its drawable name; [lockOffset] gives every icon the same, centred patch.
 */
data class Mapping(
    val mode: MappingMode = MappingMode.FILL,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val lockOffset: Boolean = false,
) {
    init {
        require(scale in 0.25f..4f) { "mapping scale $scale" }
        require(rotation in -180f..180f) { "mapping rotation $rotation" }
    }
}

enum class MatteMode { LEVELS, THRESHOLD }

/**
 * Mask adjustments: the source's luminance becomes an alpha matte. [LEVELS][MatteMode.LEVELS]
 * maps [black] to 0 and [white] to 1, linear between and clamped; [THRESHOLD][MatteMode.THRESHOLD]
 * gives 1 where the luminance reaches [threshold], else 0. Then [invert], then a Gaussian
 * [feather] whose radius is a fraction of the canvas.
 */
data class Matte(
    val mode: MatteMode = MatteMode.LEVELS,
    val black: Float = 0f,
    val white: Float = 1f,
    val threshold: Float = 0.5f,
    val invert: Boolean = false,
    val feather: Float = 0f,
) {
    init {
        require(black in 0f..1f && white in 0f..1f && black < white) { "levels $black..$white" }
        require(threshold in 0f..1f) { "threshold $threshold" }
        require(feather in 0f..0.05f) { "feather $feather" }
    }
}

/** Procedural noise variants, layer/Noise. */
enum class NoiseKind { FBM, RIDGED, GRAIN }
