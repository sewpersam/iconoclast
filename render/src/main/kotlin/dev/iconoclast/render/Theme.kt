package dev.iconoclast.render

/**
 * The theme parameters the renderer reads, spec section 2.3. Ranges are checked on
 * construction. Data classes, so they serve directly as mask cache keys.
 *
 * [layers] is the stack, painted bottom to top: [PaintLayer]s, [EffectLayer]s, and exactly
 * one [EffectsLayer] marker. Layer ids are unique. The default is the base stack
 * `background -> effects -> icon`.
 */
data class Theme(
    val shape: ShapeParams = ShapeParams(),
    val fill: FillParams = FillParams(),
    val background: BackgroundParams = BackgroundParams(),
    val layers: List<Layer> = baseLayers(),
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
    /** Size of the 48-unit icon box as a fraction of the adaptive-icon safe zone. */
    val iconScale: Float = 0.8f,
    /** Offset as a fraction of the canvas. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val dots: DotMode = DotMode.SCALE_WITH_STROKE,
) {
    init {
        require(strokeWeight in 0.25f..4f) { "strokeWeight $strokeWeight" }
        require(miterLimit in 1f..10f) { "miterLimit $miterLimit" }
        require(iconScale in 0.5f..1f) { "iconScale $iconScale" }
        require(offsetX in -0.1f..0.1f && offsetY in -0.1f..0.1f) { "offset $offsetX,$offsetY" }
    }
}

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
     * A texture from the [TextureStore][dev.iconoclast.render.texture.TextureStore] by the
     * lowercase hex SHA-256 of its canonical PNG. With [stops], the texture's luminance is
     * gradient-mapped through them and its alpha kept; null keeps its own colours.
     */
    data class Texture(val sha256: String, val stops: List<ColorStop>? = null) : Paint {
        init {
            require(HASH.matches(sha256)) { "texture hash $sha256" }
            stops?.let(::requireStops)
        }

        private companion object {
            val HASH = Regex("[0-9a-f]{64}")
        }
    }

    /**
     * Procedural noise, 0 to 1, gradient-mapped through [stops]. [octaves] applies to fBm
     * and ridged; [seed] picks the field, and the mapping's per-icon offset picks the patch.
     */
    data class Noise(
        val kind: NoiseKind = NoiseKind.FBM,
        val octaves: Int = 4,
        val seed: Int = 0,
        val stops: List<ColorStop> = listOf(ColorStop(0f, 0xFF000000.toInt()), ColorStop(1f, 0xFFFFFFFF.toInt())),
    ) : Paint {
        init {
            require(octaves in 1..8) { "octaves $octaves" }
            requireStops(stops)
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
