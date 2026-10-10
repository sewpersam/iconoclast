package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private enum class PaintKind { SOLID, LINEAR, RADIAL, TEXTURE }

/**
 * The background paint, spec 2.3: solid colour, linear gradient or radial gradient. The
 * texture option is M5 and shows disabled. Gradients edit their first and last stops; a
 * linear gradient is set by its angle through the canvas centre, a radial one by its
 * centre and radius.
 */
@Composable
fun PaintControl(paint: Paint, default: Paint, onDrag: (Paint) -> Unit, onDone: () -> Unit, onCommit: (Paint) -> Unit) {
    val kind = when (paint) {
        is Paint.Solid -> PaintKind.SOLID
        is Paint.LinearGradient -> PaintKind.LINEAR
        is Paint.RadialGradient -> PaintKind.RADIAL
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            "Paint",
            listOf(PaintKind.SOLID to "Solid", PaintKind.LINEAR to "Linear", PaintKind.RADIAL to "Radial", PaintKind.TEXTURE to "Texture"),
            selected = kind,
            default = kindOf(default),
            onSelect = { if (it == kindOf(default)) onCommit(default) else onCommit(convert(paint, it)) },
            enabled = { it != PaintKind.TEXTURE },
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
        }
    }
}

@Composable
private fun Stops(stops: List<ColorStop>, onDrag: (List<ColorStop>) -> Unit, onDone: () -> Unit, onCommit: (List<ColorStop>) -> Unit) {
    fun withFirst(c: Int) = listOf(stops.first().copy(argb = c)) + stops.drop(1)
    fun withLast(c: Int) = stops.dropLast(1) + stops.last().copy(argb = c)
    ColorControl("Start colour", stops.first().argb, DEFAULT_COLOR, { onDrag(withFirst(it)) }, onDone, { onCommit(withFirst(it)) })
    if (stops.size > 1) {
        ColorControl("End colour", stops.last().argb, DEFAULT_END, { onDrag(withLast(it)) }, onDone, { onCommit(withLast(it)) })
    }
}

private fun kindOf(p: Paint) = when (p) {
    is Paint.Solid -> PaintKind.SOLID
    is Paint.LinearGradient -> PaintKind.LINEAR
    is Paint.RadialGradient -> PaintKind.RADIAL
}

/** Keeps the colours across a change of kind. A solid colour becomes a gradient from it to black. */
private fun convert(p: Paint, to: PaintKind): Paint {
    val stops = when (p) {
        is Paint.Solid -> listOf(ColorStop(0f, p.argb), ColorStop(1f, DEFAULT_END))
        is Paint.LinearGradient -> p.stops
        is Paint.RadialGradient -> p.stops
    }
    return when (to) {
        PaintKind.SOLID -> Paint.Solid(stops.first().argb)
        PaintKind.LINEAR -> linear(DEFAULT_ANGLE, stops)
        PaintKind.RADIAL -> Paint.RadialGradient(0.5f, 0.5f, DEFAULT_RADIUS, stops)
        PaintKind.TEXTURE -> p
    }
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
