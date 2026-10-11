package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint

/** A source a new layer can start from. Null paint: a texture, which the chooser supplies. */
private class Option(val name: String, val detail: String, val paint: Paint?)

private val OPTIONS = listOf(
    Option("Solid colour", "One colour", Paint.Solid(0xFFFFFFFF.toInt())),
    Option("Linear gradient", "Two colours along a line", linear(90f, listOf(ColorStop(0f, 0xFFFFFFFF.toInt()), ColorStop(1f, 0xFF000000.toInt())))),
    Option("Radial gradient", "Two colours from a centre",
        Paint.RadialGradient(0.5f, 0.5f, 0.75f, listOf(ColorStop(0f, 0xFFFFFFFF.toInt()), ColorStop(1f, 0xFF000000.toInt())))),
    Option("Texture", "An image from your photos or files", null),
    Option("Noise: fBm", "Soft clouds", Paint.Noise(NoiseKind.FBM)),
    Option("Noise: ridged", "Veins and ridges", Paint.Noise(NoiseKind.RIDGED)),
    Option("Noise: grain", "Fine grain", Paint.Noise(NoiseKind.GRAIN)),
)

/** Spec 2.3: a new layer's source. It goes above the selected layer, or on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLayerSheet(onDismiss: () -> Unit, onAdd: (Paint) -> Unit, onTexture: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Add layer", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            for (o in OPTIONS) {
                ListItem(
                    headlineContent = { Text(o.name) },
                    supportingContent = { Text(o.detail) },
                    modifier = Modifier.clickable { o.paint?.let(onAdd) ?: onTexture() },
                )
            }
        }
    }
}
