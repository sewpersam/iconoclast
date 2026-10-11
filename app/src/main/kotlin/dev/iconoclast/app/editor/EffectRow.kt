package dev.iconoclast.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer

/**
 * One row of the effects list: name and summary, tap to select, an on/off switch, and
 * move up, move down and remove. Grain's row says what it costs.
 */
@Composable
fun EffectRow(
    layer: EffectLayer,
    label: String,
    aboveIcon: Boolean,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onMove: (up: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f)
                    .clickable(role = Role.Button, onClickLabel = if (selected) "Close $label" else "Edit $label", onClick = onSelect)
                    .semantics { this.selected = selected }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                val where = if (aboveIcon) "on the icon" else "below the icon"
                Text("${effectSummary(layer)}, $where", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (layer.effect is Effect.Grain) {
                    Text("Grain increases pack size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(layer.enabled, onEnabled, Modifier.semantics { contentDescription = label })
            Glyph("↑", "Move $label up", canMoveUp) { onMove(true) }
            Glyph("↓", "Move $label down", canMoveDown) { onMove(false) }
            Glyph("✕", "Remove $label", true, onRemove)
        }
    }
}

/** On or off, blend and opacity: "Off, Screen, 35%". The bevel's blends are its own. */
fun effectSummary(layer: EffectLayer): String = buildString {
    if (!layer.enabled) append("Off, ")
    if (layer.effect !is Effect.Bevel) append(BLEND_NAMES.getValue(layer.blend)).append(", ")
    append(Math.round(layer.opacity * 100)).append('%')
}
