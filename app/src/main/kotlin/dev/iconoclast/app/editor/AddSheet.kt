package dev.iconoclast.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.Effect

/**
 * The effects the sheet offers, each acting on the whole icon. Blur is not offered since
 * schema 9, where every fill has Frost; themes that have one keep it.
 */
private val EFFECTS: List<Pair<Effect, String>> = listOf(
    Effect.DropShadow() to "The icon's shadow on the background",
    Effect.OuterGlow() to "A glow around the icon",
    Effect.Bevel() to "Makes the icon look raised, with a lit side and a shaded side",
    Effect.InnerShadow() to "Shading inside the icon's edges, so it looks pressed in",
    Effect.InnerGlow() to "Light along the inside of the icon's edges",
    Effect.BrushStrokes() to "Painted strokes, out from the icon or swiped across behind it",
    Effect.Streaks() to "Lines trailing off the icon in one direction, like motion",
    Effect.Grain() to "Fine grey speckle over the whole icon, like film",
)

/**
 * The one Add sheet, phone feedback round 1: effects first under a big heading, then one
 * Overlay entry at the bottom, a layer that paints over the whole icon and picks its kind
 * (colour, gradient, image or pattern) on its own page like any fill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onEffect: (Effect) -> Unit, onOverlay: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Heading("Effects")
            for ((effect, detail) in EFFECTS) Item(LayerStack.effectName(effect), detail) { onEffect(effect) }
            Heading("Overlay")
            Item("Overlay", "Paint over the whole icon, for example a gradient wash across everything. Pick a colour, gradient, image or pattern on its page.", onOverlay)
        }
    }
}

@Composable
private fun Heading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun Item(name: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(detail) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
