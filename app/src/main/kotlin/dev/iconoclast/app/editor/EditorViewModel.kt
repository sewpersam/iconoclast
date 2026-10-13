package dev.iconoclast.app.editor

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.iconoclast.app.InstalledApps
import dev.iconoclast.app.container
import dev.iconoclast.app.toImageBitmap
import dev.iconoclast.app.texture.TextureImport
import dev.iconoclast.app.texture.TextureImportException
import dev.iconoclast.app.texture.TexturePresence
import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.applyTo
import dev.iconoclast.render.TextureOffset
import kotlinx.coroutines.CoroutineStart
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.CoverageMatcher
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.SampleIcons
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class PreviewSource { INSTALLED, SAMPLES }

/** Where a chosen texture goes. */
sealed interface TextureTarget {
    /** A new layer above the selected one. */
    data object NewLayer : TextureTarget
    /** The source of an existing layer. */
    data class Source(val layerId: String) : TextureTarget
    /** The background tab's texture layer, made if missing. */
    data object Background : TextureTarget
    /** The breakup of the streaks effect [layerId]. */
    data class Breakup(val layerId: String) : TextureTarget
}

/** One preview cell. [label] is its content description. */
data class PreviewItem(val drawable: Int, val label: String)

data class EditorUi(
    val loading: Boolean = true,
    /** The theme was deleted or never existed. */
    val missing: Boolean = false,
    val name: String = "",
    val theme: Theme = Theme(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val comparing: Boolean = false,
    val source: PreviewSource = PreviewSource.INSTALLED,
    val items: List<PreviewItem> = emptyList(),
    val message: String? = null,
    /** The snackbar offers Undo for [message]. */
    val undoable: Boolean = false,
    /** The layer whose controls are open in the Layers tab. */
    val selectedLayer: String? = null,
    val addingLayer: Boolean = false,
    val addingEffect: Boolean = false,
    /** The texture chooser is open; [recentTextures] are the imported ones, newest first. */
    val choosingTexture: Boolean = false,
    val recentTextures: List<String> = emptyList(),
    val importingTexture: Boolean = false,
    /** Drawables with an override in this theme: the preview badges them. */
    val overridden: Set<Int> = emptySet(),
    /** The drawable the big preview shows: the last icon tapped, else the first. */
    val focused: Int? = null,
    /** The icon whose override sheet is open, and its override as edited. */
    val overriding: PreviewItem? = null,
    val override: IconOverride = IconOverride.NONE,
)

/**
 * The theme editor, spec 2.2 screen 2. Holds the working theme and its history, drives
 * the preview, and autosaves every committed change through the repository, debounced.
 * Controls call [drag] while a slider moves and [endDrag] on release; everything else
 * calls [commit].
 */
@OptIn(FlowPreview::class)
class EditorViewModel(app: Application, private val saved: SavedStateHandle, private val id: String) : AndroidViewModel(app) {
    private val container = app.container
    private val repo = container.themes

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui

    private var history: EditorHistory? = null
    private var source: IconSource? = null
    private var plain: Theme? = null
    private var installed: List<PreviewItem> = emptyList()
    private var samples: List<PreviewItem> = emptyList()
    private var visible: Set<Int> = emptySet()
    private var reportedAbsent: Set<String> = emptySet()
    /** History depth and theme right after the last layer removal, for its snackbar Undo. */
    private var removal: EditorHistory.Mark? = null
    private var textureTarget: TextureTarget = TextureTarget.NewLayer
    /** The overrides as shown; each change is written through the repository at once. */
    private var overrides: Map<String, IconOverride> = emptyMap()

    private val renderThreads = Dispatchers.Default.limitedParallelism(RENDER_THREADS)

    private val previews = PreviewRenderer(
        viewModelScope,
        renderThreads,
        SIZE,
        render = { d, look -> draw(d, look, SIZE) },
        log = { Log.i(TAG, it) },
    )

    /** The big preview: one icon at [HERO_SIZE], its own single-flight pass beside the grid's. */
    private val heroPreviews = PreviewRenderer(viewModelScope, renderThreads, HERO_SIZE, render = { d, look -> draw(d, look, HERO_SIZE) }, cacheEntries = 32)

    /** The preview images by drawable index. */
    val frame: StateFlow<PreviewFrame<ImageBitmap>> = previews.frame

    /** The big preview's image, by drawable index. */
    val hero: StateFlow<PreviewFrame<ImageBitmap>> = heroPreviews.frame

    /** The newest theme not yet saved. */
    private val pending = MutableStateFlow<Theme?>(null)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { pending.filterNotNull().debounce(SAVE_DELAY_MS).collect { save(it) } }
        // The resolution setting sets the previews' grain cells.
        viewModelScope.launch { container.settings.resolution.collect { refresh() } }
    }

    private suspend fun load() {
        try {
            repo.load()
        } catch (e: IOException) {
            Log.w(TAG, "Themes could not be loaded", e)
        }
        val doc = repo.get(id)
        if (doc == null) {
            _ui.value = EditorUi(loading = false, missing = true)
            return
        }
        val app = getApplication<Application>()
        val icons = container.iconSource()
        source = icons
        plain = withContext(Dispatchers.IO) { migrate(Presets.read(app.assets).first()).theme }
        samples = SampleIcons.NAMES.mapNotNull { name -> icons.indexOf(name).takeIf { it >= 0 }?.let { PreviewItem(it, name.replace('_', ' ')) } }
        installed = withContext(Dispatchers.IO) { installedItems(app, icons) }
        withContext(Dispatchers.IO) { TexturePresence.ensure(container.textures, doc.theme) }
        history = EditorHistory(doc.theme)
        overrides = doc.overrides
        // The toggle survives process death; the first visit shows installed apps when there are any.
        val start = saved.get<String>(SOURCE_KEY)?.let(PreviewSource::valueOf)
            ?: if (installed.isEmpty()) PreviewSource.SAMPLES else PreviewSource.INSTALLED
        val recent = withContext(Dispatchers.IO) { container.textures.recent() }
        _ui.value = EditorUi(loading = false, name = doc.name, theme = doc.theme, source = start, items = itemsFor(start), recentTextures = recent)
        refresh()
    }

    /** Installed launchable apps that Arcticons covers, one cell per drawable, named by app. */
    private fun installedItems(app: Application, icons: IconSource): List<PreviewItem> {
        val plan = CoverageMatcher.match(InstalledApps.launchableComponents(app), Int.MAX_VALUE, icons::resolve)
        val pm = app.packageManager
        val labels = HashMap<Int, String>()
        for ((component, slot) in plan.items) {
            val d = plan.drawables[slot]
            if (d in labels) continue
            val pkg = component.substringBefore('/')
            labels[d] = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                icons.names.name(d).replace('_', ' ')
            }
        }
        return plan.drawables.map { PreviewItem(it, labels.getValue(it)) }.sortedBy { it.label.lowercase() }
    }

    private fun itemsFor(s: PreviewSource) = if (s == PreviewSource.INSTALLED) installed else samples

    fun commit(theme: Theme) = edit { it.commit(theme) }

    /** A slider moved. The drag becomes one undo step on [endDrag]. */
    fun drag(theme: Theme) {
        val h = history ?: return
        h.drag(theme)
        // Saved too, so a process death mid-drag keeps the value under the finger.
        pending.value = h.theme
        publish()
    }

    fun endDrag() = edit { it.endDrag() }

    fun undo() = edit { it.undo() }

    fun redo() = edit { it.redo() }

    /**
     * Saves the newest change now, skipping the debounce. The screen calls it when the
     * activity stops, the last moment before the process may be killed. Runs on the app
     * scope, so it finishes even if this view model is cleared meanwhile.
     */
    fun flush() {
        val theme = pending.value ?: return
        container.scope.launch { save(theme) }
    }

    private var applying = false

    /**
     * Apply: saves the newest change and makes this theme the active one, then calls
     * [then], which opens the Apply screen. Waits for both writes, so Apply never shows
     * the theme that was active before. A second tap meanwhile does nothing.
     */
    fun apply(then: () -> Unit) {
        if (applying) return
        applying = true
        viewModelScope.launch {
            try {
                repo.saveAndActivate(id, pending.value) ?: return@launch say("This theme is gone.")
                then()
            } catch (e: IOException) {
                Log.w(TAG, "Not applied", e)
                say("Not applied. Storage error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                applying = false
            }
        }
    }

    /** Press and hold: the preview shows plain Arcticons while [on]. */
    fun compare(on: Boolean) {
        if (_ui.value.comparing == on) return
        _ui.value = _ui.value.copy(comparing = on)
        refresh()
    }

    fun setSource(s: PreviewSource) {
        if (_ui.value.source == s) return
        saved[SOURCE_KEY] = s.name
        _ui.value = _ui.value.copy(source = s, items = itemsFor(s), focused = null)
        refresh()
    }

    /** The drawables on screen, which a drag renders first. */
    fun setVisible(drawables: Set<Int>) {
        visible = drawables
    }

    fun messageShown() {
        _ui.value = _ui.value.copy(message = null, undoable = false)
    }

    private fun say(message: String, undoable: Boolean = false) {
        _ui.value = _ui.value.copy(message = message, undoable = undoable)
    }

    fun selectLayer(id: String?) {
        _ui.value = _ui.value.copy(selectedLayer = id)
    }

    fun showAddLayer(show: Boolean) {
        _ui.value = _ui.value.copy(addingLayer = show)
    }

    /** Adds a layer above the selected one, or on top, and selects it. */
    fun addLayer(source: Paint) {
        val h = history ?: return
        val layer = LayerStack.newLayer(source)
        _ui.value = _ui.value.copy(addingLayer = false)
        commit(LayerStack.insertAbove(h.theme, layer, _ui.value.selectedLayer))
        selectLayer(layer.id)
    }

    fun showAddEffect(show: Boolean) {
        _ui.value = _ui.value.copy(addingEffect = show)
    }

    /** Adds an effect where it shows, Task 9's placement, and selects it. */
    fun addEffect(effect: Effect) {
        val h = history ?: return
        val layer = LayerStack.newEffect(h.theme, effect)
        _ui.value = _ui.value.copy(addingEffect = false)
        commit(LayerStack.insertEffect(h.theme, layer))
        selectLayer(layer.id)
    }

    /** Layer [id] into the place of [targetId], one undo step. */
    fun moveLayerOnto(id: String, targetId: String) {
        val h = history ?: return
        commit(LayerStack.moveOnto(h.theme, id, targetId))
    }

    /** The same while a row is dragged; [endDrag] on release makes the drag one undo step. */
    fun dragLayerOnto(id: String, targetId: String) {
        val h = history ?: return
        drag(LayerStack.moveOnto(h.theme, id, targetId))
    }

    /** The eye: hides or shows a layer, one undo step. Opacity stays as it is. */
    fun setLayerVisible(id: String, visible: Boolean) {
        val h = history ?: return
        commit(LayerStack.withVisible(h.theme, id, visible))
    }

    /** Effect [id] behind the icon or on top of it. */
    fun placeEffect(id: String, onTop: Boolean) {
        val h = history ?: return
        commit(LayerStack.placeEffect(h.theme, id, onTop))
    }

    /** One undo step; the snackbar offers it right away. */
    fun removeLayer(id: String) {
        val h = history ?: return
        val label = LayerStack.layer(h.theme, id)?.let { LayerStack.label(h.theme, it) } ?: return
        commit(LayerStack.remove(h.theme, id))
        removal = h.mark()
        if (_ui.value.selectedLayer == id) selectLayer(null)
        say("Removed $label", undoable = true)
    }

    /**
     * The snackbar's Undo: reverts the removal only while the history is still where the
     * removal left it, same depth and same theme. After any later edit it does nothing.
     */
    fun undoRemoval() {
        val mark = removal ?: return
        removal = null
        var undone = false
        edit { h -> h.undoIfAt(mark).also { undone = it } }
        if (!undone) say("Not undone: the theme changed after the layer was removed.")
    }

    /** Opens the texture chooser for [target]. */
    fun chooseTexture(target: TextureTarget) {
        textureTarget = target
        _ui.value = _ui.value.copy(addingLayer = false, addingEffect = false, choosingTexture = true)
        viewModelScope.launch {
            val recent = withContext(Dispatchers.IO) { container.textures.recent() }
            _ui.value = _ui.value.copy(recentTextures = recent)
        }
    }

    fun dismissTextureChooser() {
        _ui.value = _ui.value.copy(choosingTexture = false)
    }

    fun textureFile(hash: String): File = container.textures.file(hash)

    /** Imports a picked image as a texture, then uses it. */
    fun importTexture(uri: Uri) {
        Log.i(TAG, "texture picked: $uri")
        _ui.value = _ui.value.copy(choosingTexture = false, importingTexture = true)
        viewModelScope.launch {
            try {
                val hash = withContext(Dispatchers.IO) { TextureImport.import(getApplication(), uri, container.textures) }
                useTexture(hash)
            } catch (e: TextureImportException) {
                Log.w(TAG, "Texture not imported", e)
                say("Texture not imported. ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Texture not imported", e)
                say("Texture not imported. This image could not be read.")
            } catch (e: IOException) {
                say("Texture not imported. Storage error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _ui.value = _ui.value.copy(importingTexture = false)
            }
        }
    }

    /** Uses an imported texture or a built-in brush for the target the chooser was opened with. */
    fun useTexture(hash: String) {
        Log.i(TAG, "texture chosen: $hash for $textureTarget")
        _ui.value = _ui.value.copy(choosingTexture = false)
        viewModelScope.launch {
            // An imported texture from an earlier session may not be in memory yet.
            val missing = withContext(Dispatchers.IO) { container.textures.load(setOf(hash)) }
            if (missing.isNotEmpty()) return@launch say("This texture could not be read.")
            val h = history ?: return@launch
            val recent = withContext(Dispatchers.IO) { container.textures.recent() }
            _ui.value = _ui.value.copy(recentTextures = recent)
            when (val t = textureTarget) {
                TextureTarget.NewLayer -> addLayer(Paint.Texture(hash))
                TextureTarget.Background -> commit(LayerStack.withBackgroundTexture(h.theme, hash))
                is TextureTarget.Source -> (LayerStack.layer(h.theme, t.layerId) as? PaintLayer)?.let { l ->
                    commit(LayerStack.replace(h.theme, LayerStack.withTexture(l, hash)))
                }
                is TextureTarget.Breakup -> (LayerStack.layer(h.theme, t.layerId) as? EffectLayer)?.let { l ->
                    val e = l.effect as? Effect.Streaks ?: return@let
                    val b = (e.breakup ?: dev.iconoclast.render.Breakup()).copy(texture = hash)
                    commit(LayerStack.replace(h.theme, l.copy(effect = e.copy(breakup = b))))
                }
            }
        }
    }

    /** Applies a history operation; if it changed the theme, publishes and queues a save. */
    private inline fun edit(op: (EditorHistory) -> Boolean) {
        val h = history ?: return
        if (op(h)) pending.value = h.theme
        publish()
    }

    private fun publish() {
        val h = history ?: return
        _ui.value = _ui.value.copy(theme = h.theme, canUndo = h.canUndo, canRedo = h.canRedo)
        refresh()
    }

    private fun refresh() {
        val h = history ?: return
        val ui = _ui.value
        val absent = TexturePresence.absent(container.textures.store, h.theme)
        if (absent.isNotEmpty() && absent != reportedAbsent) say(TexturePresence.message(absent.size))
        reportedAbsent = absent
        // The renderer throws on a texture it does not hold; the preview hides that layer instead.
        val theme = if (ui.comparing) plain ?: return else TexturePresence.without(h.theme, absent)
        val first = visible.takeIf { h.dragging && !ui.comparing && it.isNotEmpty() }
        val names = source?.names ?: return
        val byDrawable = ui.items.mapNotNull { item -> overrides[names.name(item.drawable)]?.let { item.drawable to it } }.toMap()
        if (byDrawable.keys != ui.overridden) _ui.value = _ui.value.copy(overridden = byDrawable.keys)
        // Compare shows plain Arcticons: no override, no swap.
        val shown = if (ui.comparing) emptyMap() else byDrawable
        previews.request(PreviewRequest(theme, ui.items.map { it.drawable }, first, shown, container.settings.resolution.value))
        val hero = ui.focused?.takeIf { f -> ui.items.any { it.drawable == f } } ?: ui.items.firstOrNull()?.drawable
        if (hero != null) {
            heroPreviews.request(PreviewRequest(theme, listOf(hero), null, shown.filterKeys { it == hero }, container.settings.resolution.value))
        }
    }

    /**
     * One preview icon: the effective theme and geometry, as Apply draws its slot. A swap
     * target missing from the icon set draws the icon's own geometry, as PackBuilder does.
     */
    private fun draw(drawable: Int, look: PreviewLook, size: Int): ImageBitmap {
        val icons = requireNotNull(source)
        val name = icons.names.name(drawable)
        val theme = look.override?.applyTo(look.theme) ?: look.theme
        val geometry = look.override?.swapDrawable?.let(icons::indexOf)?.takeIf { it >= 0 } ?: drawable
        return container.renderer.render(icons.names.name(geometry), icons.icon(geometry), name, theme, size, look.resolution).toImageBitmap()
    }

    /** Opens the override sheet for [item], spec 2.2 screen 3. */
    fun openOverride(item: PreviewItem) {
        val name = source?.names?.name(item.drawable) ?: return
        _ui.value = _ui.value.copy(overriding = item, override = overrides[name] ?: IconOverride.NONE, focused = item.drawable)
        refresh()
    }

    fun closeOverride() {
        _ui.value = _ui.value.copy(overriding = null, override = IconOverride.NONE)
    }

    /** A slider in the sheet moved: the preview follows, nothing is written until [commitOverride]. */
    fun dragOverride(o: IconOverride) = showOverride(o)

    /** Shows [o] and writes it through the repository; an empty one removes the override. */
    fun commitOverride(o: IconOverride) {
        val name = showOverride(o) ?: return
        // Undispatched: each write joins the repository's lock queue in the order made.
        container.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (o.isEmpty) repo.clearOverride(id, name) else repo.setOverride(id, name, o)
            } catch (e: IOException) {
                Log.w(TAG, "Override not saved", e)
                say("Override not saved. Storage error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** "Reset to theme". */
    fun resetOverride() = commitOverride(IconOverride.NONE)

    private fun showOverride(o: IconOverride): String? {
        val item = _ui.value.overriding ?: return null
        val name = source?.names?.name(item.drawable) ?: return null
        overrides = if (o.isEmpty) overrides - name else overrides + (name to o)
        _ui.value = _ui.value.copy(override = o)
        refresh()
        return name
    }

    /** The drawable name of preview cell [drawable], for the sheet. */
    fun drawableName(drawable: Int): String = source?.names?.name(drawable).orEmpty()

    /** Drawable names matching [query], prefix matches first, for the swap search. */
    suspend fun searchDrawables(query: String): List<String> = withContext(Dispatchers.Default) {
        val icons = source ?: return@withContext emptyList()
        icons.search(query, SEARCH_LIMIT).map(icons.names::name)
    }

    /** The texture offset the icon shows without an override: its own seeded one. */
    fun ownOffset(drawable: Int): TextureOffset = TextureOffset.of(drawableName(drawable))

    /** Writes the theme field only, so overrides saved meanwhile by the override sheet stay. */
    private suspend fun save(theme: Theme) {
        try {
            repo.updateTheme(id, theme)
        } catch (e: IOException) {
            Log.w(TAG, "Theme not saved", e)
            _ui.value = _ui.value.copy(message = "Not saved. Storage error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun onCleared() {
        // Leaving within the debounce: the last change still reaches the disk.
        flush()
    }

    companion object {
        private const val TAG = "IconoclastEditor"

        /** Never below 128 px; see [dev.iconoclast.app.ThemePreviews.SIZE]. Also while dragging. */
        const val SIZE = 128
        /** The big preview: 256 px, close to one to one at its 104 dp on a 420 dpi screen. */
        const val HERO_SIZE = 256
        const val RENDER_THREADS = 4
        const val SAVE_DELAY_MS = 300L
        const val SEARCH_LIMIT = 30
        private const val SOURCE_KEY = "previewSource"

        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, createSavedStateHandle(), id) }
        }
    }
}
