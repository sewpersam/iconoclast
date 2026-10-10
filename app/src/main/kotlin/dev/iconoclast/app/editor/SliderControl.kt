package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.util.Locale

/**
 * A labelled slider, spec 2.3: the numeric value beside the label and a reset to
 * [default]. [onDrag] runs on every move, [onDone] on release, so a drag is one undo
 * step. TalkBack reads the label and the formatted value.
 */
@Composable
fun SliderControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    format: (Float) -> String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = format(value)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(text, style = MaterialTheme.typography.labelLarge)
            ResetButton(label, enabled = value != default, onReset)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onDrag,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = text
            },
        )
    }
}

/** The per-control reset. The app ships no icon library, so the icon is a glyph. */
@Composable
fun ResetButton(label: String, enabled: Boolean, onReset: () -> Unit) {
    IconButton(
        onClick = onReset,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = "Reset $label" },
    ) { Text("↺", style = MaterialTheme.typography.titleLarge) }
}

fun decimals(n: Int, suffix: String = ""): (Float) -> String = { String.format(Locale.ROOT, "%.${n}f", it) + suffix }

/** A fraction shown as a whole percentage. */
val percent: (Float) -> String = { String.format(Locale.ROOT, "%.0f%%", it * 100) }

/** A signed fraction as a percentage: +3%, -10%, 0%. */
val signedPercent: (Float) -> String = { val p = Math.round(it * 100); if (p > 0) "+$p%" else "$p%" }
