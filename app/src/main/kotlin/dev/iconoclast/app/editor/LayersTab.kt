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
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme

/**
 * Spec 2.3, Layers: the stack, top layer first as in Photoshop, with add, remove and
 * reorder. The selected layer's controls open below its row.
 */
@Composable
fun LayersTab(
    theme: Theme,
    selected: String?,
    onSelect: (String?) -> Unit,
    onAdd: () -> Unit,
    onMove: (id: String, up: Boolean) -> Unit,
    onRemove: (String) -> Unit,
    drag: (Theme) -> Unit,
    done: () -> Unit,
    commit: (Theme) -> Unit,
    textures: TextureActions,
) {
    val layers = theme.layers
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Top of the stack first. Each layer paints over the ones below it.", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = onAdd) { Text("Add layer") }
        for (i in layers.indices.reversed()) {
            val layer = layers[i]
            val isSelected = layer.id == selected
            LayerRow(
                layer, LayerStack.label(theme, layer), isSelected,
                canMoveUp = i < layers.lastIndex, canMoveDown = i > 0,
                onSelect = { onSelect(if (isSelected) null else layer.id) },
                onMove = { up -> onMove(layer.id, up) },
                onRemove = { onRemove(layer.id) },
            )
            if (isSelected && layer is PaintLayer) {
                Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                    LayerControls(layer, theme, drag, done, commit, textures)
                }
                HorizontalDivider()
            }
        }
    }
}
