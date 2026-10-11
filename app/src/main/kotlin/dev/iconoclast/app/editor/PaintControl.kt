package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class PaintKind { SOLID, LINEAR, RADIAL, TEXTURE, NOISE }

private enum class TextureColours { OWN, ONE, MAP }

/** What a texture source needs from the editor: open the texture chooser, and a texture's file for its thumbnail. */
class TextureActions(val choose: () -> Unit, val file: (String) -> File)

/**
 * A paint, spec 2.3: solid colour, linear gradient or radial gradient, and with
 * [textures] also an uploaded texture or procedural noise. Gradients edit their first and
 * last stops; a linear gradient is set by its angle through the canvas centre, a radial
 * one by its centre and radius. A texture keeps its own colours, or maps its luminance to
 * one colour or a gradient.
 */
@Composable
fun PaintControl(
    paint: Paint,
    default: Paint,
    onDrag: (Paint) -> Unit,
    onDone: () -> Unit,
    onCommit: (Paint) -> Unit,
    textures: TextureActions? = null,
) {
    val kind = kindOf(paint)
    val kinds = listOf(PaintKind.SOLID to "Solid", PaintKind.LINEAR to "Linear", PaintKind.RADIAL to "Radial") +
        if (textures != null) listOf(PaintKind.TEXTURE to "Texture", PaintKind.NOISE to "Noise") else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            "Paint", kinds, selected = kind, default = kindOf(default),
            onSelect = {
                when {
                    it == kind -> Unit
                    it == kindOf(default) -> onCommit(default)
                    it == PaintKind.TEXTURE -> textures?.choose?.invoke()
                    else -> onCommit(convert(paint, it))
                }
            },
        )
        when (paint) {
            is Paint.Solid -> ColorControl("Colour", paint.argb, (default as? Paint.Solid)?.argb ?: DEFAULT_COLOR,
                { onDrag(Paint.Solid(it)) }, onDone, { onCommit(Paint.Solid(it)) })
            is Paint.LinearGradient -> {
                val angle = angleOf(paint)
                SliderControl("Angle", angle, 0f..360f, DEFAULT_ANGLE, decimals(0, "°"),
                    { onDrag(linear(it, paint.stops)) }, onDone, { onCommit(linear(DEFAULT_ANGLE, paint.stops)) })
                Stops(paint.stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) })
            }
            is Paint.RadialGradient -> {
                SliderControl("Centre X", paint.cx, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cx = it)) }, onDone, { onCommit(paint.copy(cx = 0.5f)) })
                SliderControl("Centre Y", paint.cy, 0f..1f, 0.5f, percent, { onDrag(paint.copy(cy = it)) }, onDone, { onCommit(paint.copy(cy = 0.5f)) })
                SliderControl("Radius", paint.radius, RADIUS_RANGE, DEFAULT_RADIUS, percent,
                    { onDrag(paint.copy(radius = it)) }, onDone, { onCommit(paint.copy(radius = DEFAULT_RADIUS)) })
                Stops(paint.stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) })
            }
            is Paint.Texture -> TextureSource(paint, textures, onDrag, onDone, onCommit)
            is Paint.Noise -> NoiseSource(paint, onDrag, onDone, onCommit)
        }
    }
}

@Composable
private fun TextureSource(paint: Paint.Texture, textures: TextureActions?, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        textures?.let { TextureThumbnail(it.file(paint.sha256), "Texture ${paint.sha256.take(8)}") }
        Text(paint.sha256.take(8), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        if (textures != null) TextButton(onClick = textures.choose) { Text("Change texture") }
    }
    val colours = when (paint.stops?.size) {
        null -> TextureColours.OWN
        1 -> TextureColours.ONE
        else -> TextureColours.MAP
    }
    ChoiceControl(
        "Texture colours",
        listOf(TextureColours.OWN to "Own colours", TextureColours.ONE to "One colour", TextureColours.MAP to "Gradient map"),
        colours, TextureColours.OWN,
        { onCommit(paint.copy(stops = textureStops(it, paint.stops))) },
    )
    paint.stops?.let { stops -> Stops(stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) }) }
}

@Composable
private fun NoiseSource(paint: Paint.Noise, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit) {
    val d = Paint.Noise()
    ChoiceControl(
        "Noise", listOf(NoiseKind.FBM to "fBm", NoiseKind.RIDGED to "Ridged", NoiseKind.GRAIN to "Grain"),
        paint.kind, d.kind, { onCommit(paint.copy(kind = it)) },
    )
    if (paint.kind != NoiseKind.GRAIN) {
        SliderControl("Octaves", paint.octaves.toFloat(), 1f..8f, d.octaves.toFloat(), decimals(0),
            { onDrag(paint.copy(octaves = it.roundToInt().coerceIn(1, 8))) }, onDone, { onCommit(paint.copy(octaves = d.octaves)) })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Seed", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(paint.seed.toString(), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onCommit(paint.copy(seed = Random.nextInt())) }) { Text("New seed") }
        ResetButton("Seed", enabled = paint.seed != d.seed) { onCommit(paint.copy(seed = d.seed)) }
    }
    Stops(paint.stops, { onDrag(paint.copy(stops = it)) }, onDone, { onCommit(paint.copy(stops = it)) })
}

@Composable
private fun Stops(stops: List<ColorStop>, onDrag: (List<ColorStop>) -> Unit, onDone: () -> Unit, onCommit: (List<ColorStop>) -> Unit) {
    fun withFirst(c: Int) = listOf(stops.first().copy(argb = c)) + stops.drop(1)
    fun withLast(c: Int) = stops.dropLast(1) + stops.last().copy(argb = c)
    if (stops.size == 1) {
        ColorControl("Colour", stops.first().argb, WHITE, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
        return
    }
    ColorControl("Start colour", stops.first().argb, DEFAULT_COLOR, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
    ColorControl("End colour", stops.last().argb, DEFAULT_END, { onDrag(withLast(it)) }, onDone, { onCommit(withLast(it)) })
}

private fun kindOf(p: Paint) = when (p) {
    is Paint.Solid -> PaintKind.SOLID
    is Paint.LinearGradient -> PaintKind.LINEAR
    is Paint.RadialGradient -> PaintKind.RADIAL
    is Paint.Texture -> PaintKind.TEXTURE
    is Paint.Noise -> PaintKind.NOISE
}

private fun stopsOf(p: Paint): List<ColorStop> = when (p) {
    is Paint.Solid -> listOf(ColorStop(0f, p.argb), ColorStop(1f, DEFAULT_END))
    is Paint.LinearGradient -> p.stops
    is Paint.RadialGradient -> p.stops
    is Paint.Texture -> p.stops?.takeIf { it.size > 1 } ?: listOf(ColorStop(0f, DEFAULT_END), ColorStop(1f, WHITE))
    is Paint.Noise -> p.stops
}

/** Keeps the colours across a change of kind. A solid colour becomes a gradient from it to black. */
private fun convert(p: Paint, to: PaintKind): Paint {
    val stops = stopsOf(p)
    return when (to) {
        PaintKind.SOLID -> Paint.Solid(stops.first().argb)
        PaintKind.LINEAR -> linear(DEFAULT_ANGLE, stops)
        PaintKind.RADIAL -> Paint.RadialGradient(0.5f, 0.5f, DEFAULT_RADIUS, stops)
        PaintKind.NOISE -> Paint.Noise(stops = stops)
        PaintKind.TEXTURE -> p
    }
}

/** Own colours: no stops. One colour: the texture's light parts in it. Gradient map: dark to light. */
private fun textureStops(mode: TextureColours, current: List<ColorStop>?): List<ColorStop>? = when (mode) {
    TextureColours.OWN -> null
    TextureColours.ONE -> listOf(ColorStop(0f, current?.last()?.argb ?: WHITE))
    TextureColours.MAP -> if (current != null && current.size > 1) current
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
