package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.texture.BuiltInBrushes
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of paint a fill can be. */
enum class PaintKind { SOLID, GRADIENT, IMAGE, PATTERN }

/** A gradient's type: the toggle under the one Gradient choice. */
enum class GradientType { LINEAR, RADIAL, ANGULAR }

private enum class ImageColours { ORIGINAL, TINT, RECOLOUR }

/**
 * What an image source needs from the editor: open the image chooser, an image's file for
 * its thumbnail, and the crop step for the layer's image.
 */
class TextureActions(val choose: () -> Unit, val file: (String) -> File, val crop: () -> Unit = {})

/**
 * A layer's fill: a solid colour, a gradient, and with [textures] also an image or a
 * pattern. [tip] names the fill on its page: "Line colour", "Background colour", "Fill".
 * Edits come back as the whole [layer], since an image's placement is the layer's mapping.
 *
 * Switching kinds never loses work: the last paint of each kind is remembered per layer,
 * and switching back restores it. A gradient is one choice with a type toggle and a colour
 * ramp. An image shows its picture, Change and Crop, its colours, and how it sits on each
 * icon, random placement included. A pattern's placement is under [more].
 */
@Composable
fun PaintControl(
    tip: Tip,
    layer: PaintLayer,
    default: Paint,
    onDrag: (PaintLayer) -> Unit,
    onDone: () -> Unit,
    onCommit: (PaintLayer) -> Unit,
    textures: TextureActions? = null,
    more: Boolean = false,
) {
    val paint = layer.source
    val kind = kindOf(paint)
    val last = remember(layer.id) { HashMap<PaintKind, Paint>() }
    last[kind] = paint
    val dragP: (Paint) -> Unit = { onDrag(withSource(layer, it)) }
    val commitP: (Paint) -> Unit = { onCommit(withSource(layer, it)) }
    val kinds = listOf(PaintKind.SOLID to "Solid", PaintKind.GRADIENT to "Gradient") +
        if (textures != null || kind == PaintKind.IMAGE || kind == PaintKind.PATTERN) listOf(PaintKind.IMAGE to "Image", PaintKind.PATTERN to "Pattern") else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            tip, kinds, selected = kind, default = kindOf(default),
            onSelect = {
                val remembered = last[it]
                when {
                    it == kind -> Unit
                    remembered != null -> commitP(remembered)
                    it == PaintKind.IMAGE -> textures?.choose?.invoke()
                    else -> commitP(convert(paint, it))
                }
            },
        )
        when (paint) {
            is Paint.Solid -> ColorControl(Tips.COLOUR, paint.argb, (default as? Paint.Solid)?.argb ?: DEFAULT_COLOR,
                { dragP(Paint.Solid(it)) }, onDone, { commitP(Paint.Solid(it)) })
            is Paint.LinearGradient, is Paint.RadialGradient, is Paint.AngularGradient -> GradientSource(paint, dragP, onDone, commitP, more)
            is Paint.Texture -> {
                ImageSource(paint, textures, dragP, onDone, commitP)
                PlacementControls(layer.mapping, LayerStack.NEW_TEXTURE_MAPPING, image = true, more = more,
                    { onDrag(layer.copy(mapping = it)) }, onDone, { onCommit(layer.copy(mapping = it)) })
            }
            is Paint.Noise -> {
                PatternSource(paint, dragP, onDone, commitP, more)
                if (more) PlacementControls(layer.mapping, Mapping(), image = false, more = true,
                    { onDrag(layer.copy(mapping = it)) }, onDone, { onCommit(layer.copy(mapping = it)) })
            }
        }
    }
}

@Composable
private fun GradientSource(paint: Paint, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit, more: Boolean) {
    val stops = stopsOf(paint)
    val type = typeOf(paint)
    ChoiceControl(
        Tips.GRADIENT_TYPE, listOf(GradientType.LINEAR to "Linear", GradientType.RADIAL to "Radial", GradientType.ANGULAR to "Angular"),
        type, GradientType.LINEAR, { if (it != type) onCommit(gradient(it, paint, stops)) },
    )
    when (paint) {
        is Paint.LinearGradient -> {
            val angle = angleOf(paint)
            SliderControl(Tips.DIRECTION, angle, 0f..360f, DEFAULT_ANGLE, decimals(0, "°"),
                { onDrag(linear(it, paint.stops)) }, onDone, { onCommit(linear(DEFAULT_ANGLE, paint.stops)) })
        }
        is Paint.RadialGradient -> {
            SliderControl(Tips.GRADIENT_SIZE, paint.radius, RADIUS_RANGE, DEFAULT_RADIUS, percent,
                { onDrag(paint.copy(radius = it)) }, onDone, { onCommit(paint.copy(radius = DEFAULT_RADIUS)) })
            if (more || paint.cx != 0.5f || paint.cy != 0.5f) {
                SliderControl(Tips.CENTRE_X, paint.cx, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cx = it)) }, onDone, { onCommit(paint.copy(cx = 0.5f)) })
                SliderControl(Tips.CENTRE_Y, paint.cy, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cy = it)) }, onDone, { onCommit(paint.copy(cy = 0.5f)) })
            }
        }
        is Paint.AngularGradient -> {
            SliderControl(Tips.START_ANGLE, paint.angle, 0f..360f, DEFAULT_START, decimals(0, "°"),
                { onDrag(paint.copy(angle = it)) }, onDone, { onCommit(paint.copy(angle = DEFAULT_START)) })
            if (more || paint.cx != 0.5f || paint.cy != 0.5f) {
                SliderControl(Tips.SWEEP_CENTRE_X, paint.cx, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cx = it)) }, onDone, { onCommit(paint.copy(cx = 0.5f)) })
                SliderControl(Tips.SWEEP_CENTRE_Y, paint.cy, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cy = it)) }, onDone, { onCommit(paint.copy(cy = 0.5f)) })
            }
        }
        else -> Unit
    }
    GradientRamp(Tips.GRADIENT_COLOURS, stops, { onDrag(withStops(paint, it)) }, onDone, { onCommit(withStops(paint, it)) })
}

private fun typeOf(p: Paint) = when (p) {
    is Paint.RadialGradient -> GradientType.RADIAL
    is Paint.AngularGradient -> GradientType.ANGULAR
    else -> GradientType.LINEAR
}

/** The gradient of [type] with [stops]; a radial or angular one keeps the centre it had. */
private fun gradient(type: GradientType, from: Paint, stops: List<ColorStop>): Paint {
    val (cx, cy) = when (from) {
        is Paint.RadialGradient -> from.cx to from.cy
        is Paint.AngularGradient -> from.cx to from.cy
        else -> 0.5f to 0.5f
    }
    return when (type) {
        GradientType.LINEAR -> linear(DEFAULT_ANGLE, stops)
        GradientType.RADIAL -> Paint.RadialGradient(cx, cy, DEFAULT_RADIUS, stops)
        GradientType.ANGULAR -> Paint.AngularGradient(cx, cy, DEFAULT_START, stops)
    }
}

private fun withStops(p: Paint, stops: List<ColorStop>): Paint = when (p) {
    is Paint.LinearGradient -> p.copy(stops = stops)
    is Paint.RadialGradient -> p.copy(stops = stops)
    is Paint.AngularGradient -> p.copy(stops = stops)
    is Paint.Noise -> p.copy(stops = stops)
    is Paint.Texture -> p.copy(stops = stops)
    is Paint.Solid -> p
}

@Composable
private fun ImageSource(paint: Paint.Texture, textures: TextureActions?, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit) {
    HelpAnchor(Tips.CHANGE_IMAGE) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = imageName(paint.sha256)
            textures?.let { TextureThumbnail(paint.sha256, it.file, "Image: $name", 56.dp) }
            Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            if (textures != null) {
                TextButton(onClick = textures.crop) { Text(Tips.CROP.label) }
                TextButton(onClick = textures.choose) { Text(Tips.CHANGE_IMAGE.label) }
            }
        }
    }
    val colours = when (paint.stops?.size) {
        null -> ImageColours.ORIGINAL
        1 -> ImageColours.TINT
        else -> ImageColours.RECOLOUR
    }
    ChoiceControl(
        Tips.IMAGE_COLOURS,
        listOf(ImageColours.ORIGINAL to "Original", ImageColours.TINT to "Tint", ImageColours.RECOLOUR to "Recolour"),
        colours, ImageColours.ORIGINAL,
        { onCommit(paint.copy(stops = imageStops(it, paint.stops))) },
    )
    paint.stops?.let { stops -> Stops(stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) }) }
}

/**
 * How an image or pattern sits on each icon: zoom and turn for all, then how far each
 * icon's own patch moves, turns and zooms at random, and Shuffle for a new arrangement.
 * An image shows them in view; its placement mode is under More options.
 */
@Composable
private fun PlacementControls(
    m: Mapping, d: Mapping, image: Boolean, more: Boolean,
    onDrag: (Mapping) -> Unit, onDone: () -> Unit, onCommit: (Mapping) -> Unit,
) {
    Section(if (image) "Placement" else "Pattern placement")
    if (image && more) {
        ChoiceControl(Tips.IMAGE_PLACEMENT, listOf(MappingMode.FILL to "Cover the tile", MappingMode.FIT to "Show whole image", MappingMode.TILE to "Repeat"),
            m.mode, d.mode, { onCommit(m.copy(mode = it)) })
    }
    SliderControl(Tips.ZOOM, m.scale, 0.25f..4f, d.scale, decimals(2, "×"), { onDrag(m.copy(scale = it)) }, onDone, { onCommit(m.copy(scale = d.scale)) })
    SliderControl(Tips.ROTATE, m.rotation, -180f..180f, d.rotation, decimals(0, "°"),
        { onDrag(m.copy(rotation = it)) }, onDone, { onCommit(m.copy(rotation = d.rotation)) })
    SliderControl(Tips.RANDOM_SHIFT, m.randomShift, 0f..1f, d.randomShift, percent,
        { onDrag(m.copy(randomShift = it)) }, onDone, { onCommit(m.copy(randomShift = d.randomShift)) })
    SliderControl(Tips.RANDOM_ROTATION, m.randomRotation, 0f..180f, d.randomRotation, decimals(0, "°"),
        { onDrag(m.copy(randomRotation = it)) }, onDone, { onCommit(m.copy(randomRotation = d.randomRotation)) })
    SliderControl(Tips.RANDOM_SIZE, m.randomScale, 0f..1f, d.randomScale, percent,
        { onDrag(m.copy(randomScale = it)) }, onDone, { onCommit(m.copy(randomScale = d.randomScale)) })
    ShuffleControl(Tips.SHUFFLE_PLACEMENT, m.seed != d.seed, { onCommit(m.copy(seed = newSeed())) }, { onCommit(m.copy(seed = d.seed)) })
}

/** A new arrangement; never 0, which is each icon's own. */
private fun newSeed(): Int = Random.nextInt().let { if (it == 0) 1 else it }

/** A built-in image by its name; an imported one is "Your image". */
fun imageName(sha256: String): String = if (BuiltInBrushes.isBuiltIn(sha256)) textureName(sha256) else "Your image"

@Composable
private fun PatternSource(paint: Paint.Noise, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit, more: Boolean) {
    val d = Paint.Noise()
    ChoiceControl(
        Tips.PATTERN, NoiseKind.entries.map { it to LayerStack.patternName(it) },
        paint.kind, d.kind, { onCommit(paint.copy(kind = it)) },
    )
    if (paint.kind != NoiseKind.GRAIN && (more || paint.octaves != d.octaves)) {
        SliderControl(Tips.DETAIL, paint.octaves.toFloat(), 1f..8f, d.octaves.toFloat(), decimals(0),
            { onDrag(paint.copy(octaves = it.roundToInt().coerceIn(1, 8))) }, onDone, { onCommit(paint.copy(octaves = d.octaves)) })
    }
    ShuffleControl(Tips.SHUFFLE_PATTERN, paint.seed != d.seed, { onCommit(paint.copy(seed = Random.nextInt())) }, { onCommit(paint.copy(seed = d.seed)) })
    Stops(paint.stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) })
}

/** An image's or pattern's colours: one tint colour, or a ramp from its darks to its lights. */
@Composable
private fun Stops(stops: List<ColorStop>, onDrag: (List<ColorStop>) -> Unit, onDone: () -> Unit, onCommit: (List<ColorStop>) -> Unit) {
    if (stops.size == 1) {
        fun withFirst(c: Int) = listOf(stops.first().copy(argb = c))
        ColorControl(Tips.TINT_COLOUR, stops.first().argb, WHITE, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
        return
    }
    GradientRamp(Tips.DARK_TO_LIGHT, stops, onDrag, onDone, onCommit)
}

fun kindOf(p: Paint) = when (p) {
    is Paint.Solid -> PaintKind.SOLID
    is Paint.LinearGradient, is Paint.RadialGradient, is Paint.AngularGradient -> PaintKind.GRADIENT
    is Paint.Texture -> PaintKind.IMAGE
    is Paint.Noise -> PaintKind.PATTERN
}

private fun stopsOf(p: Paint): List<ColorStop> = when (p) {
    is Paint.Solid -> listOf(ColorStop(0f, p.argb), ColorStop(1f, DEFAULT_END))
    is Paint.LinearGradient -> p.stops
    is Paint.RadialGradient -> p.stops
    is Paint.AngularGradient -> p.stops
    is Paint.Texture -> p.stops?.takeIf { it.size > 1 } ?: listOf(ColorStop(0f, DEFAULT_END), ColorStop(1f, WHITE))
    is Paint.Noise -> p.stops
}.let { if (it.size >= 2) it else listOf(it.first().copy(offset = 0f), it.first().copy(offset = 1f)) }

/** A first visit to a kind keeps the colours: a solid colour becomes a gradient from it to black. */
private fun convert(p: Paint, to: PaintKind): Paint {
    val stops = stopsOf(p)
    return when (to) {
        PaintKind.SOLID -> Paint.Solid(stops.first().argb)
        PaintKind.GRADIENT -> linear(DEFAULT_ANGLE, stops)
        PaintKind.PATTERN -> Paint.Noise(stops = stops)
        PaintKind.IMAGE -> p
    }
}

/** Original: no stops. Tint: the image's light parts in one colour. Recolour: dark to light. */
private fun imageStops(mode: ImageColours, current: List<ColorStop>?): List<ColorStop>? = when (mode) {
    ImageColours.ORIGINAL -> null
    ImageColours.TINT -> listOf(ColorStop(0f, current?.last()?.argb ?: WHITE))
    ImageColours.RECOLOUR -> if (current != null && current.size > 1) current
        else listOf(ColorStop(0f, DEFAULT_END), ColorStop(1f, current?.first()?.argb ?: WHITE))
}

/** 0 degrees runs left to right, 90 top to bottom; the line spans the canvas through its centre. */
fun linear(angle: Float, stops: List<ColorStop>): Paint.LinearGradient {
    val a = Math.toRadians(angle.toDouble())
    val dx = (cos(a) / 2).toFloat()
    val dy = (sin(a) / 2).toFloat()
    return Paint.LinearGradient(0.5f - dx, 0.5f - dy, 0.5f + dx, 0.5f + dy, stops)
}

fun angleOf(p: Paint.LinearGradient): Float {
    val deg = Math.toDegrees(atan2((p.y1 - p.y0).toDouble(), (p.x1 - p.x0).toDouble())).toFloat()
    return (deg + 360f) % 360f
}

private const val DEFAULT_ANGLE = 90f
private const val DEFAULT_START = 270f
private const val DEFAULT_RADIUS = 0.75f
private val RADIUS_RANGE = 0.1f..1.5f
private const val DEFAULT_COLOR = 0xFF202124.toInt()
private const val DEFAULT_END = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
