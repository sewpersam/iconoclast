package dev.iconoclast.app.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer

/**
 * The theme editor, docs/naming-spec.md 1.1: the live preview pinned on top, and below it
 * one list of layers. Tapping a row opens that layer's properties page in its place, with
 * a back arrow; the preview stays, so every change shows. The theme name in the bar
 * renames; undo, redo and Apply stay in the bar. Apply saves this theme and makes it the
 * active one, then [onApply] goes to the Apply screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onApply: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    // The process may be killed any time after the activity stops: save what is pending.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            val result = snackbar.showSnackbar(it, actionLabel = if (ui.undoable) "Undo" else null, withDismissAction = ui.undoable, duration = SnackbarDuration.Long)
            vm.messageShown()
            if (result == SnackbarResult.ActionPerformed) vm.undoRemoval()
        }
    }
    BackHandler(enabled = ui.page != null) { vm.openPage(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    HelpAnchor(Tips.THEME_NAME) {
                        Text(
                            ui.name.ifEmpty { "Theme" },
                            Modifier.clickable(enabled = !ui.loading && !ui.missing, role = Role.Button, onClickLabel = "Rename theme") { renaming = true },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                // Done, in words: the only way out of the theme. Undo and redo keep their arrows.
                navigationIcon = {
                    HelpAnchor(Tips.DONE) {
                        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                            Text(Tips.DONE.label, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                },
                actions = {
                    NamedIcon(Tips.UNDO.label) {
                        IconButton(onClick = vm::undo, enabled = ui.canUndo) { Icon(EditorIcons.Undo, contentDescription = "Undo") }
                    }
                    NamedIcon(Tips.REDO.label) {
                        IconButton(onClick = vm::redo, enabled = ui.canRedo) { Icon(EditorIcons.Redo, contentDescription = "Redo") }
                    }
                    HelpAnchor(Tips.APPLY) {
                        Button(onClick = { vm.apply(onApply) }, enabled = !ui.loading && !ui.missing, modifier = Modifier.padding(end = 8.dp)) { Text("Apply") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.missing -> Text("This theme no longer exists.", Modifier.align(Alignment.Center).padding(24.dp))
                else -> Editor(vm, ui)
            }
        }
    }
    if (renaming) RenameDialog(ui.name, onDismiss = { renaming = false }) { vm.rename(it); renaming = false }
}

@Composable
private fun Editor(vm: EditorViewModel, ui: EditorUi) {
    val textures = { id: String -> TextureActions({ vm.chooseTexture(TextureTarget.Source(id)) }, vm::textureFile) { vm.openCrop(id) } }
    val stack = StackActions(
        open = vm::openPage,
        add = { vm.showAdd(true) },
        moveOnto = vm::moveLayerOnto,
        dragOnto = vm::dragLayerOnto,
        dragDone = vm::endDrag,
        setVisible = vm::setLayerVisible,
        remove = vm::removeLayer,
    )
    Column(Modifier.fillMaxSize()) {
        LivePreview(vm, ui)
        if (ui.importingTexture) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Adding image" })
        val page = ui.page
        val theme = ui.theme
        val pageLayers = LayerStack.rowLayers(theme, page)
        // A page whose layer went, by undo or delete, closes.
        LaunchedEffect(page, pageLayers.isEmpty()) { if (page != null && pageLayers.isEmpty()) vm.openPage(null) }
        if (page == null || pageLayers.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                LayersList(theme, stack, vm::textureFile)
            }
        } else {
            val first = pageLayers.first()
            val (title, info) = when {
                page == LayerStack.ICON_ROW -> "Icon" to Tips.PAGE_ICON
                page == Layer.BACKGROUND_ID -> "Background" to Tips.PAGE_BACKGROUND
                first is EffectLayer -> LayerStack.label(theme, first) to Tips.PAGE_EFFECT
                else -> LayerStack.label(theme, first) to Tips.PAGE_LAYER
            }
            Breadcrumb(title, Tip(title, info.help)) { vm.openPage(null) }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    page == LayerStack.ICON_ROW -> IconPage(theme, vm::drag, vm::endDrag, vm::commit, textures)
                    page == Layer.BACKGROUND_ID -> BackgroundPage(theme, vm::drag, vm::endDrag, vm::commit, textures(Layer.BACKGROUND_ID))
                    first is EffectLayer -> EffectPage(first, theme, vm::drag, vm::endDrag, vm::commit,
                        BreakupActions(ui.recentTextures, vm::textureFile) { vm.chooseTexture(TextureTarget.Breakup(it)) })
                    first is PaintLayer -> LayerPage(first, theme, vm::drag, vm::endDrag, vm::commit, textures(first.id))
                }
            }
        }
    }
    if (ui.adding) {
        AddSheet(onDismiss = { vm.showAdd(false) }, onEffect = vm::addEffect, onOverlay = vm::addOverlay)
    }
    ui.overriding?.let { item -> LiveOverrideSheet(vm, ui, item) }
    ui.cropping?.let { CropDialog(it, vm::texturePng, vm::closeCrop, vm::applyCrop) }
    TextureSheet(ui.choosingTexture, ui.recentTextures, vm::textureFile, vm::dismissTextureChooser, vm::importTexture, vm::importTextureLink, vm::useTexture)
}

/**
 * A properties page's header as a breadcrumb, "Layers › Icon": tap Layers to go back to
 * the list, as the system back does. No arrow, so the bar keeps one way out, Done.
 */
@Composable
private fun Breadcrumb(title: String, info: Tip, onLayers: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onLayers, modifier = Modifier.semantics { contentDescription = "Back to layers" }) {
            Text("Layers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        InfoButton(info)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename theme") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The preview panel, reading the preview images itself: a new image recomposes the panel
 * only, never the controls below it.
 */
@Composable
private fun LivePreview(vm: EditorViewModel, ui: EditorUi) {
    val frame by vm.frame.collectAsState()
    val hero by vm.hero.collectAsState()
    // Edit-to-screen latency: logged once the frame showing the new images is drawn.
    LaunchedEffect(frame) { withFrameNanos {}; vm.shown("grid", frame.theme, frame.complete) }
    LaunchedEffect(hero) { withFrameNanos {}; vm.shown("hero", hero.theme, hero.complete) }
    PreviewPanel(ui.items, frame.images, ui.focused, hero.images, ui.overridden, vm::openOverride, vm::setVisible)
}

@Composable
private fun LiveOverrideSheet(vm: EditorViewModel, ui: EditorUi, item: PreviewItem) {
    val frame by vm.frame.collectAsState()
    OverrideSheet(vm, ui, item, frame.images[item.drawable])
}
