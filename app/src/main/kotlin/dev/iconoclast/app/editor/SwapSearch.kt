package dev.iconoclast.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Swap the drawable, spec 2.2 screen 3: a search over every Arcticons drawable name,
 * results live as the query changes, prefix matches first. Tapping a name picks it; the
 * sheet's preview then shows the icon drawn with it. [current] is the swap in force.
 */
@Composable
fun SwapSearch(current: String?, search: suspend (String) -> List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(query) {
        delay(TYPING_PAUSE_MS)
        results = search(query)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (current == null) "Drawing: this app's own" else "Drawing: ${current.replace('_', ' ')}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current != null) HelpAnchor(Tips.USE_OWN_DRAWING) { TextButton(onClick = onClear) { Text(Tips.USE_OWN_DRAWING.label) } }
        }
        OutlinedTextField(
            query, { query = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(Tips.DIFFERENT_DRAWING.label) },
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text("No drawing with that name.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        for (name in results) {
            val label = name.replace('_', ' ')
            Text(
                if (name == current) "$label  (in use)" else label,
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Use $label") { onPick(name) }
                    .semantics { contentDescription = label }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private const val TYPING_PAUSE_MS = 120L
