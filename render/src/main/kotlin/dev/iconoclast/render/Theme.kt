package dev.iconoclast.render

/**
 * The theme parameters the renderer reads, spec section 2.3. Ranges are checked on
 * construction. Data classes, so they serve directly as mask cache keys.
 *
 * [layers] is the stack, painted bottom to top: [PaintLayer]s, [EffectLayer]s, and exactly
 * one [EffectsLayer] marker. Layer ids are unique. The default is the base stack
 * `background -> effects -> icon`.
 *
 * [textureOffset] pins the per-icon offset. Only an effective per-icon theme sets it, from
 * a texture offset override in the stored document; a saved theme never carries it, and
 * null derives the offset from the slot's seed name, [TextureOffset.of].
 */
data class Theme(
    val shape: ShapeParams = ShapeParams(),
    val fill: FillParams = FillParams(),
    val background: BackgroundParams = BackgroundParams(),
    val layers: List<Layer> = baseLayers(),
    val textureOffset: TextureOffset? = null,
) {
    init {
        require(layers.count { it is EffectsLayer } == 1) { "the stack needs exactly one effects layer" }
        require(layers.map { it.id }.toSet().size == layers.size) { "duplicate layer ids" }
    }

    /**
     * [layer] at the default insertion point: just above the [EffectsLayer] marker and
     * the effects already stacked directly on it, so effects added one after another
     * keep their order. Its id must be new.
     */
    fun withEffect(layer: EffectLayer): Theme {
        var at = layers.indexOfFirst { it is EffectsLayer } + 1
        while (at < layers.size && layers[at] is EffectLayer) at++
        return copy(layers = layers.subList(0, at) + layer + layers.subList(at, layers.size))
    }

    /**
     * Every texture the stack uses, by hash, built-in brushes included: what must be in the
     * store before it renders. Paint layers' textures, which covers every layer a streak
     * takes its paint from, then effect textures, a streak's breakup. Effects switched off
     * count, so a theme keeps them across export and import. A theme export carries all of
     * them except the built-in brushes, which every install has.
     */
    fun textureHashes(): Set<String> = layers.mapNotNullTo(LinkedHashSet()) {
        when (it) {
            is PaintLayer -> (it.source as? Paint.Texture)?.sha256
            is EffectLayer -> (it.effect as? Effect.Streaks)?.breakup?.texture
            is EffectsLayer -> null
        }
    }

    companion object {
        /**
         * The base stack, spec 2.3: the background paint clipped to the background, the
         * effects slot, then the glyph as two layers, the fill body in [body] and the lines
         * in [line] on top. Straight ARGB colours. Schema 1 themes migrate to exactly this.
         */
        fun baseLayers(
            background: Paint = Paint.Solid(0xFF202124.toInt()),
            body: Int = 0xFFFFFFFF.toInt(),
            line: Int = 0xFFFFFFFF.toInt(),
        ): List<Layer> = listOf(
            PaintLayer(Layer.BACKGROUND_ID, background, clip = ClipTarget.BACKGROUND),
            EffectsLayer(),
            PaintLayer(Layer.ICON_FILL_ID, Paint.Solid(body), clip = ClipTarget.ICON_FILL),
            PaintLayer(Layer.ICON_LINES_ID, Paint.Solid(line), clip = ClipTarget.ICON_LINES),
        )
    }
}

enum class LineCap { BUTT, ROUND, SQUARE }

enum class LineJoin { MITER, ROUND, BEVEL }

enum class DotMode { SCALE_WITH_STROKE, KEEP_ORIGINAL }

/** Shape, applied to the line art before anything else. */
data class ShapeParams(
    /** Stroke width in 48-grid units. Every Arcticons stroke at the pin is 1 unit wide. */
    val strokeWeight: Float = 1f,
    val cap: LineCap = LineCap.ROUND,
    val join: LineJoin = LineJoin.ROUND,
    val miterLimit: Float = 4f,
    /**
     * Size of the 48-unit icon box as a fraction of the adaptive-icon safe zone, the unit
     * every saved theme uses. [MAX_ICON_SCALE] makes the box the whole canvas; the editor
     * shows the size as that fraction of the canvas, so 100% touches the edges.
     */
    val iconScale: Float = 0.8f,
    /** Offset as a fraction of the canvas. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val dots: DotMode = DotMode.SCALE_WITH_STROKE,
) {
    init {
        require(strokeWeight in 0.25f..4f) { "strokeWeight $strokeWeight" }
        require(miterLimit in 1f..10f) { "miterLimit $miterLimit" }
        require(iconScale in 0.5f..MAX_ICON_SCALE) { "iconScale $iconScale" }
        require(offsetX in -0.1f..0.1f && offsetY in -0.1f..0.1f) { "offset $offsetX,$offsetY" }
    }
}

/** The icon scale whose 48-unit box spans the whole canvas: the safe zone's inverse, 108 / 66. */
const val MAX_ICON_SCALE = 108f / 66f

enum class FillMode { OUTLINE, INVERT, SILHOUETTE, OUTLINE_SILHOUETTE }

data class FillParams(
    val mode: FillMode = FillMode.OUTLINE,
    /** Invert fill only. Whitespace between strokes and fill, in 48-grid units. */
    val gapWidth: Float = 1f,
    /** Seals near-closed shapes before the flood fill, in 48-grid units. */
    val gapClosing: Float = 0.5f,
    /** Invert fill falls back to Outline when its area is below this fraction of the outline's area. */
    val fallbackThreshold: Float = 0.1f,
    /** Invert fill draws unenclosed strokes on top, so antennas and dots survive. */
    val keepLoneLines: Boolean = true,
) {
    init {
        require(gapWidth in 0f..4f) { "gapWidth $gapWidth" }
        require(gapClosing in 0f..2f) { "gapClosing $gapClosing" }
        require(fallbackThreshold in 0f..0.3f) { "fallbackThreshold $fallbackThreshold" }
    }
}

/** A colour stop. [argb] is straight, non-premultiplied 0xAARRGGBB. */
data class ColorStop(val offset: Float, val argb: Int)

/**
 * A layer's source. Gradient coordinates are fractions of the canvas, 0 to 1, y down;
 * textures and noise are placed by the layer's [Mapping].
 */
sealed interface Paint {
    data class Solid(val argb: Int) : Paint

    data class LinearGradient(
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val stops: List<ColorStop>,
    ) : Paint {
        init { requireStops(stops) }
    }

    data class RadialGradient(
        val cx: Float, val cy: Float, val radius: Float,
        val stops: List<ColorStop>,
    ) : Paint {
        init {
            require(radius > 0f) { "radius $radius" }
            requireStops(stops)
        }
    }

    /**
     * An angular (conic) gradient, schema 8: the stops run once round the centre ([cx],
     * [cy]), clockwise on screen from [angle] degrees, where 0 points right and 90 down,
     * as a linear gradient's direction does.
     */
    data class AngularGradient(
        val cx: Float, val cy: Float, val angle: Float,
        val stops: List<ColorStop>,
    ) : Paint {
        init {
            require(angle in 0f..360f) { "angle $angle" }
            requireStops(stops)
        }
    }

    /**
     * A texture from the [TextureStore][dev.iconoclast.render.texture.TextureStore] by the
     * lowercase hex SHA-256 of its canonical PNG. With [stops], the texture's luminance is
     * gradient-mapped through them and its alpha kept; null keeps its own colours.
     */
    data class Texture(val sha256: String, val stops: List<ColorStop>? = null, val adjust: Adjust? = null) : Paint {
        init {
            require(HASH.matches(sha256)) { "texture hash $sha256" }
            stops?.let(::requireStops)
        }

        private companion object {
            val HASH = Regex("[0-9a-f]{64}")
        }
    }

    /**
     * A procedural pattern, schema 9: one of three generators after Blender's Noise, Voronoi
     * and Wave textures, its value 0 to 1 gradient-mapped through [stops]. [seed] picks the
     * field, and the mapping's per-icon offset picks the patch. Code: layer/PatternField.
     */
    data class Noise(
        val pattern: Pattern = Pattern.Noise(),
        val seed: Int = 0,
        val stops: List<ColorStop> = DEFAULT_STOPS,
    ) : Paint {
        init { requireStops(stops) }

        /**
         * A schema 8 noise paint as its schema 9 pattern, drawing the same field to the bit:
         * fBm and ridged are Noise at size 1/8 with [octaves] - 1 detail and roughness 0.5;
         * grain is pixel-size Cells.
         */
        constructor(kind: NoiseKind, octaves: Int = 4, seed: Int = 0, stops: List<ColorStop> = DEFAULT_STOPS) :
            this(Pattern.legacy(kind, octaves), seed, stops)

        companion object {
            val DEFAULT_STOPS = listOf(ColorStop(0f, 0xFF000000.toInt()), ColorStop(1f, 0xFFFFFFFF.toInt()))
        }
    }
}

/**
 * Non-destructive colour adjustments of an image fill, schema 9, each -1 to 1 with 0 no
 * change. Applied to the image's own colours before any recolouring. Code: layer/Adjustments.
 */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    init {
        require(brightness in -1f..1f) { "brightness $brightness" }
        require(contrast in -1f..1f) { "contrast $contrast" }
        require(saturation in -1f..1f) { "saturation $saturation" }
        require(warmth in -1f..1f) { "warmth $warmth" }
    }

    val isIdentity: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

/** What a Cells pattern shows, after Blender's Voronoi outputs. */
enum class CellFeature {
    /** One flat random value per cell: Voronoi's Color output. */
    CELLS,
    /** Lines along the borders between cells, [Pattern.Cells.edgeWidth] wide. */
    EDGES,
    /** Distance to the second-nearest cell minus the nearest, F2 - F1: cracked mud. */
    CRACKLE,
}

/** A Waves pattern's shape, Blender's Wave Type. */
enum class WaveKind { BANDS, RINGS }

/**
 * The three pattern generators, schema 9. Sizes are a cell's side, or a wave's period, as
 * a fraction of the canvas before the layer's mapping scale.
 */
sealed interface Pattern {

    /**
     * Blender's Noise texture: value noise summed over octaves. [detail] 0 to 8 is the
     * octaves above the first, fractional parts blended in; [roughness] the gain per
     * octave; [distortion] 0 to 2 warps the field by itself; [stretch] 1 to 16 pulls it
     * along [stretchAngle] degrees; [ridges] folds each octave about its middle into veins.
     */
    data class Noise(
        val size: Float = 0.125f,
        val detail: Float = 3f,
        val roughness: Float = 0.5f,
        val distortion: Float = 0f,
        val stretch: Float = 1f,
        val stretchAngle: Float = 0f,
        val ridges: Boolean = false,
    ) : Pattern {
        init {
            require(size in 0.01f..1f) { "noise size $size" }
            require(detail in 0f..8f) { "noise detail $detail" }
            require(roughness in 0f..1f) { "noise roughness $roughness" }
            require(distortion in 0f..2f) { "noise distortion $distortion" }
            require(stretch in 1f..16f) { "noise stretch $stretch" }
            require(stretchAngle in 0f..180f) { "noise stretch angle $stretchAngle" }
        }
    }

    /**
     * Blender's Voronoi texture, F1 and F2 in two dimensions. [size] 0 is one pack pixel
     * per cell on an even grid, film-grain fine, box-filtered; above 0 the cells' points
     * move off the grid by [randomness]. [edgeWidth] 0.01 to 0.5 of a cell, for EDGES.
     */
    data class Cells(
        val size: Float = 0.125f,
        val randomness: Float = 1f,
        val edgeWidth: Float = 0.1f,
        val feature: CellFeature = CellFeature.CELLS,
    ) : Pattern {
        init {
            require(size == 0f || size in 0.01f..1f) { "cells size $size" }
            require(randomness in 0f..1f) { "cells randomness $randomness" }
            require(edgeWidth in 0.01f..0.5f) { "cells edge width $edgeWidth" }
        }
    }

    /**
     * Blender's Wave texture with a sine profile: straight [WaveKind.BANDS] or [WaveKind.RINGS]
     * about the centre, [size] the period, bent by [distortion] 0 to 20 of noise with
     * [detail] 0 to 8 octaves above the first. The mapping's rotation turns the bands.
     */
    data class Waves(
        val kind: WaveKind = WaveKind.BANDS,
        val size: Float = 0.125f,
        val distortion: Float = 0f,
        val detail: Float = 2f,
    ) : Pattern {
        init {
            require(size in 0.01f..1f) { "waves size $size" }
            require(distortion in 0f..20f) { "waves distortion $distortion" }
            require(detail in 0f..8f) { "waves detail $detail" }
        }
    }

    companion object {
        /** Schema 8's noise kinds, drawn the same by these patterns. */
        fun legacy(kind: NoiseKind, octaves: Int): Pattern {
            require(octaves in 1..8) { "octaves $octaves" }
            return when (kind) {
                NoiseKind.FBM -> Noise(detail = (octaves - 1).toFloat())
                NoiseKind.RIDGED -> Noise(detail = (octaves - 1).toFloat(), ridges = true)
                NoiseKind.GRAIN -> Cells(size = 0f, randomness = 0f)
            }
        }
    }
}

private fun requireStops(stops: List<ColorStop>) {
    require(stops.isNotEmpty()) { "no stops" }
    require(stops.zipWithNext().all { (a, b) -> a.offset <= b.offset }) { "stops out of order" }
}

sealed interface BackgroundShape {
    data object Circle : BackgroundShape
    data object Square : BackgroundShape
    /** AOSP's teardrop: three corners at half the size, the bottom right at 15%. */
    data object Teardrop : BackgroundShape

    /** |x|^n + |y|^n = 1. */
    data class Squircle(val exponent: Float = 4f) : BackgroundShape {
        init { require(exponent in 2f..12f) { "exponent $exponent" } }
    }

    /** Corner radius as a fraction of the shape's side, up to 0.5, which is a circle. */
    data class RoundedSquare(val cornerRadius: Float = 0.2f) : BackgroundShape {
        init { require(cornerRadius in 0f..0.5f) { "cornerRadius $cornerRadius" } }
    }
}

/** [width] is a fraction of the canvas. The border sits inside the shape's edge. */
data class Border(val width: Float, val argb: Int, val opacity: Float = 1f) {
    init {
        require(width in 0f..0.1f) { "border width $width" }
        require(opacity in 0f..1f) { "border opacity $opacity" }
    }
}

/** The background shape. Its paint is the stack's background layer. */
data class BackgroundParams(
    /** Off means no background shape: no clip, and an empty background clip target. */
    val enabled: Boolean = true,
    val shape: BackgroundShape = BackgroundShape.Squircle(),
    /** Side of the shape as a fraction of the canvas. */
    val size: Float = 1f,
    val border: Border? = null,
) {
    init { require(size in 0.7f..1f) { "background size $size" } }
}
