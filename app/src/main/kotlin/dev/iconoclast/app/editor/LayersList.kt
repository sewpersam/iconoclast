package dev.iconoclast.app.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.iconoclast.render.Theme
import java.io.File

/** What the layer list does to the stack. The view model supplies them. */
class StackActions(
    val open: (String) -> Unit,
    val add: () -> Unit,
    /** One undo step: row [id] into the place of row [targetId]. */
    val moveOnto: (id: String, targetId: String) -> Unit,
    /** The same during a drag; [dragDone] makes the whole drag one undo step. */
    val dragOnto: (id: String, targetId: String) -> Unit,
    val dragDone: () -> Unit,
    val setVisible: (id: String, visible: Boolean) -> Unit,
    val remove: (String) -> Unit,
)

/**
 * The editor's one list, docs/naming-spec.md 1.1: every layer, top of the stack first.
 * The icon's fill and lines are one Icon row, the background its own row, and effects are
 * rows like any layer, so their place in the list is where they draw. Drag a row by its
 * handle to reorder; tap it to open its properties page. The effects marker is not shown:
 * it only anchors the background border and the default effect slot.
 */
@Composable
fun LayersList(theme: Theme, actions: StackActions, file: (String) -> File) {
    val rows = LayerStack.rows(theme)
    val current by rememberUpdatedState(rows)
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HelpAnchor(Tip("Layers", "Everything the icons are made of. Higher layers cover lower ones. Tap a layer to change it, drag its handle to move it.")) {
                Text("Layers", style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.weight(1f)) {}
            HelpAnchor(Tips.ADD) {
                Button(
                    onClick = actions.add,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    elevation = null,
                ) {
                    Icon(EditorIcons.Add, contentDescription = null, Modifier.size(18.dp))
                    Text("Add", Modifier.padding(start = 6.dp))
                }
            }
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            for ((i, row) in rows.withIndex()) key(row.id) {
                val isDragged = dragging == row.id
                val label = LayerStack.rowLabel(theme, row)
                StackRow(
                    theme, row, label, rowSummary(theme, row),
                    onOpen = { actions.open(row.id) },
                    onVisible = { actions.setVisible(row.id, it) },
                    onRemove = if (LayerStack.canRemove(theme, row.id)) ({ actions.remove(row.id) }) else null,
                    file = file,
                    modifier = Modifier
                        .onSizeChanged { heights[row.id] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) offset else 0f
                            alpha = if (isDragged) 0.92f else 1f
                        },
                    handle = Modifier.pointerInput(row.id) {
                        fun end() {
                            dragging = null
                            offset = 0f
                            actions.dragDone()
                        }
                        detectDragGestures(
                            onDragStart = {
                                dragging = row.id
                                offset = 0f
                            },
                            onDragEnd = ::end,
                            onDragCancel = ::end,
                        ) { change, amount ->
                            change.consume()
                            offset += amount.y
                            val list = current
                            val at = list.indexOfFirst { it.id == row.id }
                            val next = list.getOrNull(if (offset > 0) at + 1 else at - 1) ?: return@detectDragGestures
                            val h = heights[next.id] ?: 0
                            if (kotlin.math.abs(offset) > h / 2) {
                                actions.dragOnto(row.id, next.id)
                                offset -= if (offset > 0) h else -h
                            }
                        }
                    },
                    onMoveUp = rows.getOrNull(i - 1)?.let { above -> { actions.moveOnto(row.id, above.id) } },
                    onMoveDown = rows.getOrNull(i + 1)?.let { below -> { actions.moveOnto(row.id, below.id) } },
                )
            }
        }
    }
}
