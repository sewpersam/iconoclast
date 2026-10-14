package dev.iconoclast.app.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.ui.theme.ValueStyle
import dev.iconoclast.render.BlendMode
import java.util.Locale

/**
 * A control's label row: the name, an optional current value, and a reset that shows only
 * when the value differs from its default. Long-press opens the [tip]'s help.
 */
@Composable
fun LabelRow(tip: Tip, value: String? = null, canReset: Boolean = false, onReset: () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The anchor is the words only: a switch or button beside them keeps its own press.
        // TooltipBox does not pass a Row weight through, so the weight sits on a box around it.
        Box(Modifier.weight(1f)) {
            HelpAnchor(tip) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tip.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (value != null) Text(value, Modifier.padding(start = 8.dp), style = ValueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing()
        ResetButton(tip.label, canReset, onReset)
    }
}

/**
 * A labelled slider, spec 2.3: the value beside the label and a reset to [default].
 * [onDrag] runs on every move, [onDone] on release, so a drag is one undo step.
 */
@Composable
fun SliderControl(
    tip: Tip,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    format: (Float) -> String,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    val text = format(value)
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip, text, value != default, onReset)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onDrag,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = tip.label
                stateDescription = text
            },
        )
    }
}

/** The per-control reset, there only when there is something to reset. */
@Composable
fun ResetButton(label: String, enabled: Boolean, onReset: () -> Unit) {
    IconButton(
        onClick = onReset,
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0f).semantics { contentDescription = "Reset $label" },
    ) { Icon(EditorIcons.Reset, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * One of several options as pills that wrap to the next line: the picked one solid, the
 * rest a quiet grey. [enabled] false greys an option out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceControl(
    tip: Tip,
    options: List<Pair<T, String>>,
    selected: T,
    default: T,
    onSelect: (T) -> Unit,
    enabled: (T) -> Boolean = { true },
) {
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip, canReset = selected != default, onReset = { onSelect(default) })
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, name) in options) Pill(name, value == selected, enabled(value)) { onSelect(value) }
        }
    }
}

@Composable
fun Pill(name: String, picked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .background(if (picked) c.onSurface else c.surfaceVariant, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = picked }
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(name, style = MaterialTheme.typography.labelLarge, color = if (picked) c.surface else c.onSurface) }
}

/** An on/off switch, named for its on state, with a reset to [default]. [enabled] false greys it out. */
@Composable
fun SwitchControl(tip: Tip, checked: Boolean, default: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    LabelRow(tip, canReset = enabled && checked != default, onReset = { onChange(default) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent, disabledUncheckedBorderColor = Color.Transparent),
            modifier = Modifier.semantics { contentDescription = tip.label },
        )
    }
}

/** A random arrangement: a Shuffle button, and a reset back to the original arrangement. */
@Composable
fun ShuffleControl(tip: Tip, canReset: Boolean, onShuffle: () -> Unit, onReset: () -> Unit) {
    LabelRow(tip, canReset = canReset, onReset = onReset) {
        TextButton(onClick = onShuffle) { Text("Shuffle") }
    }
}

/** A section title on a properties page. */
@Composable
fun Section(title: String) {
    Text(title, Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
}

/** Whether a page's More options is open; [key] names the page, so each keeps its own. */
@Composable
fun rememberMore(key: String): MutableState<Boolean> = rememberSaveable(key) { mutableStateOf(false) }

/**
 * The rare settings of a page, behind one row at its bottom. Closed by default. The page
 * holds [open], so controls above can show more while it is open too.
 */
@Composable
fun MoreOptions(open: MutableState<Boolean>, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .clickable(role = Role.Button, onClickLabel = if (open.value) "Hide more options" else "Show more options") { open.value = !open.value }
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("More options", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Icon(EditorIcons.Chevron, contentDescription = null, Modifier.rotate(if (open.value) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(open.value) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
}

/** A mix mode picker: the standard names, each with its plain second line. */
@Composable
fun BlendControl(tip: Tip, blend: BlendMode, default: BlendMode, onSelect: (BlendMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LabelRow(tip, canReset = blend != default, onReset = { onSelect(default) }) {
        Box {
            TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "${tip.label} ${BLEND_NAMES.getValue(blend)}" }) {
                Text(BLEND_NAMES.getValue(blend))
                Icon(EditorIcons.Chevron, contentDescription = null, Modifier.padding(start = 4.dp).size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((mode, name) in BLEND_NAMES) DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(BLEND_LINES.getValue(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { open = false; onSelect(mode) },
                    trailingIcon = if (mode == blend) ({ Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) }) else null,
                )
            }
        }
    }
}

/** A short dropdown choice: the label left, the picked option as a button right. */
@Composable
fun <T> DropdownControl(tip: Tip, options: List<Pair<T, String>>, selected: T, default: T, onSelect: (T) -> Unit, name: (T) -> String = { v -> options.first { it.first == v }.second }) {
    var open by remember { mutableStateOf(false) }
    LabelRow(tip, canReset = selected != default, onReset = { onSelect(default) }) {
        Box {
            TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "${tip.label} ${name(selected)}" }) {
                Text(name(selected))
                Icon(EditorIcons.Chevron, contentDescription = null, Modifier.padding(start = 4.dp).size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((v, n) in options) DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onSelect(v) })
            }
        }
    }
}

/** Space between groups on a page, where a divider would otherwise go. */
@Composable
fun Gap() = Spacer(Modifier.size(8.dp))

fun decimals(n: Int, suffix: String = ""): (Float) -> String = { String.format(Locale.ROOT, "%.${n}f", it) + suffix }

/** A fraction shown as a whole percentage. */
val percent: (Float) -> String = { String.format(Locale.ROOT, "%.0f%%", it * 100) }

/** A signed fraction as a percentage: +3%, -10%, 0%. */
val signedPercent: (Float) -> String = { val p = Math.round(it * 100); if (p > 0) "+$p%" else "$p%" }

/** A multiple of the original: line thickness, gap and gap closing in 48-grid units, where 1 is Arcticons' own line. */
val times: (Float) -> String = {
    val s = String.format(Locale.ROOT, "%.2f", it)
    (if (s.endsWith("0")) s.dropLast(1) else s) + "×"
}
