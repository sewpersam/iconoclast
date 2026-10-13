package dev.iconoclast.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.Effect

private val OPTIONS: List<Pair<Effect, String>> = listOf(
    Effect.BrushStrokes() to "Painted strokes flicking off the icon",
    Effect.Streaks() to "Straight textured lines trailing from the icon",
    Effect.Bevel() to "Light and shade along the icon's edges",
    Effect.DropShadow() to "The icon's shadow on the background",
    Effect.InnerShadow() to "A shadow inside the icon",
    Effect.OuterGlow() to "A glow around the icon",
    Effect.InnerGlow() to "A glow from the icon's edges inward",
    Effect.Grain() to "Monochrome noise over everything",
)

/** Spec 2.3, Effects: the eight effects, each at its default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEffectSheet(onDismiss: () -> Unit, onAdd: (Effect) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Add effect", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            for ((effect, detail) in OPTIONS) {
                ListItem(
                    headlineContent = { Text(LayerStack.effectName(effect)) },
                    supportingContent = { Text(detail) },
                    modifier = Modifier.clickable { onAdd(effect) },
                )
            }
        }
    }
}
