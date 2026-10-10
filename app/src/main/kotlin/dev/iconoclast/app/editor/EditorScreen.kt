package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val TABS = listOf("Shape", "Fill", "Background", "Layers", "Effects")

/** Layers and Effects are M5 and M6; their tabs hold their place, disabled. */
private const val ENABLED_TABS = 3

/**
 * The theme editor, spec 2.2 screen 2: the live preview on top, the tabbed controls
 * below, undo and redo in the bar, and a press-and-hold compare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val frame by vm.frame.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // The process may be killed any time after the activity stops: save what is pending.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.name.ifEmpty { "Theme" }, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = vm::undo, enabled = ui.canUndo) { Text("Undo") }
                    TextButton(onClick = vm::redo, enabled = ui.canRedo) { Text("Redo") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.missing -> Text("This theme no longer exists.", Modifier.align(Alignment.Center).padding(24.dp))
                else -> Editor(vm, ui, frame.images)
            }
        }
    }
}

@Composable
private fun Editor(vm: EditorViewModel, ui: EditorUi, images: Map<Int, androidx.compose.ui.graphics.ImageBitmap>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PreviewGrid(ui.items, images, vm::setVisible, Modifier.fillMaxWidth().height(232.dp))
        PreviewBar(vm, ui)
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            TABS.forEachIndexed { i, name ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    enabled = i < ENABLED_TABS,
                    text = { Text(if (i < ENABLED_TABS) name else "$name (later)") },
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val theme = ui.theme
            when (tab) {
                0 -> ShapeTab(theme, vm::drag, vm::endDrag, vm::commit)
                1 -> FillTab(theme, vm::drag, vm::endDrag, vm::commit)
                2 -> BackgroundTab(theme, vm::drag, vm::endDrag, vm::commit)
            }
        }
    }
}

/** The installed or sample toggle, and compare: plain Arcticons while held. */
@Composable
private fun PreviewBar(vm: EditorViewModel, ui: EditorUi) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(ui.source == PreviewSource.INSTALLED, { vm.setSource(PreviewSource.INSTALLED) }, { Text("Installed apps") })
        FilterChip(ui.source == PreviewSource.SAMPLES, { vm.setSource(PreviewSource.SAMPLES) }, { Text("Samples") })
        Box(Modifier.weight(1f))
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
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (comparing) "Plain" else "Compare", style = MaterialTheme.typography.labelLarge) }
}
