package dev.iconoclast.app.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.texture.BuiltInBrushes
import java.io.File

/**
 * Picks a texture, spec 2.4: the system photo picker, or the document picker for files
 * the photo picker does not show, an image link, a built-in brush, or one already imported. Neither
 * picker needs a storage permission. Always composed, so a picker's result arrives after
 * the sheet has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextureSheet(
    open: Boolean,
    recent: List<String>,
    file: (String) -> File,
    onDismiss: () -> Unit,
    onPicked: (Uri) -> Unit,
    onLink: (String) -> Unit,
    onChosen: (String) -> Unit,
) {
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    var linking by rememberSaveable { mutableStateOf(false) }
    if (linking) LinkDialog(onDismiss = { linking = false }) { linking = false; onLink(it) }
    if (!open) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Choose an image", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("From photos") },
                supportingContent = { Text("Pick from your gallery") },
                modifier = Modifier.clickable {
                    onDismiss()
                    photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            ListItem(
                headlineContent = { Text("From files") },
                supportingContent = { Text("Pick any image file on your phone") },
                modifier = Modifier.clickable {
                    onDismiss()
                    documents.launch(arrayOf("image/*"))
                },
            )
            ListItem(
                headlineContent = { Text("Paste image link") },
                supportingContent = { Text("Download an image from the web") },
                modifier = Modifier.clickable {
                    onDismiss()
                    linking = true
                },
            )
            Text("Included patterns", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (b in BuiltInBrushes.ALL) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextureThumbnail(b.sha256, file, "Included pattern ${b.name}", 64.dp, Modifier.clickable { onChosen(b.sha256) })
                        Text(b.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Text("Your images", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (hash in recent) {
                        TextureThumbnail(hash, file, "Your image", 64.dp, Modifier.clickable { onChosen(hash) })
                    }
                }
            }
        }
    }
}

/** Asks for an image's web address, filled from the clipboard when it holds one. */
@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val clipboard = LocalClipboard.current
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) return@LaunchedEffect
        val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        if (clip != null && (clip.startsWith("http://") || clip.startsWith("https://"))) text = clip
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste image link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Image link") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) }),
                )
                Text("The link of the image itself. In a browser, long-press the image and copy its link.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
