package dev.icondesigner.render

/**
 * The theme parameters the renderer reads, spec section 2.3. Ranges are checked on
 * construction. Data classes, so they serve directly as mask cache keys.
 */
data class Theme(
    val shape: ShapeParams = ShapeParams(),
    val fill: FillParams = FillParams(),
    val background: BackgroundParams = BackgroundParams(),
    val glyph: GlyphPaint = GlyphPaint(),
)

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

/** Paint coordinates are fractions of the canvas, 0 to 1, y down. */
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

data class BackgroundParams(
    /** Off means a transparent background and no shape clip. */
    val enabled: Boolean = true,
    val shape: BackgroundShape = BackgroundShape.Squircle(),
    /** Side of the shape as a fraction of the canvas. */
    val size: Float = 1f,
    val paint: Paint = Paint.Solid(0xFF202124.toInt()),
    val border: Border? = null,
) {
    init { require(size in 0.7f..1f) { "background size $size" } }
}

/**
 * How the glyph masks are painted. This is the M2 stand-in for the base of the layer
 * stack: [body] paints the fill mask, [line] the strokes. Straight ARGB.
 */
data class GlyphPaint(
    val line: Int = 0xFFFFFFFF.toInt(),
    val body: Int = 0xFFFFFFFF.toInt(),
)
