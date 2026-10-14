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
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Effect
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint

/** A source a new layer can start from. Null paint: an image, which the chooser supplies. */
private class Option(val name: String, val detail: String, val paint: Paint?)

private val PAINTS = listOf(
    Option("Colour", "One flat colour over your icons", Paint.Solid(0xFFFFFFFF.toInt())),
    Option("Gradient", "Colours blending into each other", linear(90f, listOf(ColorStop(0f, 0xFFFFFFFF.toInt()), ColorStop(1f, 0xFF000000.toInt())))),
    Option("Image", "A photo or pattern laid over your icons", null),
    Option("Clouds", "Soft random cloudy pattern", Paint.Noise(NoiseKind.FBM)),
    Option("Veins", "Random lines like marble or rock", Paint.Noise(NoiseKind.RIDGED)),
    Option("Speckle", "Fine random dots", Paint.Noise(NoiseKind.GRAIN)),
)

private val EFFECTS: List<Pair<Effect, String>> = listOf(
    Effect.BrushStrokes() to "Painted strokes flicking off the icon",
    Effect.Streaks() to "Lines trailing off the icon in one direction, like motion",
    Effect.Bevel() to "Makes the icon look raised, with a lit side and a shaded side",
    Effect.DropShadow() to "The icon's shadow on the background",
    Effect.InnerShadow() to "Shading inside the icon's edges, so it looks pressed in",
    Effect.OuterGlow() to "A glow around the icon",
    Effect.InnerGlow() to "Light along the inside of the icon's edges",
    Effect.Grain() to "Fine grey speckle over the whole icon, like film",
)

/** The one Add sheet: a colour, gradient, image or pattern layer, or an effect, each at its default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onAdd: (Paint) -> Unit, onImage: () -> Unit, onEffect: (Effect) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Add", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            Group("Colour or image")
            for (o in PAINTS) Item(o.name, o.detail) { o.paint?.let(onAdd) ?: onImage() }
            Group("Effect")
            for ((effect, detail) in EFFECTS) Item(LayerStack.effectName(effect), detail) { onEffect(effect) }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
