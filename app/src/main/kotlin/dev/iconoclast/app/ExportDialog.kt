package dev.iconoclast.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Theme export, spec 2.4 and 6.5. "Include signing key" is off by default and turns on only
 * through [KeyWarning]: the key never leaves the phone without a tap on that warning.
 */
@Composable
fun ExportDialog(name: String, onDismiss: () -> Unit, onExport: (includeKey: Boolean) -> Unit) {
    var includeKey by rememberSaveable { mutableStateOf(false) }
    var warning by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share $name") },
        text = {
            Column {
                Text("Saves the theme and its images as a .zip file.")
                Row(
                    Modifier.toggleable(includeKey, role = Role.Checkbox) { on -> if (on) warning = true else includeKey = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeKey, onCheckedChange = null)
                    Text("Include my pack key (for moving phones)", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Only for moving to a new phone or reinstalling. Without it, a reinstalled Iconoclast cannot update your current pack.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onExport(includeKey) }) { Text("Share") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (warning) KeyWarning(onAccept = { includeKey = true; warning = false }, onCancel = { warning = false })
}

/** The explicit warning before the signing key goes into an export, spec 6.5. */
@Composable
private fun KeyWarning(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Keep this file private") },
        text = {
            Text(
                "Anyone who has this file can make a pack that installs over yours and changes your icons. " +
                    "Keep it private: store it where only you can read it, and never post or share it. " +
                    "The key is not encrypted inside the file.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Include the key") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
