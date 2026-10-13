package dev.iconoclast.app.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme

/** What the layer and effect lists do to the stack. The view model supplies them. */
class StackActions(
    val select: (String?) -> Unit,
    val addLayer: () -> Unit,
    val addEffect: () -> Unit,
    /** One undo step: [id] into the place of the second id. */
    val moveOnto: (id: String, targetId: String) -> Unit,
    /** The same during a drag; [dragDone] makes the whole drag one undo step. */
    val dragOnto: (id: String, targetId: String) -> Unit,
    val dragDone: () -> Unit,
    val setVisible: (id: String, visible: Boolean) -> Unit,
    val placeEffect: (id: String, onTop: Boolean) -> Unit,
    val remove: (String) -> Unit,
)

/**
 * Spec 2.3, Layers and Effects on one list: the whole stack, top first as in Photoshop.
 * Effects are layers in it, so one list shows what paints over what. Drag a row by its
 * handle to reorder, tap the eye to hide or show, tap the row to open its settings below
 * it; an effect's start with where it shows, behind the icon or on top of it. The effects
 * marker is not shown: it only anchors the background border and the default effect slot.
 */
@Composable
fun LayersTab(
    theme: Theme,
    selected: String?,
    actions: StackActions,
    drag: (Theme) -> Unit,
    done: () -> Unit,
    commit: (Theme) -> Unit,
    textures: TextureActions,
    breakup: BreakupActions,
) {
    val rows = LayerStack.rows(theme)
    val current by rememberUpdatedState(rows)
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AddButtons(actions)
        for ((i, layer) in rows.withIndex()) key(layer.id) {
            val isDragged = dragging == layer.id
            StackRow(
                layer, LayerStack.label(theme, layer), layerSummary(layer, LayerStack.aboveIcon(theme, layer)),
                selected = layer.id == selected,
                onSelect = { actions.select(if (layer.id == selected) null else layer.id) },
                onVisible = { actions.setVisible(layer.id, it) },
                onRemove = { actions.remove(layer.id) },
                file = textures.file,
                modifier = Modifier
                    .onSizeChanged { heights[layer.id] = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) offset else 0f
                        shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                    },
                handle = Modifier.pointerInput(layer.id) {
                    val gap = 6.dp.toPx()
                    fun end() {
                        dragging = null
                        offset = 0f
                        actions.dragDone()
                    }
                    detectDragGestures(
                        onDragStart = {
                            actions.select(null)
                            dragging = layer.id
                            offset = 0f
                        },
                        onDragEnd = ::end,
                        onDragCancel = ::end,
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val list = current
                        val at = list.indexOfFirst { it.id == layer.id }
                        val next = list.getOrNull(if (offset > 0) at + 1 else at - 1) ?: return@detectDragGestures
                        val h = (heights[next.id] ?: 0) + gap
                        if (kotlin.math.abs(offset) > h / 2) {
                            actions.dragOnto(layer.id, next.id)
                            offset -= if (offset > 0) h else -h
                        }
                    }
                },
                onMoveUp = rows.getOrNull(i - 1)?.let { above -> { actions.moveOnto(layer.id, above.id) } },
                onMoveDown = rows.getOrNull(i + 1)?.let { below -> { actions.moveOnto(layer.id, below.id) } },
            )
            if (layer.id == selected && dragging == null) {
                LayerPanel(layer, theme, actions, drag, done, commit, textures, breakup)
            }
        }
    }
}

@Composable
private fun AddButtons(actions: StackActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = actions.addLayer, Modifier.weight(1f)) { Text("Add layer") }
        FilledTonalButton(onClick = actions.addEffect, Modifier.weight(1f)) { Text("Add effect") }
    }
}

/** The open row's settings: a paint layer's, or an effect's with where it shows first. */
@Composable
private fun LayerPanel(
    layer: Layer,
    theme: Theme,
    actions: StackActions,
    drag: (Theme) -> Unit,
    done: () -> Unit,
    commit: (Theme) -> Unit,
    textures: TextureActions,
    breakup: BreakupActions,
) {
    Column(Modifier.padding(start = 8.dp, end = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (layer) {
            is PaintLayer -> LayerControls(layer, theme, drag, done, commit, textures)
            is EffectLayer -> {
                val onTop = LayerStack.aboveIcon(theme, layer)
                // Where a new effect of this kind goes is its default.
                val rest = LayerStack.remove(theme, layer.id)
                val placed = LayerStack.insertEffect(rest, layer)
                val defaultOnTop = LayerStack.aboveIcon(placed, layer)
                ChoiceControl("Where", listOf(false to "Behind the icon", true to "On top of the icon"), onTop, defaultOnTop,
                    { actions.placeEffect(layer.id, it) })
                if (layer.effect is Effect.Grain) {
                    // Measured by PhoneBudgetTest.applyOf300IconsGrain: 331 KB per icon over the
                    // same look without grain, amount 1 at full opacity. docs/verification/m6.md.
                    Text("Grain at full amount adds about 330 KB per icon.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EffectControls(layer, theme, drag, done, commit, breakup)
            }
            else -> Unit
        }
    }
}
