package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A colour, docs/naming-spec.md 2.10: at rest one row, the name and a swatch; a tap opens
 * the colour picker. The picker is pure Compose: a square for intensity and brightness, a
 * hue bar, a see-through bar, the hex code, and the colours used most recently anywhere in
 * the app. [argb] is straight 0xAARRGGBB. Drags call [onDrag] and end with [onDone], one
 * undo step; a tap on a recent colour or the hex code commits.
 */
@Composable
fun ColorControl(
    tip: Tip,
    argb: Int,
    default: Int,
    onDrag: (Int) -> Unit,
    onDone: () -> Unit,
    onCommit: (Int) -> Unit,
    startOpen: Boolean = false,
) {
    val label = tip.label
    var open by rememberSaveable { mutableStateOf(startOpen) }
    val hex = hex(argb)
    val recents = RecentColours.of(LocalContext.current)
    Column(Modifier.fillMaxWidth()) {
        HelpAnchor(tip) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = if (open) "Close $label" else "Change $label") { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                ColourSwatch(argb, 28.dp, Modifier.semantics { contentDescription = "$label $hex" })
                ResetButton(label, enabled = argb != default) { onCommit(default) }
            }
        }
        if (open) ColourPicker(label, argb, onDrag, { onDone(); recents.add(it) }, { onCommit(it); recents.add(it) }, recents.colours)
    }
}

/** A colour on a checkerboard, so see-through shows. */
@Composable
fun ColourSwatch(argb: Int, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(
        modifier.size(size).clip(shape)
            .drawBehind { checker(); drawRect(Color(argb)) }
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    )
}

/** Light and mid grey squares behind a see-through colour. */
private fun DrawScope.checker(cell: Float = 6.dp.toPx()) {
    drawRect(Color(0xFFFFFFFF))
    var y = 0
    while (y * cell < size.height) {
        var x = y % 2
        while (x * cell < size.width) {
            drawRect(Color(0xFFCCCCCC), Offset(x * cell, y * cell), Size(cell, cell))
            x += 2
        }
        y++
    }
}

/**
 * The picker itself. Hue and intensity survive a trip through grey or black, where the
 * colour itself forgets them: [hsv] holds them while the colour passes through.
 */
@Composable
private fun ColourPicker(label: String, argb: Int, onDrag: (Int) -> Unit, onDone: (Int) -> Unit, onCommit: (Int) -> Unit, recent: List<Int>) {
    var hsv by remember { mutableStateOf(toHsv(argb)) }
    LaunchedEffect(argb) { if (fromHsv(hsv, argb) != argb) hsv = toHsv(argb) }
    // The latest colour a drag produced, for onDone, which fires after the last onDrag.
    val latest = remember { intArrayOf(argb) }
    latest[0] = argb
    fun set(h: FloatArray, alpha: Int = argb ushr 24) {
        hsv = h
        val c = fromHsv(h, alpha shl 24)
        latest[0] = c
        onDrag(c)
    }
    val done = { onDone(latest[0]) }
    val hueColour = Color.hsv(hsv[0], 1f, 1f)
    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Intensity left to right, brightness bottom to top.
        Box(
            Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, hueColour)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .drag2d({ fx, fy -> set(floatArrayOf(hsv[0], fx, 1f - fy)) }, done)
                .semantics {
                    contentDescription = "$label ${Tips.INTENSITY.label} and ${Tips.BRIGHTNESS.label}"
                    stateDescription = "${Tips.INTENSITY.label} ${percent(hsv[1])}, ${Tips.BRIGHTNESS.label} ${percent(hsv[2])}"
                }
                .drawWithContent {
                    drawContent()
                    val c = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
                    drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black, 10.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                },
        )
        PickerBar("$label ${Tips.HUE.label}", hsv[0] / 360f, "${Math.round(hsv[0])}°",
            { drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) },
            { set(floatArrayOf(it * 360f, hsv[1], hsv[2])) }, done)
        val opaque = Color(fromHsv(hsv, 0xFF shl 24))
        PickerBar("$label ${Tips.SEE_THROUGH.label}", (argb ushr 24) / 255f, percent((argb ushr 24) / 255f),
            { checker(); drawRect(Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque))) },
            { set(hsv, Math.round(it * 255f)) }, done)
        HexField(label, argb, onCommit)
        if (recent.isNotEmpty()) {
            Text(Tips.RECENT_COLOURS.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in recent) {
                    val picked = c == argb
                    ColourSwatch(c, 36.dp,
                        Modifier.clickable(role = Role.Button, onClickLabel = "$label ${hex(c)}") { onCommit(c) }
                            .semantics { contentDescription = "Recent colour ${hex(c)}"; selected = picked },
                        CircleShape)
                }
            }
        }
    }
}

/** A horizontal bar picking a fraction 0 to 1 by tap or drag, with a thumb. */
@Composable
private fun PickerBar(desc: String, fraction: Float, value: String, paint: DrawScope.() -> Unit, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .drawBehind(paint)
            .drag2d({ fx, _ -> onChange(fx) }, onDone)
            .semantics {
                contentDescription = desc
                stateDescription = value
                setProgress { onChange(it.coerceIn(0f, 1f)); onDone(); true }
            }
            .drawWithContent {
                drawContent()
                val x = (fraction.coerceIn(0f, 1f) * (size.width - size.height) + size.height / 2)
                drawCircle(Color.White, size.height / 2 - 3.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black, size.height / 2 - 1.dp.toPx(), Offset(x, size.height / 2), style = Stroke(1.dp.toPx()))
            },
    )
}

/** Tap or drag anywhere: [onMove] gets the pointer as fractions of the box, clamped; [onEnd] when it lifts. */
@Composable
private fun Modifier.drag2d(onMove: (Float, Float) -> Unit, onEnd: () -> Unit): Modifier {
    // The gesture outlives recompositions; it calls whatever the latest composition passed.
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) { awaitEachGesture {
        val down = awaitFirstDown()
        fun send(p: Offset) = move((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
        send(down.position)
        down.consume()
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            send(c.position)
            c.consume()
        }
        end()
    } }
}

/**
 * The last [MAX] colours picked anywhere, newest first, kept in preferences. One per
 * process, read on first use.
 */
class RecentColours private constructor(private val prefs: android.content.SharedPreferences) {
    val colours = mutableStateListOf<Int>().apply {
        prefs.getString(KEY, null)?.split(',')?.mapNotNull { it.toLongOrNull(16)?.toInt() }?.let { addAll(it.take(MAX)) }
    }

    fun add(c: Int) {
        colours.remove(c)
        colours.add(0, c)
        while (colours.size > MAX) colours.removeAt(colours.size - 1)
        prefs.edit().putString(KEY, colours.joinToString(",") { "%08X".format(it) }).apply()
    }

    companion object {
        private const val MAX = 10
        private const val KEY = "recentColours"
        @Volatile private var instance: RecentColours? = null

        fun of(context: android.content.Context): RecentColours = instance ?: synchronized(this) {
            instance ?: RecentColours(context.applicationContext.getSharedPreferences("colours", android.content.Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

@Composable
private fun HexField(label: String, argb: Int, onCommit: (Int) -> Unit) {
    var text by remember(argb) { mutableStateOf(hex(argb).removePrefix("#")) }
    val parsed = parseHex(text, argb)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(8) },
        label = { Text(Tips.HEX.label) },
        prefix = { Text("#") },
        isError = parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { parsed?.let(onCommit) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** #RRGGBB when opaque, else #AARRGGBB. */
fun hex(argb: Int): String =
    if (argb ushr 24 == 0xFF) String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    else String.format(Locale.ROOT, "#%08X", argb)

/** RRGGBB keeps the alpha of [current]; AARRGGBB sets it. Null when not hex. */
fun parseHex(text: String, current: Int): Int? {
    val t = text.trim().removePrefix("#")
    if (t.isEmpty() || t.any { Character.digit(it, 16) < 0 }) return null
    return when (t.length) {
        6 -> (current and 0xFF000000.toInt()) or t.toInt(16)
        8 -> t.toLong(16).toInt()
        else -> null
    }
}

/** Hue 0 to 360, saturation and value 0 to 1. Pure Kotlin, so it is the same on the JVM. */
fun toHsv(argb: Int): FloatArray {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
}

/** The colour of [hsv] with the alpha of [alphaFrom]. */
fun fromHsv(hsv: FloatArray, alphaFrom: Int): Int {
    val (h, s, v) = hsv
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = Math.round((f + m) * 255f).coerceIn(0, 255)
    return (alphaFrom and 0xFF000000.toInt()) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
