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
import androidx.compose.material3.ListItem
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
 * the photo picker does not show, a built-in brush, or one already imported. Neither
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
    onChosen: (String) -> Unit,
) {
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    if (!open) return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Texture", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("From photos") },
                supportingContent = { Text("The system photo picker") },
                modifier = Modifier.clickable {
                    onDismiss()
                    photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            ListItem(
                headlineContent = { Text("From files") },
                supportingContent = { Text("Any image file, through the document picker") },
                modifier = Modifier.clickable {
                    onDismiss()
                    documents.launch(arrayOf("image/*"))
                },
            )
            Text("Built-in", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (b in BuiltInBrushes.ALL) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextureThumbnail(b.sha256, file, "Built-in brush ${b.name}", 64.dp, Modifier.clickable { onChosen(b.sha256) })
                        Text(b.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Text("Imported", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (hash in recent) {
                        TextureThumbnail(hash, file, "Imported texture ${hash.take(8)}", 64.dp, Modifier.clickable { onChosen(hash) })
                    }
                }
            }
        }
    }
}
