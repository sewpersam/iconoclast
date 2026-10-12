package dev.iconoclast.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.pack.KeyState

/** Mid grey: shows white line art and black backgrounds alike. */
private val PreviewBackdrop = Color(0xFF3C4043)

/** The themes home, spec 2.2 screen 1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: HomeViewModel, onOpen: (String) -> Unit, onApply: () -> Unit, onCoverage: () -> Unit, onSettings: () -> Unit) {
    val themes by model.themes.collectAsState()
    val activeId by model.activeId.collectAsState()
    val message by model.message.collectAsState()
    val unreadable by model.unreadable.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<ThemeDocument?>(null) }
    var deleting by remember { mutableStateOf<ThemeDocument?>(null) }
    var exporting by remember { mutableStateOf<ThemeDocument?>(null) }
    var overflow by remember { mutableStateOf(false) }
    val newApps by model.newSinceApply.collectAsState()
    val keyPrompt by model.keyPrompt.collectAsState()
    // Spec 2.5: diff installed apps against the last Apply whenever the app is shown.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshCoverage() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::import) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Themes") },
                actions = {
                    TextButton(
                        onClick = onCoverage,
                        modifier = Modifier.semantics {
                            if (newApps > 0) contentDescription = "Coverage, $newApps new since the last Apply"
                        },
                    ) {
                        Text("Coverage")
                        if (newApps > 0) Badge(Modifier.padding(start = 6.dp)) { Text("$newApps") }
                    }
                    TextButton(onClick = onApply) { Text("Apply") }
                    Box {
                        IconButton(onClick = { overflow = true }, modifier = Modifier.semantics { contentDescription = "More" }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(text = { Text("Import theme") }, onClick = { overflow = false; pick.launch(ThemeTransfer.IMPORT_TYPES) })
                            DropdownMenuItem(text = { Text("Settings and about") }, onClick = { overflow = false; onSettings() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { model.create() }) { Text("New theme") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (unreadable.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val n = unreadable.size
                    Text(
                        if (n == 1) "1 theme could not be read" else "$n themes could not be read",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(themes, key = { it.id }) { doc ->
                ThemeCard(
                    doc = doc,
                    active = doc.id == activeId,
                    model = model,
                    onOpen = { onOpen(doc.id) },
                    onRename = { renaming = doc },
                    onDelete = { deleting = doc },
                    onExport = { exporting = doc },
                )
            }
        }
    }

    exporting?.let { doc ->
        ExportDialog(doc.name, onDismiss = { exporting = null }) { includeKey -> model.export(doc.id, includeKey); exporting = null }
    }
    keyPrompt?.let { ImportKeyDialog(damaged = it == KeyState.DAMAGED, onAnswer = model::answerKeyPrompt) }
    renaming?.let { doc -> RenameDialog(doc, onDismiss = { renaming = null }) { model.rename(doc.id, it); renaming = null } }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${doc.name}?") },
            text = { Text("This theme is removed from the phone. An installed pack keeps working.") },
            confirmButton = { TextButton(onClick = { model.delete(doc.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeCard(
    doc: ThemeDocument,
    active: Boolean,
    model: HomeViewModel,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        border = if (active) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(),
    ) {
        PreviewGrid(doc, model)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More actions for ${doc.name}" }) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!active) DropdownMenuItem(text = { Text("Set active") }, onClick = { menu = false; model.setActive(doc.id) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; model.duplicate(doc.id) })
                    DropdownMenuItem(text = { Text("Export") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** Three rows of three live previews, rendered off the main thread. */
@Composable
private fun PreviewGrid(doc: ThemeDocument, model: HomeViewModel) {
    val written by model.texturesWritten.collectAsState()
    val resolution by model.resolution.collectAsState()
    val icons by produceState<List<PreviewIcon>?>(null, doc.theme, doc.overrides, written, resolution) { value = model.previews(doc) }
    Column(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)
            .background(PreviewBackdrop, RoundedCornerShape(12.dp)).padding(6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        icons?.chunked(3)?.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { icon ->
                    Image(icon.image, contentDescription = "${icon.label} in ${doc.name}", modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(doc: ThemeDocument, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(doc.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename theme") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
