package dev.iconoclast.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer

/**
 * One row of the layer list: name and summary, tap to select, and move up, move down and
 * remove buttons. The app ships no icon library, so the buttons are glyphs with labels.
 */
@Composable
fun LayerRow(
    layer: Layer,
    label: String,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(summary(layer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Glyph("↑", "Move $label up", canMoveUp) { onMove(true) }
            Glyph("↓", "Move $label down", canMoveDown) { onMove(false) }
            Glyph("✕", "Remove $label", layer !is EffectsLayer, onRemove)
        }
    }
}

@Composable
private fun Glyph(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

/** Blend, opacity and clip at a glance: "Multiply, 80%, clip Icon". */
private fun summary(layer: Layer): String = when (layer) {
    is EffectsLayer -> "Border; effects arrive later"
    is PaintLayer -> buildString {
        append(BLEND_NAMES.getValue(layer.blend))
        append(", ").append(Math.round(layer.opacity * 100)).append('%')
        append(", clip ")
        if (layer.clipInverse) append("outside ")
        append(CLIP_NAMES.getValue(layer.clip))
        if (layer.matte != null) append(", masked")
    }
}
