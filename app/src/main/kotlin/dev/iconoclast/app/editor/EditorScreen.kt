package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * The theme editor, spec 2.2 screen 2: the live preview on top, the tabbed controls
 * below, undo, redo and Apply in the bar, and a press-and-hold compare. Apply saves this
 * theme and makes it the active one, then [onApply] goes to the Apply screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onApply: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val frame by vm.frame.collectAsState()
    val hero by vm.hero.collectAsState()
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
                else -> Editor(vm, ui, frame.images, hero.images)
            }
        }
    }
}

@Composable
private fun Editor(vm: EditorViewModel, ui: EditorUi, images: Map<Int, ImageBitmap>, heroImages: Map<Int, ImageBitmap>) {
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
        PreviewPanel(ui.items, images, ui.focused, heroImages, ui.overridden, vm::openOverride, vm::setVisible)
        PreviewBar(vm, ui)
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
    ui.overriding?.let { item -> OverrideSheet(vm, ui, item, images[item.drawable]) }
    TextureSheet(ui.choosingTexture, ui.recentTextures, vm::textureFile, vm::dismissTextureChooser, vm::importTexture, vm::useTexture)
}

/** Your apps or the sample set, and compare: plain Arcticons while held. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewBar(vm: EditorViewModel, ui: EditorUi) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sources = listOf(PreviewSource.INSTALLED to "Your apps", PreviewSource.SAMPLES to "Samples")
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            sources.forEachIndexed { i, (s, name) ->
                SegmentedButton(
                    selected = ui.source == s,
                    onClick = { vm.setSource(s) },
                    shape = SegmentedButtonDefaults.itemShape(i, sources.size),
                    icon = {},
                ) { Text(name, maxLines = 1) }
            }
        }
        CompareButton(ui.comparing, vm::compare)
    }
}

/**
 * Touch: plain Arcticons while pressed. Accessibility services cannot hold a press, so
 * their click and long-click actions toggle it instead, and the state is announced.
 */
@Composable
private fun CompareButton(comparing: Boolean, compare: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(if (comparing) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .pointerInput(compare) {
                detectTapGestures(onPress = {
                    compare(true)
                    tryAwaitRelease()
                    compare(false)
                })
            }
            .semantics {
                role = Role.Button
                contentDescription = "Compare with plain Arcticons"
                stateDescription = if (comparing) "Showing plain Arcticons" else "Showing this theme"
                onClick(label = if (comparing) "Show this theme" else "Show plain Arcticons") { compare(!comparing); true }
                onLongClick(label = if (comparing) "Show this theme" else "Show plain Arcticons") { compare(!comparing); true }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (comparing) "Plain" else "Hold to compare", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}
