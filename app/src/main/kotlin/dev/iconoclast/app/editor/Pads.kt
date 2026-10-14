package dev.iconoclast.app.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A point picked on a square pad, docs/naming-spec.md style: the label row with the value
 * and a reset, then the pad. [x] and [y] are 0 to 1, y down; drag the dot anywhere, a
 * double tap puts it at [tapX], [tapY]. The pad takes the drag it starts, so
 * the page does not scroll under it. [cross] draws centre guides: an offset's zero.
 */
@Composable
fun PointPad(
    tip: Tip, x: Float, y: Float, canReset: Boolean, value: String,
    onDrag: (Float, Float) -> Unit, onDone: () -> Unit, onReset: () -> Unit, cross: Boolean = true,
    tapX: Float = 0.5f, tapY: Float = 0.5f,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip, value, canReset, onReset)
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(PAD).border(1.dp, c.outline, RoundedCornerShape(8.dp))
                    .padTouch(onDrag, onDone) { onDrag(tapX, tapY); onDone() }
                    .semantics { contentDescription = tip.label; stateDescription = value }
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        if (cross) {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                            drawLine(c.outlineVariant, Offset(w / 2, 0f), Offset(w / 2, h), 1.dp.toPx(), pathEffect = dash)
                            drawLine(c.outlineVariant, Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx(), pathEffect = dash)
                        }
                        val p = Offset(x * w, y * h)
                        if (cross) drawLine(c.onSurfaceVariant, Offset(w / 2, h / 2), p, 2.dp.toPx())
                        drawCircle(c.onSurface, 9.dp.toPx(), p)
                        drawCircle(c.surface, 9.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    },
            )
        }
    }
}

/**
 * An offset as a pad: the centre is no offset, the edges [max] either way, a double tap
 * centres it. [dx], [dy] are fractions of the tile, y down.
 */
@Composable
fun OffsetPad(tip: Tip, dx: Float, dy: Float, max: Float, canReset: Boolean, onDrag: (Float, Float) -> Unit, onDone: () -> Unit, onReset: () -> Unit) {
    val value = if (dx == 0f && dy == 0f) "None" else String.format(Locale.ROOT, "%.1f%%", hypot(dx, dy) * 100)
    PointPad(
        tip, 0.5f + dx / max / 2f, 0.5f + dy / max / 2f, canReset, value,
        { fx, fy -> onDrag((fx - 0.5f) * 2f * max, (fy - 0.5f) * 2f * max) }, onDone, onReset,
    )
}

/**
 * A direction as a dial: the line points at [degrees], counterclockwise from the right
 * with y up, as the effects store it. Drag round to turn it; double tap for [default].
 */
@Composable
fun DirectionDial(tip: Tip, degrees: Float, default: Float, onDrag: (Float) -> Unit, onDone: () -> Unit, onReset: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val value = String.format(Locale.ROOT, "%.0f°", degrees)
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip, value, degrees != default, onReset)
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(DIAL).border(1.dp, c.outline, CircleShape)
                    .padTouch({ fx, fy -> onDrag(angleOf(fx, fy)) }, onDone) { onDrag(default); onDone() }
                    .semantics { contentDescription = tip.label; stateDescription = value }
                    .drawWithContent {
                        val r = size.width / 2
                        val centre = Offset(r, r)
                        val a = Math.toRadians(degrees.toDouble())
                        val tipAt = centre + Offset((cos(a) * r * 0.8).toFloat(), (-sin(a) * r * 0.8).toFloat())
                        drawCircle(c.outlineVariant, 3.dp.toPx(), centre)
                        drawLine(c.onSurface, centre, tipAt, 2.dp.toPx())
                        drawCircle(c.onSurface, 8.dp.toPx(), tipAt)
                    },
            )
        }
    }
}

/** Degrees 0 to 360, counterclockwise from the right with y up, of a point in a unit box about its centre. */
private fun angleOf(fx: Float, fy: Float): Float {
    val d = Math.toDegrees(atan2(-(fy - 0.5f), fx - 0.5f).toDouble()).toFloat()
    return ((d + 360f) % 360f).let { Math.round(it).toFloat() % 360f }
}

/**
 * Press and drag anywhere: [onMove] gets the pointer as fractions of the box, clamped, and
 * [onEnd] when it lifts. Two taps in quick succession call [onDoubleTap] instead.
 */
@Composable
private fun Modifier.padTouch(onMove: (Float, Float) -> Unit, onEnd: () -> Unit, onDoubleTap: () -> Unit): Modifier {
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val double by rememberUpdatedState(onDoubleTap)
    val lastTap = remember { longArrayOf(0L) }
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val slop = viewConfiguration.touchSlop
            val doubleTap = down.uptimeMillis - lastTap[0] < viewConfiguration.doubleTapTimeoutMillis
            fun send(p: Offset) = move((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
            var moved = false
            if (!doubleTap) send(down.position)
            while (true) {
                val e = awaitPointerEvent()
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                if ((ch.position - down.position).getDistance() > slop) moved = true
                if (moved || !doubleTap) send(ch.position)
                ch.consume()
            }
            if (doubleTap && !moved) {
                lastTap[0] = 0L
                double()
            } else {
                lastTap[0] = if (moved) 0L else down.uptimeMillis
                end()
            }
        }
    }
}

private val PAD = 168.dp
private val DIAL = 120.dp
