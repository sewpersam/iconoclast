package dev.iconoclast.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.texture.BuiltInBrushes
import java.io.File

/**
 * What the breakup picker needs from the editor: the imported textures, newest first, a
 * texture's file for its thumbnail, and the texture chooser for the streak [layerId].
 */
class BreakupActions(val recent: List<String>, val file: (String) -> File, val import: (layerId: String) -> Unit)

/**
 * A streak's breakup source, spec 2.3: noise, a built-in brush or an imported texture, by
 * thumbnail. Null [selected] is noise. A texture the theme names but the phone has not
 * listed as imported still shows, so the current choice is always visible.
 */
@Composable
fun BreakupPicker(selected: String?, actions: BreakupActions, layerId: String, onSelect: (String?) -> Unit) {
    val imported = (actions.recent + listOfNotNull(selected?.takeUnless(BuiltInBrushes::isBuiltIn))).distinct()
    Column(Modifier.fillMaxWidth()) {
        Text("Breakup source", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Choice("Noise", selected == null, { onSelect(null) }) { Swatch("fBm") }
            for (b in BuiltInBrushes.ALL) {
                Choice(b.name, selected == b.sha256, { onSelect(b.sha256) }) { TextureThumbnail(b.sha256, actions.file, null, THUMB) }
            }
            for (hash in imported) {
                Choice("Imported", selected == hash, { onSelect(hash) }) { TextureThumbnail(hash, actions.file, "Imported texture ${hash.take(8)}", THUMB) }
            }
            OutlinedButton(onClick = { actions.import(layerId) }) { Text("Import", maxLines = 1) }
        }
    }
}

@Composable
private fun Choice(name: String, isSelected: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .clip(shape)
            .border(if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(role = Role.RadioButton, onClickLabel = "Use $name", onClick = onClick)
            .semantics { selected = isSelected; contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(THUMB + 8.dp), contentAlignment = Alignment.Center) { thumb() }
        Text(name, Modifier.width(THUMB + 8.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun Swatch(text: String) {
    Box(Modifier.size(THUMB).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private val THUMB = 48.dp
