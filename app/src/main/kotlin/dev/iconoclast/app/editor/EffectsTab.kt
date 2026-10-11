package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Theme

/**
 * Spec 2.3, Effects: the effect layers, top of the stack first, with add, remove,
 * reorder and an on/off switch each. Moving an effect swaps it with its neighbour in the
 * whole stack, so it can pass the icon; the row says which side it is on. The selected
 * effect's controls open below its row.
 */
@Composable
fun EffectsTab(
    theme: Theme,
    selected: String?,
    onSelect: (String?) -> Unit,
    onAdd: () -> Unit,
    onMove: (id: String, up: Boolean) -> Unit,
    onRemove: (String) -> Unit,
    drag: (Theme) -> Unit,
    done: () -> Unit,
    commit: (Theme) -> Unit,
    breakup: BreakupActions,
) {
    val layers = theme.layers
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Top of the stack first. Streaks, shadows and outer glows show below the icon; bevel, inner effects and grain on it.",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = onAdd) { Text("Add effect") }
        if (layers.none { it is EffectLayer }) Text("No effects yet.", style = MaterialTheme.typography.bodyMedium)
        for (i in layers.indices.reversed()) {
            val layer = layers[i] as? EffectLayer ?: continue
            val isSelected = layer.id == selected
            EffectRow(
                layer, LayerStack.label(theme, layer), LayerStack.aboveIcon(theme, layer), isSelected,
                canMoveUp = i < layers.lastIndex, canMoveDown = i > 0,
                onSelect = { onSelect(if (isSelected) null else layer.id) },
                onEnabled = { commit(LayerStack.replace(theme, layer.copy(enabled = it))) },
                onMove = { up -> onMove(layer.id, up) },
                onRemove = { onRemove(layer.id) },
            )
            if (isSelected) {
                Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                    EffectControls(layer, theme, drag, done, commit, breakup)
                }
                HorizontalDivider()
            }
        }
    }
}
