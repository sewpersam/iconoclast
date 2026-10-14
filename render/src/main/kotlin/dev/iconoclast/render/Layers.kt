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
 * from [matte] if any, times [opacity], composited with [blend]. A layer that is not
 * [visible] paints nothing; an effect linked to it still reads its source.
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
    /** False: the layer paints nothing, whatever its [opacity]. The editor's eye. */
    val visible: Boolean = true,
    /** Schema 9: frost, grain, a light rim and a cut through to the wallpaper, over this layer's own area. */
    val finish: Finish = Finish(),
) : Layer {
    init {
        require(id.isNotBlank()) { "blank layer id" }
        require(opacity in 0f..1f) { "opacity $opacity" }
    }
}

/**
 * A fill's finish, schema 9, applied where the layer shows, its clip times its matte. The
 * layer's opacity is its see-through amount and stays apart. In paint order:
 * - [cut] 0 to 1 erases everything painted below, toward a transparent tile, so the
 *   wallpaper shows through the layer's shape; then the layer paints as usual;
 * - [frost] 0 to 0.1 of the canvas blurs what is there now, below and the layer itself,
 *   mixed in by the layer's area: frosted glass;
 * - [grain] 0 to 1 overlays film grain on the area, source-atop, so a cut stays clean;
 * - [rim] 0 to 1 screens a white light along the inside of the area's edge.
 * The editor's Glass starting point is a see-through fill with frost, grain and a rim.
 */
data class Finish(
    val frost: Float = 0f,
    val grain: Float = 0f,
    val rim: Float = 0f,
    val cut: Float = 0f,
) {
    init {
        require(frost in 0f..0.1f) { "frost $frost" }
        require(grain in 0f..1f) { "grain $grain" }
        require(rim in 0f..1f) { "rim $rim" }
        require(cut in 0f..1f) { "cut $cut" }
    }

    val isNone: Boolean get() = frost == 0f && grain == 0f && rim == 0f && cut == 0f
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
 * - [BELOW], schema 9: the layer directly below in the editor's list, wherever it is
 *   dragged: the icon row's ICON, a paint layer's own clip times its matte, an enabled
 *   effect's output coverage (a blur's clip), nothing for a switched-off effect or at the
 *   bottom. The effects marker is not in the list and is passed over.
 */
enum class ClipTarget { CANVAS, BACKGROUND, ICON, ICON_FILL, ICON_LINES, EFFECTS, BELOW }

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
 * source about the canvas centre. [crop] limits a texture to a rectangle of it.
 *
 * Per icon, schema 8: [randomShift] scales how far each icon's patch moves from the centre
 * by its per-icon offset, 0 every icon the same centred patch, 1 anywhere (schema 7's
 * unlocked offset); [randomRotation] turns each icon by up to that many degrees either way;
 * [randomScale] zooms each icon by up to 2^randomScale either way. Each icon's draws come
 * from its seed name and [seed]: seed 0 draws the shift from the icon's one per-icon
 * offset, as every schema 7 theme did; Shuffle picks another [seed], a new arrangement.
 */
data class Mapping(
    val mode: MappingMode = MappingMode.FILL,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    /** 0 to 1: how far each icon's patch moves. Schema 7's lockOffset true is 0, false is 1. */
    val randomShift: Float = 1f,
    /** 0 to 180 degrees: each icon turned by up to this much, either way. */
    val randomRotation: Float = 0f,
    /** 0 to 1: each icon zoomed by up to 2^this, in or out. */
    val randomScale: Float = 0f,
    /** Picks the arrangement. 0 is the icon's own per-icon offset. */
    val seed: Int = 0,
    /** Textures only. Null: the whole texture. */
    val crop: Crop? = null,
) {
    init {
        require(scale in 0.25f..4f) { "mapping scale $scale" }
        require(rotation in -180f..180f) { "mapping rotation $rotation" }
        require(randomShift in 0f..1f) { "mapping random shift $randomShift" }
        require(randomRotation in 0f..180f) { "mapping random rotation $randomRotation" }
        require(randomScale in 0f..1f) { "mapping random scale $randomScale" }
    }
}

/**
 * The part of a texture a layer shows, fractions of its width and height, y down. The
 * renderer snaps the edges to whole texels. At least [MIN] on each side.
 */
data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) { "crop $left,$top,$right,$bottom" }
        require(right - left >= MIN && bottom - top >= MIN) { "crop too small: $left,$top,$right,$bottom" }
    }

    companion object {
        const val MIN = 0.02f
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

/** Schema 8's procedural noise variants, now [Pattern.legacy]; grain effects use GRAIN's field. */
enum class NoiseKind { FBM, RIDGED, GRAIN }
