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
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.texture.BuiltInBrushes
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of paint a fill can be. The seam for the next pass's image fill and gradient ramp. */
enum class PaintKind { SOLID, GRADIENT, IMAGE, PATTERN }

private enum class GradientShape { STRAIGHT, ROUND }

private enum class ImageColours { ORIGINAL, TINT, RECOLOUR }

/** What an image source needs from the editor: open the image chooser, and an image's file for its thumbnail. */
class TextureActions(val choose: () -> Unit, val file: (String) -> File)

/**
 * A fill: a solid colour, a gradient, and with [textures] also an image or a pattern.
 * [tip] names the fill on its page: "Line colour", "Background colour", "Fill".
 *
 * Switching kinds never loses work: the last paint of each kind is remembered under
 * [memory], one per layer, and switching back restores it. A gradient edits its first and
 * last colours; a straight one is set by its direction, a round one by its size, and with
 * [more] its centre. A pattern's detail shows with [more] too.
 */
@Composable
fun PaintControl(
    tip: Tip,
    paint: Paint,
    default: Paint,
    onDrag: (Paint) -> Unit,
    onDone: () -> Unit,
    onCommit: (Paint) -> Unit,
    memory: String,
    textures: TextureActions? = null,
    more: Boolean = false,
) {
    val kind = kindOf(paint)
    val last = remember(memory) { HashMap<PaintKind, Paint>() }
    last[kind] = paint
    val kinds = listOf(PaintKind.SOLID to "Solid", PaintKind.GRADIENT to "Gradient") +
        if (textures != null || kind == PaintKind.IMAGE || kind == PaintKind.PATTERN) listOf(PaintKind.IMAGE to "Image", PaintKind.PATTERN to "Pattern") else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            tip, kinds, selected = kind, default = kindOf(default),
            onSelect = {
                val remembered = last[it]
                when {
                    it == kind -> Unit
                    remembered != null -> onCommit(remembered)
                    it == PaintKind.IMAGE -> textures?.choose?.invoke()
                    else -> onCommit(convert(paint, it))
                }
            },
        )
        when (paint) {
            is Paint.Solid -> ColorControl(Tips.COLOUR, paint.argb, (default as? Paint.Solid)?.argb ?: DEFAULT_COLOR,
                { onDrag(Paint.Solid(it)) }, onDone, { onCommit(Paint.Solid(it)) })
            is Paint.LinearGradient, is Paint.RadialGradient -> GradientSource(paint, onDrag, onDone, onCommit, more)
            is Paint.Texture -> ImageSource(paint, textures, onDrag, onDone, onCommit)
            is Paint.Noise -> PatternSource(paint, onDrag, onDone, onCommit, more)
        }
    }
}

@Composable
private fun GradientSource(paint: Paint, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit, more: Boolean) {
    val stops = stopsOf(paint)
    val shape = if (paint is Paint.RadialGradient) GradientShape.ROUND else GradientShape.STRAIGHT
    ChoiceControl(
        Tips.GRADIENT_SHAPE, listOf(GradientShape.STRAIGHT to "Straight", GradientShape.ROUND to "Round"), shape, GradientShape.STRAIGHT,
        { if (it != shape) onCommit(if (it == GradientShape.ROUND) Paint.RadialGradient(0.5f, 0.5f, DEFAULT_RADIUS, stops) else linear(DEFAULT_ANGLE, stops)) },
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
        else -> Unit
    }
    Stops(stops, Tips.START_COLOUR, Tips.END_COLOUR, { onDrag(withStops(paint, it)) }, onDone, { onCommit(withStops(paint, it)) })
}

private fun withStops(p: Paint, stops: List<ColorStop>): Paint = when (p) {
    is Paint.LinearGradient -> p.copy(stops = stops)
    is Paint.RadialGradient -> p.copy(stops = stops)
    is Paint.Noise -> p.copy(stops = stops)
    is Paint.Texture -> p.copy(stops = stops)
    is Paint.Solid -> p
}

@Composable
private fun ImageSource(paint: Paint.Texture, textures: TextureActions?, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit) {
    HelpAnchor(Tips.CHANGE_IMAGE) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = imageName(paint.sha256)
            textures?.let { TextureThumbnail(paint.sha256, it.file, "Image: $name") }
            Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            if (textures != null) TextButton(onClick = textures.choose) { Text(Tips.CHANGE_IMAGE.label) }
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
    paint.stops?.let { stops -> Stops(stops, Tips.DARK_COLOUR, Tips.LIGHT_COLOUR, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) }) }
}

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
    Stops(paint.stops, Tips.DARK_COLOUR, Tips.LIGHT_COLOUR, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) })
}

@Composable
private fun Stops(stops: List<ColorStop>, first: Tip, last: Tip, onDrag: (List<ColorStop>) -> Unit, onDone: () -> Unit, onCommit: (List<ColorStop>) -> Unit) {
    fun withFirst(c: Int) = listOf(stops.first().copy(argb = c)) + stops.drop(1)
    fun withLast(c: Int) = stops.dropLast(1) + stops.last().copy(argb = c)
    if (stops.size == 1) {
        ColorControl(Tips.TINT_COLOUR, stops.first().argb, WHITE, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
        return
    }
    ColorControl(first, stops.first().argb, DEFAULT_COLOR, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
    ColorControl(last, stops.last().argb, DEFAULT_END, { onDrag(withLast(it)) }, onDone, { onCommit(withLast(it)) })
}

fun kindOf(p: Paint) = when (p) {
    is Paint.Solid -> PaintKind.SOLID
    is Paint.LinearGradient, is Paint.RadialGradient -> PaintKind.GRADIENT
    is Paint.Texture -> PaintKind.IMAGE
    is Paint.Noise -> PaintKind.PATTERN
}

private fun stopsOf(p: Paint): List<ColorStop> = when (p) {
    is Paint.Solid -> listOf(ColorStop(0f, p.argb), ColorStop(1f, DEFAULT_END))
    is Paint.LinearGradient -> p.stops
    is Paint.RadialGradient -> p.stops
    is Paint.Texture -> p.stops?.takeIf { it.size > 1 } ?: listOf(ColorStop(0f, DEFAULT_END), ColorStop(1f, WHITE))
    is Paint.Noise -> p.stops
}

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
private const val DEFAULT_RADIUS = 0.75f
private val RADIUS_RANGE = 0.1f..1.5f
private const val DEFAULT_COLOR = 0xFF202124.toInt()
private const val DEFAULT_END = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
