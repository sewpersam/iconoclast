package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * A colour as hue, saturation and value sliders plus a hex field, no picker library.
 * [argb] is straight 0xAARRGGBB. The sliders keep the alpha; the hex field takes
 * RRGGBB, which keeps it too, or AARRGGBB. Collapsed it is a swatch and the hex value;
 * a tap opens the sliders. With [swatches] a row of one-tap colours shows even collapsed.
 */
@Composable
fun ColorControl(
    label: String,
    argb: Int,
    default: Int,
    onDrag: (Int) -> Unit,
    onDone: () -> Unit,
    onCommit: (Int) -> Unit,
    swatches: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Hue and saturation survive a trip through grey or black, where the colour forgets them.
    var hsv by remember { mutableStateOf(toHsv(argb)) }
    LaunchedEffect(argb) { if (fromHsv(hsv, argb) != argb) hsv = toHsv(argb) }
    val hex = hex(argb)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "Close $label" else "Edit $label") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box(
                Modifier.size(28.dp)
                    .background(Color(argb), RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "$label colour $hex" },
            )
            Text(hex, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            ResetButton(label, enabled = argb != default) { onCommit(default) }
        }
        if (swatches || open) Swatches(label, argb, onCommit)
        if (open) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            fun set(h: FloatArray) {
                hsv = h
                onDrag(fromHsv(h, argb))
            }
            val ref = toHsv(default)
            SliderControl("$label hue", hsv[0], 0f..360f, ref[0], decimals(0, "°"),
                { set(floatArrayOf(it, hsv[1], hsv[2])) }, onDone, { set(floatArrayOf(ref[0], hsv[1], hsv[2])); onDone() })
            SliderControl("$label saturation", hsv[1], 0f..1f, ref[1], percent,
                { set(floatArrayOf(hsv[0], it, hsv[2])) }, onDone, { set(floatArrayOf(hsv[0], ref[1], hsv[2])); onDone() })
            SliderControl("$label value", hsv[2], 0f..1f, ref[2], percent,
                { set(floatArrayOf(hsv[0], hsv[1], it)) }, onDone, { set(floatArrayOf(hsv[0], hsv[1], ref[2])); onDone() })
            HexField(label, argb, onCommit)
        }
    }
}

/** One tap colours, the common picks; the sliders and hex field remain for the rest. */
@Composable
private fun Swatches(label: String, argb: Int, onCommit: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((c, name) in SWATCHES) {
            // The picked swatch keeps the alpha the colour has.
            val value = (argb and 0xFF000000.toInt()) or (c and 0xFFFFFF)
            val picked = value == argb
            Box(
                Modifier.size(36.dp)
                    .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(if (picked) 4.dp else 0.dp)
                    .background(Color(c), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "$label $name") { onCommit(value) }
                    .semantics { contentDescription = "$label $name"; selected = picked },
            )
        }
    }
}

private val SWATCHES: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "white", 0xFFBDC1C6.toInt() to "light grey", 0xFF5F6368.toInt() to "grey",
    0xFF202124.toInt() to "charcoal", 0xFF000000.toInt() to "black", 0xFFE57373.toInt() to "red",
    0xFFFFB74D.toInt() to "orange", 0xFFFFF176.toInt() to "yellow", 0xFF81C784.toInt() to "green",
    0xFF3E8E73.toInt() to "jade", 0xFF4DD0E1.toInt() to "teal", 0xFF64B5F6.toInt() to "blue",
    0xFF9575CD.toInt() to "purple", 0xFFF06292.toInt() to "pink",
)

@Composable
private fun HexField(label: String, argb: Int, onCommit: (Int) -> Unit) {
    var text by remember(argb) { mutableStateOf(hex(argb).removePrefix("#")) }
    val parsed = parseHex(text, argb)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(8) },
        label = { Text("$label hex") },
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
