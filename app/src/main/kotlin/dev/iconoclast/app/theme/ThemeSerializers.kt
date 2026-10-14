package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Border
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Crop
import dev.iconoclast.render.DotMode
import dev.iconoclast.render.BevelStyle
import dev.iconoclast.render.Breakup
import dev.iconoclast.render.Decay
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.Layer
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * JSON serializers for the :render theme classes, which stay free of serialization.
 * Each public serializer maps through a private surrogate whose field names are the
 * file format, docs/theme-format.md. Every field is written, defaults included, so a
 * later change to a default never changes how a saved theme looks.
 */

/** Writes and reads -0f as 0f, so a parsed theme equals one built with 0f. Rejects NaN and infinities. */
object CanonicalFloatSerializer : KSerializer<Float> {
    override val descriptor = PrimitiveSerialDescriptor("dev.iconoclast.Float", PrimitiveKind.FLOAT)
    override fun serialize(encoder: Encoder, value: Float) = encoder.encodeFloat(canonical(value))
    override fun deserialize(decoder: Decoder): Float = canonical(decoder.decodeFloat())

    private fun canonical(v: Float): Float {
        if (!v.isFinite()) throw SerializationException("not a finite number: $v")
        return if (v == 0f) 0f else v
    }
}

/** Straight ARGB as "#AARRGGBB". */
object ArgbSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("dev.iconoclast.Argb", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString("#%08X".format(value))
    override fun deserialize(decoder: Decoder): Int {
        val s = decoder.decodeString()
        if (!COLOR.matches(s)) throw SerializationException("colour must be #AARRGGBB: $s")
        return s.substring(1).toLong(16).toInt()
    }

    private val COLOR = Regex("#[0-9A-Fa-f]{8}")
}

private typealias JFloat = @Serializable(CanonicalFloatSerializer::class) Float
private typealias Argb = @Serializable(ArgbSerializer::class) Int

@Serializable
private sealed interface PaintJson {
    @Serializable @SerialName("solid")
    data class Solid(val color: Argb) : PaintJson

    @Serializable @SerialName("linear")
    data class Linear(val x0: JFloat, val y0: JFloat, val x1: JFloat, val y1: JFloat, val stops: List<StopJson>) : PaintJson

    @Serializable @SerialName("radial")
    data class Radial(val cx: JFloat, val cy: JFloat, val radius: JFloat, val stops: List<StopJson>) : PaintJson

    /** Schema 8. */
    @Serializable @SerialName("angular")
    data class Angular(val cx: JFloat, val cy: JFloat, val angle: JFloat, val stops: List<StopJson>) : PaintJson

    @Serializable @SerialName("texture")
    data class Texture(val sha256: String, val stops: List<StopJson>?) : PaintJson

    @Serializable @SerialName("noise")
    data class Noise(val kind: NoiseKind, val octaves: Int, val seed: Int, val stops: List<StopJson>) : PaintJson
}

@Serializable
private data class StopJson(val offset: JFloat, val color: Argb)

@Serializable
private sealed interface ShapeJson {
    @Serializable @SerialName("circle") data object Circle : ShapeJson
    @Serializable @SerialName("square") data object Square : ShapeJson
    @Serializable @SerialName("teardrop") data object Teardrop : ShapeJson
    @Serializable @SerialName("squircle") data class Squircle(val exponent: JFloat) : ShapeJson
    @Serializable @SerialName("roundedSquare") data class RoundedSquare(val cornerRadius: JFloat) : ShapeJson
}

/** The sealed [Paint], tagged by "type": solid, linear, radial, texture or noise. */
object PaintSerializer : KSerializer<Paint> {
    private val surrogate = PaintJson.serializer()
    override val descriptor = surrogate.descriptor
    override fun serialize(encoder: Encoder, value: Paint) = encoder.encodeSerializableValue(surrogate, value.toJson())
    override fun deserialize(decoder: Decoder): Paint = decoder.decodeSerializableValue(surrogate).toPaint()
}

/** The sealed [BackgroundShape], tagged by "type": circle, square, teardrop, squircle or roundedSquare. */
object BackgroundShapeSerializer : KSerializer<BackgroundShape> {
    private val surrogate = ShapeJson.serializer()
    override val descriptor = surrogate.descriptor
    override fun serialize(encoder: Encoder, value: BackgroundShape) = encoder.encodeSerializableValue(surrogate, value.toJson())
    override fun deserialize(decoder: Decoder): BackgroundShape = decoder.decodeSerializableValue(surrogate).toShape()
}

@Serializable
private data class ShapeParamsJson(
    val strokeWeight: JFloat, val cap: LineCap, val join: LineJoin, val miterLimit: JFloat,
    val iconScale: JFloat, val offsetX: JFloat, val offsetY: JFloat, val dots: DotMode,
)

@Serializable
private data class FillParamsJson(
    val mode: FillMode, val gapWidth: JFloat, val gapClosing: JFloat, val fallbackThreshold: JFloat, val keepLoneLines: Boolean,
)

@Serializable
private data class BorderJson(val width: JFloat, val color: Argb, val opacity: JFloat)

@Serializable
private data class BackgroundJson(
    val enabled: Boolean,
    @Serializable(BackgroundShapeSerializer::class) val shape: BackgroundShape,
    val size: JFloat,
    val border: BorderJson?,
)

@Serializable
private data class CropJson(val left: JFloat, val top: JFloat, val right: JFloat, val bottom: JFloat)

/** Schema 8: lockOffset became randomShift; the per-icon turn, zoom, seed and crop are new. */
@Serializable
private data class MappingJson(
    val mode: MappingMode, val scale: JFloat, val rotation: JFloat,
    val randomShift: JFloat, val randomRotation: JFloat, val randomScale: JFloat, val seed: Int, val crop: CropJson?,
)

@Serializable
private data class MatteJson(
    val mode: MatteMode, val black: JFloat, val white: JFloat, val threshold: JFloat, val invert: Boolean, val feather: JFloat,
)

@Serializable
private sealed interface LayerJson {
    @Serializable @SerialName("paint")
    data class Paint(
        val id: String,
        @Serializable(PaintSerializer::class) val source: dev.iconoclast.render.Paint,
        val clip: ClipTarget,
        val clipInverse: Boolean,
        val blend: BlendMode,
        val opacity: JFloat,
        val mapping: MappingJson,
        val matte: MatteJson?,
        /** Schema 6. */
        val visible: Boolean,
    ) : LayerJson

    @Serializable @SerialName("effects")
    data class Effects(val id: String) : LayerJson

    @Serializable @SerialName("effect")
    data class Effect(
        val id: String,
        val enabled: Boolean,
        val blend: BlendMode,
        val opacity: JFloat,
        val effect: EffectJson,
    ) : LayerJson
}

@Serializable
private data class BreakupJson(
    val texture: String?, val scale: JFloat, val stretch: JFloat, val density: JFloat, val contrast: JFloat, val seed: Int,
)

/** Schema 3, brushStrokes schema 5. Tagged by "type"; field names are the :render parameter names. */
@Serializable
private sealed interface EffectJson {
    @Serializable @SerialName("streaks")
    data class Streaks(
        val angle: JFloat, val length: JFloat, val decay: Decay, val breakup: BreakupJson?, val paintLayer: String?, val color: Argb,
    ) : EffectJson

    /** Schema 5. */
    @Serializable @SerialName("brushStrokes")
    data class BrushStrokes(
        val count: Int, val length: JFloat, val width: JFloat, val softness: JFloat, val blur: JFloat, val fade: JFloat,
        val randomness: JFloat, val bend: JFloat, val bristles: JFloat, val drift: JFloat, val angle: JFloat,
        val paintLayer: String?, val color: Argb, val seed: Int,
    ) : EffectJson

    @Serializable @SerialName("bevel")
    data class Bevel(
        val style: BevelStyle, val lightAngle: JFloat, val depth: JFloat, val size: JFloat, val softness: JFloat,
        val highlight: Argb, val highlightBlend: BlendMode, val shadow: Argb, val shadowBlend: BlendMode,
    ) : EffectJson

    @Serializable @SerialName("dropShadow")
    data class DropShadow(val angle: JFloat, val distance: JFloat, val blur: JFloat, val color: Argb) : EffectJson

    @Serializable @SerialName("innerShadow")
    data class InnerShadow(val angle: JFloat, val distance: JFloat, val blur: JFloat, val color: Argb) : EffectJson

    @Serializable @SerialName("outerGlow")
    data class OuterGlow(val size: JFloat, val spread: JFloat, val color: Argb) : EffectJson

    @Serializable @SerialName("innerGlow")
    data class InnerGlow(val size: JFloat, val spread: JFloat, val color: Argb) : EffectJson

    @Serializable @SerialName("grain")
    data class Grain(val amount: JFloat, val size: JFloat, val seed: Int) : EffectJson

    /** Schema 8. */
    @Serializable @SerialName("blur")
    data class Blur(val radius: JFloat, val clip: ClipTarget, val clipInverse: Boolean) : EffectJson
}

private fun Effect.toJson(): EffectJson = when (this) {
    is Effect.Streaks -> EffectJson.Streaks(
        angle, length, decay,
        breakup?.run { BreakupJson(texture, scale, stretch, density, contrast, seed) },
        paintLayer, color,
    )
    is Effect.BrushStrokes -> EffectJson.BrushStrokes(
        count, length, width, softness, blur, fade, randomness, bend, bristles, drift, angle, paintLayer, color, seed,
    )
    is Effect.Bevel -> EffectJson.Bevel(style, lightAngle, depth, size, softness, highlight, highlightBlend, shadow, shadowBlend)
    is Effect.DropShadow -> EffectJson.DropShadow(angle, distance, blur, color)
    is Effect.InnerShadow -> EffectJson.InnerShadow(angle, distance, blur, color)
    is Effect.OuterGlow -> EffectJson.OuterGlow(size, spread, color)
    is Effect.InnerGlow -> EffectJson.InnerGlow(size, spread, color)
    is Effect.Grain -> EffectJson.Grain(amount, size, seed)
    is Effect.Blur -> EffectJson.Blur(radius, clip, clipInverse)
}

private fun EffectJson.toEffect(): Effect = when (this) {
    is EffectJson.Streaks -> Effect.Streaks(
        angle, length, decay,
        breakup?.run { Breakup(texture, scale, stretch, density, contrast, seed) },
        paintLayer, color,
    )
    is EffectJson.BrushStrokes -> Effect.BrushStrokes(
        count, length, width, softness, blur, fade, randomness, bend, bristles, drift, angle, paintLayer, color, seed,
    )
    is EffectJson.Bevel -> Effect.Bevel(style, lightAngle, depth, size, softness, highlight, highlightBlend, shadow, shadowBlend)
    is EffectJson.DropShadow -> Effect.DropShadow(angle, distance, blur, color)
    is EffectJson.InnerShadow -> Effect.InnerShadow(angle, distance, blur, color)
    is EffectJson.OuterGlow -> Effect.OuterGlow(size, spread, color)
    is EffectJson.InnerGlow -> Effect.InnerGlow(size, spread, color)
    is EffectJson.Grain -> Effect.Grain(amount, size, seed)
    is EffectJson.Blur -> Effect.Blur(radius, clip, clipInverse)
}

@Serializable
private data class ThemeJson(val shape: ShapeParamsJson, val fill: FillParamsJson, val background: BackgroundJson, val layers: List<LayerJson>)

/**
 * The whole [Theme]. Out-of-range values fail in the :render constructors with
 * IllegalArgumentException, which the parser reports as a [SerializationException].
 */
object ThemeSerializer : KSerializer<Theme> {
    private val surrogate = ThemeJson.serializer()
    override val descriptor = surrogate.descriptor

    /** A pinned texture offset belongs to an effective per-icon theme, never to a saved one. */
    override fun serialize(encoder: Encoder, value: Theme) {
        require(value.textureOffset == null) { "an effective per-icon theme cannot be saved" }
        encoder.encodeSerializableValue(surrogate, value.toJson())
    }

    override fun deserialize(decoder: Decoder): Theme {
        val json = decoder.decodeSerializableValue(surrogate)
        return try {
            json.toTheme()
        } catch (e: IllegalArgumentException) {
            throw SerializationException("theme value out of range: ${e.message}", e)
        }
    }
}

private fun Paint.toJson(): PaintJson = when (this) {
    is Paint.Solid -> PaintJson.Solid(argb)
    is Paint.LinearGradient -> PaintJson.Linear(x0, y0, x1, y1, stops.map { StopJson(it.offset, it.argb) })
    is Paint.RadialGradient -> PaintJson.Radial(cx, cy, radius, stops.map { StopJson(it.offset, it.argb) })
    is Paint.AngularGradient -> PaintJson.Angular(cx, cy, angle, stops.map { StopJson(it.offset, it.argb) })
    is Paint.Texture -> PaintJson.Texture(sha256, stops?.map { StopJson(it.offset, it.argb) })
    is Paint.Noise -> PaintJson.Noise(kind, octaves, seed, stops.map { StopJson(it.offset, it.argb) })
}

private fun PaintJson.toPaint(): Paint = when (this) {
    is PaintJson.Solid -> Paint.Solid(color)
    is PaintJson.Linear -> Paint.LinearGradient(x0, y0, x1, y1, stops.map { ColorStop(it.offset, it.color) })
    is PaintJson.Radial -> Paint.RadialGradient(cx, cy, radius, stops.map { ColorStop(it.offset, it.color) })
    is PaintJson.Angular -> Paint.AngularGradient(cx, cy, angle, stops.map { ColorStop(it.offset, it.color) })
    is PaintJson.Texture -> Paint.Texture(sha256, stops?.map { ColorStop(it.offset, it.color) })
    is PaintJson.Noise -> Paint.Noise(kind, octaves, seed, stops.map { ColorStop(it.offset, it.color) })
}

private fun Layer.toJson(): LayerJson = when (this) {
    is EffectsLayer -> LayerJson.Effects(id)
    is EffectLayer -> LayerJson.Effect(id, enabled, blend, opacity, effect.toJson())
    is PaintLayer -> LayerJson.Paint(
        id, source, clip, clipInverse, blend, opacity,
        mapping.run { MappingJson(mode, scale, rotation, randomShift, randomRotation, randomScale, seed, crop?.run { CropJson(left, top, right, bottom) }) },
        matte?.run { MatteJson(mode, black, white, threshold, invert, feather) },
        visible,
    )
}

private fun LayerJson.toLayer(): Layer = when (this) {
    is LayerJson.Effects -> EffectsLayer(id)
    is LayerJson.Effect -> EffectLayer(id, effect.toEffect(), enabled, blend, opacity)
    is LayerJson.Paint -> PaintLayer(
        id, source, clip, clipInverse, blend, opacity,
        mapping.run { Mapping(mode, scale, rotation, randomShift, randomRotation, randomScale, seed, crop?.run { Crop(left, top, right, bottom) }) },
        matte?.run { Matte(mode, black, white, threshold, invert, feather) },
        visible,
    )
}

private fun BackgroundShape.toJson(): ShapeJson = when (this) {
    BackgroundShape.Circle -> ShapeJson.Circle
    BackgroundShape.Square -> ShapeJson.Square
    BackgroundShape.Teardrop -> ShapeJson.Teardrop
    is BackgroundShape.Squircle -> ShapeJson.Squircle(exponent)
    is BackgroundShape.RoundedSquare -> ShapeJson.RoundedSquare(cornerRadius)
}

private fun ShapeJson.toShape(): BackgroundShape = when (this) {
    ShapeJson.Circle -> BackgroundShape.Circle
    ShapeJson.Square -> BackgroundShape.Square
    ShapeJson.Teardrop -> BackgroundShape.Teardrop
    is ShapeJson.Squircle -> BackgroundShape.Squircle(exponent)
    is ShapeJson.RoundedSquare -> BackgroundShape.RoundedSquare(cornerRadius)
}

private fun Theme.toJson() = ThemeJson(
    shape.run { ShapeParamsJson(strokeWeight, cap, join, miterLimit, iconScale, offsetX, offsetY, dots) },
    fill.run { FillParamsJson(mode, gapWidth, gapClosing, fallbackThreshold, keepLoneLines) },
    background.run { BackgroundJson(enabled, shape, size, border?.let { BorderJson(it.width, it.argb, it.opacity) }) },
    layers.map { it.toJson() },
)

private fun ThemeJson.toTheme() = Theme(
    shape.run { ShapeParams(strokeWeight, cap, join, miterLimit, iconScale, offsetX, offsetY, dots) },
    fill.run { FillParams(mode, gapWidth, gapClosing, fallbackThreshold, keepLoneLines) },
    background.run { BackgroundParams(enabled, shape, size, border?.let { Border(it.width, it.color, it.opacity) }) },
    layers.map { it.toLayer() },
)
