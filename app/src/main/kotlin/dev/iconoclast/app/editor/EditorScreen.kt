package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Spec 2.3's Shape and Fill are one Icon tab, everything about the line art; Layers holds
 * the effects too, since they are layers of the same stack. Three tabs fit a phone.
 */
private val TABS = listOf("Icon", "Background", "Layers")
private const val LAYERS_TAB = 2

/**
 * The theme editor, spec 2.2 screen 2: the live preview of your apps on top, the tabbed
 * controls below, undo, redo and Apply in the bar. Apply saves this
 * theme and makes it the active one, then [onApply] goes to the Apply screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onApply: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // The process may be killed any time after the activity stops: save what is pending.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            val result = snackbar.showSnackbar(it, actionLabel = if (ui.undoable) "Undo" else null, withDismissAction = ui.undoable, duration = SnackbarDuration.Long)
            vm.messageShown()
            if (result == SnackbarResult.ActionPerformed) vm.undoRemoval()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.name.ifEmpty { "Theme" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = vm::undo, enabled = ui.canUndo) { Icon(EditorIcons.Undo, contentDescription = "Undo") }
                    IconButton(onClick = vm::redo, enabled = ui.canRedo) { Icon(EditorIcons.Redo, contentDescription = "Redo") }
                    TextButton(onClick = { vm.apply(onApply) }, enabled = !ui.loading && !ui.missing) { Text("Apply") }
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
}

@Composable
private fun Editor(vm: EditorViewModel, ui: EditorUi) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val textures = TextureActions({ ui.selectedLayer?.let { vm.chooseTexture(TextureTarget.Source(it)) } }, vm::textureFile)
    val stack = StackActions(
        select = vm::selectLayer,
        addLayer = { vm.showAddLayer(true) },
        addEffect = { vm.showAddEffect(true) },
        moveOnto = vm::moveLayerOnto,
        dragOnto = vm::dragLayerOnto,
        dragDone = vm::endDrag,
        setVisible = vm::setLayerVisible,
        placeEffect = vm::placeEffect,
        remove = vm::removeLayer,
    )
    Column(Modifier.fillMaxSize()) {
        LivePreview(vm, ui)
        if (ui.importingTexture) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Importing texture" })
        PrimaryTabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { i, name ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name, maxLines = 1) })
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val theme = ui.theme
            when (tab) {
                0 -> IconTab(theme, vm::drag, vm::endDrag, vm::commit) { id -> vm.selectLayer(id); tab = LAYERS_TAB }
                1 -> BackgroundTab(theme, vm::drag, vm::endDrag, vm::commit,
                    TextureActions({ vm.chooseTexture(TextureTarget.Background) }, vm::textureFile))
                else -> LayersTab(
                    theme, ui.selectedLayer, stack, vm::drag, vm::endDrag, vm::commit, textures,
                    BreakupActions(ui.recentTextures, vm::textureFile) { vm.chooseTexture(TextureTarget.Breakup(it)) },
                )
            }
        }
    }
    if (ui.addingLayer) {
        AddLayerSheet(onDismiss = { vm.showAddLayer(false) }, onAdd = vm::addLayer, onTexture = { vm.chooseTexture(TextureTarget.NewLayer) })
    }
    if (ui.addingEffect) AddEffectSheet(onDismiss = { vm.showAddEffect(false) }, onAdd = vm::addEffect)
    ui.overriding?.let { item -> LiveOverrideSheet(vm, ui, item) }
    TextureSheet(ui.choosingTexture, ui.recentTextures, vm::textureFile, vm::dismissTextureChooser, vm::importTexture, vm::useTexture)
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
