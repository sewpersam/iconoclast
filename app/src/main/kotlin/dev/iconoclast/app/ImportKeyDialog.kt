package dev.iconoclast.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Spec 6.5: the imported backup carries a pack signing key and this phone has none.
 * Restoring it lets Apply update a pack installed before, instead of uninstalling it.
 * Dismissing is "theme only".
 */
@Composable
fun ImportKeyDialog(onAnswer: (restore: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text("Restore the signing key?") },
        text = {
            Text(
                "This backup holds a pack signing key, and this phone has none yet. Restore it if it is your own key, " +
                    "from your earlier install: Apply can then update the pack already installed instead of replacing it.",
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Restore key") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Theme only") } },
    )
}
