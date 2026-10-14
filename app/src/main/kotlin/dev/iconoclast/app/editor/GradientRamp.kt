package dev.iconoclast.app.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ColorStop
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * A gradient's colours as a ramp, docs/naming-spec.md 2.10: a bar showing the gradient with
 * a handle under each colour. Tap the bar to add a colour, drag a handle to move it, drag
 * it off the bar or long-press it to remove it, tap it to edit its colour below. At least
 * two colours. A move is a drag, one undo step; adding and removing commit.
 */
@Composable
fun GradientRamp(tip: Tip, stops: List<ColorStop>, onDrag: (List<ColorStop>) -> Unit, onDone: () -> Unit, onCommit: (List<ColorStop>) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    // Bumped by a tap on a handle, so the picker opens for the tapped colour.
    var opened by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf(-1) }
    val sel = selected.coerceIn(0, stops.size - 1)
    val current by rememberUpdatedState(stops)
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val handle = 22.dp
    val barHeight = 36.dp
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip)
        // The handles' half width is margin either side, so the end handles sit inside the touch area.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = with(density) { (handle / 2).toPx() }
            val widthPx = with(density) { maxWidth.toPx() } - 2 * pad
            val off = with(density) { 56.dp.toPx() }
            val reach = with(density) { (handle / 2 + 6.dp).toPx() }
            Box(
                Modifier.fillMaxWidth().height(barHeight + handle + 8.dp)
                    .semantics { contentDescription = "${tip.label}: ${stops.size} colours. Tap to add a colour." }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val s = current
                            val t0 = ((down.position.x - pad) / widthPx).coerceIn(0f, 1f)
                            val hit = s.indices.minByOrNull { abs(pad + s[it].offset * widthPx - down.position.x) }
                                ?.takeIf { abs(pad + s[it].offset * widthPx - down.position.x) <= reach }
                            var moved = false
                            var off_ = false
                            var work = s
                            // Long-press on a handle removes it; any move or lift first cancels that.
                            val up = withTimeoutOrNull(longPress) {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                    if (!c.pressed) return@withTimeoutOrNull true
                                    if ((c.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
                                }
                                @Suppress("UNREACHABLE_CODE")
                                false
                            }
                            if (up == null) {
                                if (hit != null && s.size > 2) { onCommit(Ramp.remove(s, hit)); selected = 0 }
                                // Wait for the lift so the press does nothing more.
                                while (awaitPointerEvent().changes.any { it.pressed }) Unit
                                return@awaitEachGesture
                            }
                            if (up == true) {
                                if (hit != null) { selected = hit; opened++ }
                                else { val r = Ramp.add(s, t0); onCommit(r.first); selected = r.second; opened++ }
                                return@awaitEachGesture
                            }
                            if (hit == null) {
                                while (awaitPointerEvent().changes.any { it.pressed }) Unit
                                return@awaitEachGesture
                            }
                            selected = hit
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                off_ = abs(c.position.y - down.position.y) > off && s.size > 2
                                removing = if (off_) hit else -1
                                val t = ((c.position.x - pad) / widthPx).coerceIn(0f, 1f)
                                work = Ramp.move(s, hit, t)
                                moved = true
                                onDrag(work)
                            }
                            removing = -1
                            when {
                                off_ -> { onCommit(Ramp.remove(s, hit)); selected = 0 }
                                moved -> onDone()
                            }
                        }
                    },
            ) {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.padding(horizontal = handle / 2).fillMaxWidth().height(barHeight).clip(shape)
                        .drawBehind {
                            checkerboard()
                            drawRect(Brush.horizontalGradient(*Ramp.brushStops(stops)))
                        }
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                )
                for ((i, s) in stops.withIndex()) {
                    val x = with(density) { (s.offset * widthPx).toDp() }
                    val picked = i == sel
                    Box(
                        Modifier.offset(x = x, y = barHeight + 4.dp).size(handle)
                            .alpha(if (removing == i) 0.3f else 1f)
                            .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(if (picked) 4.dp else 2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .drawBehind { checkerboard(4.dp.toPx()); drawRect(Color(s.argb)) }
                            .semantics { contentDescription = "Colour ${i + 1} at ${percent(s.offset)}, ${hex(s.argb)}" },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { val r = Ramp.add(stops, Ramp.widestGap(stops)); onCommit(r.first); selected = r.second; opened++ }) { Text("Add colour") }
            TextButton(onClick = { onCommit(Ramp.remove(stops, sel)); selected = 0 }, enabled = stops.size > 2) { Text("Remove colour") }
            TextButton(onClick = { onCommit(Ramp.even(stops)) }, enabled = !Ramp.isEven(stops)) { Text("Space evenly") }
        }
        key(sel, opened) {
            ColorControl(Tips.STOP_COLOUR, stops[sel].argb, stops[sel].argb,
                { onDrag(Ramp.recolour(stops, sel, it)) }, onDone, { onCommit(Ramp.recolour(stops, sel, it)) }, startOpen = opened > 0)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.checkerboard(cell: Float = 6.dp.toPx()) {
    drawRect(Color.White)
    var y = 0
    while (y * cell < size.height) {
        var x = y % 2
        while (x * cell < size.width) {
            drawRect(Color(0xFFCCCCCC), Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell))
            x += 2
        }
        y++
    }
}

/**
 * The ramp's edits as pure functions of the stop list, ascending offsets.
 *
 * A stop is untouched while it sits at its even place, i / (n - 1). Adding a colour keeps
 * the moved stops where they are and spreads the new one and the untouched ones evenly
 * between them: with nothing moved, every stop ends up evenly spaced, as the owner asked
 * ("they default to equidistant, but you can move them around").
 */
object Ramp {
    private const val EVEN_TOLERANCE = 0.005f

    fun isEven(stops: List<ColorStop>): Boolean = stops.withIndex().all { (i, s) -> untouched(i, s, stops.size) }

    private fun untouched(i: Int, s: ColorStop, n: Int) = abs(s.offset - i.toFloat() / (n - 1)) <= EVEN_TOLERANCE

    fun even(stops: List<ColorStop>): List<ColorStop> = stops.mapIndexed { i, s -> s.copy(offset = i.toFloat() / (stops.size - 1)) }

    /** Adds a colour where the gradient is at [t]; returns the stops and the new one's index. */
    fun add(stops: List<ColorStop>, t: Float): Pair<List<ColorStop>, Int> {
        val pinned = stops.withIndex().map { (i, s) -> !untouched(i, s, stops.size) }
        val at = stops.indexOfFirst { it.offset > t }.let { if (it < 0) stops.size else it }
        val list = stops.toMutableList().apply { add(at, ColorStop(t, colourAt(stops, t))) }
        val pin = pinned.toMutableList().apply { add(at, false) }
        return spread(list, pin) to at
    }

    /** Untouched stops evenly between the pinned ones; the ends, if untouched, at 0 and 1. */
    private fun spread(list: List<ColorStop>, pinned: List<Boolean>): List<ColorStop> {
        val n = list.size
        val anchors = (0 until n).filter { pinned[it] || it == 0 || it == n - 1 }
        val pos = FloatArray(n) { list[it].offset }
        for (a in anchors) if (!pinned[a]) pos[a] = if (a == 0) 0f else 1f
        for ((a, b) in anchors.zipWithNext()) for (i in a + 1 until b) pos[i] = pos[a] + (pos[b] - pos[a]) * (i - a) / (b - a)
        // Keep the order whatever the pinned stops did.
        for (i in 1 until n) pos[i] = maxOf(pos[i], pos[i - 1])
        return list.mapIndexed { i, s -> s.copy(offset = pos[i]) }
    }

    /** Stop [i] moved to [t], held between its neighbours. */
    fun move(stops: List<ColorStop>, i: Int, t: Float): List<ColorStop> {
        val lo = stops.getOrNull(i - 1)?.offset ?: 0f
        val hi = stops.getOrNull(i + 1)?.offset ?: 1f
        return stops.mapIndexed { j, s -> if (j == i) s.copy(offset = t.coerceIn(lo, hi)) else s }
    }

    fun remove(stops: List<ColorStop>, i: Int): List<ColorStop> = if (stops.size <= 2) stops else stops.filterIndexed { j, _ -> j != i }

    fun recolour(stops: List<ColorStop>, i: Int, argb: Int): List<ColorStop> = stops.mapIndexed { j, s -> if (j == i) s.copy(argb = argb) else s }

    /** The middle of the widest gap between neighbours: where Add colour puts one. */
    fun widestGap(stops: List<ColorStop>): Float =
        stops.zipWithNext().maxByOrNull { (a, b) -> b.offset - a.offset }?.let { (a, b) -> (a.offset + b.offset) / 2 } ?: 0.5f

    /** Straight ARGB at [t], each channel interpolated, clamped at the ends. */
    fun colourAt(stops: List<ColorStop>, t: Float): Int {
        if (t <= stops.first().offset) return stops.first().argb
        if (t >= stops.last().offset) return stops.last().argb
        val i = stops.indexOfLast { it.offset <= t }
        val a = stops[i]
        val b = stops[i + 1]
        val f = if (b.offset == a.offset) 1f else (t - a.offset) / (b.offset - a.offset)
        var out = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val ca = a.argb ushr shift and 0xFF
            val cb = b.argb ushr shift and 0xFF
            out = out or (Math.round(ca + (cb - ca) * f).coerceIn(0, 255) shl shift)
        }
        return out
    }

    fun brushStops(stops: List<ColorStop>): Array<Pair<Float, Color>> {
        val s = stops.map { it.offset to Color(it.argb) }
        return (if (s.size == 1) s + (1f to s[0].second) else s).toTypedArray()
    }
}
