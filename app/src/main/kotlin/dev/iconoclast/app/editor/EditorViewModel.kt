package dev.iconoclast.app.editor

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
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
import dev.iconoclast.app.theme.applyTo
import dev.iconoclast.render.TextureOffset
import kotlinx.coroutines.CoroutineStart
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

/** Where a chosen texture goes. */
sealed interface TextureTarget {
    /** A new layer above the selected one. */
    data object NewLayer : TextureTarget
    /** The source of an existing layer. */
    data class Source(val layerId: String) : TextureTarget
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
    /** The user's apps that Arcticons covers; the sample set when none is installed. */
    val items: List<PreviewItem> = emptyList(),
    val message: String? = null,
    /** The snackbar offers Undo for [message]. */
    val undoable: Boolean = false,
    /**
     * The row whose properties page is open: a layer id, [LayerStack.ICON_ROW] for the
     * Icon, or null for the layer list.
     */
    val page: String? = null,
    /** The Add sheet is open. */
    val adding: Boolean = false,
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
class EditorViewModel(app: Application, private val id: String) : AndroidViewModel(app) {
    private val container = app.container
    private val repo = container.themes

    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui

    private var history: EditorHistory? = null
    private var source: IconSource? = null
    /** Drawable index by drawable name, for the preview items: overrides are keyed by name. */
    private var itemByName: Map<String, Int> = emptyMap()
    /** The strip cells on screen. Only they, and the big one, are drawn. */
    private var visible: Set<Int> = emptySet()
    private val visibleChanges = MutableStateFlow<Set<Int>>(emptySet())
    private var reportedAbsent: Set<String> = emptySet()
    /** History depth and theme right after the last layer removal, for its snackbar Undo. */
    private var removal: EditorHistory.Mark? = null
    private var textureTarget: TextureTarget = TextureTarget.NewLayer
    /** The overrides as shown; each change is written through the repository at once. */
    private var overrides: Map<String, IconOverride> = emptyMap()


    private val previews = PreviewRenderer(
        viewModelScope,
        container.previewThreads,
        SIZE,
        render = { d, look -> draw(d, look, SIZE) },
        log = { Log.i(TAG, it) },
    )

    /** The big preview: one icon at [HERO_SIZE], its own single-flight pass beside the grid's. */
    private val heroPreviews = PreviewRenderer(viewModelScope, container.heroThread, HERO_SIZE, render = { d, look -> draw(d, look, HERO_SIZE) }, cacheEntries = 32)

    /** The preview images by drawable index. */
    val frame: StateFlow<PreviewFrame<ImageBitmap>> = previews.frame

    /** The big preview's image, by drawable index. */
    val hero: StateFlow<PreviewFrame<ImageBitmap>> = heroPreviews.frame

    /** When the newest edit was made, or the editor opened, and which of its previews were logged shown: 1 hero, 2 grid. */
    private var editAt = 0L
    private var shownLogged = 0

    /**
     * The screen drew [which] preview frame of [theme]. Logs, once per edit, how long after
     * the edit the hero and then the complete grid reached the screen.
     */
    fun shown(which: String, theme: Theme?, complete: Boolean) {
        val h = history ?: return
        if (editAt == 0L || theme != h.theme) return
        val bit = if (which == "hero") 1 else 2
        if (which != "hero" && !complete) return
        if (shownLogged and bit != 0) return
        shownLogged = shownLogged or bit
        Log.i(PERF_TAG, "$which shown ${(System.nanoTime() - editAt) / 1_000_000} ms after the edit or open")
    }

    /** The newest theme not yet saved. */
    private val pending = MutableStateFlow<Theme?>(null)

    init {
        editAt = System.nanoTime()
        viewModelScope.launch { load() }
        viewModelScope.launch { pending.filterNotNull().debounce(SAVE_DELAY_MS).collect { save(it) } }
        // The resolution setting sets the previews' grain cells.
        viewModelScope.launch { container.settings.resolution.collect { refresh() } }
        // Strip cells scrolling into view are drawn as they arrive.
        viewModelScope.launch { visibleChanges.collect { refresh() } }
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
        // Your apps, styled. A phone with none of them covered shows the sample set instead.
        val items = withContext(Dispatchers.IO) { installedItems(app, icons) }.ifEmpty {
            SampleIcons.NAMES.mapNotNull { name -> icons.indexOf(name).takeIf { it >= 0 }?.let { PreviewItem(it, name.replace('_', ' ')) } }
        }
        itemByName = items.associate { icons.names.name(it.drawable) to it.drawable }
        withContext(Dispatchers.IO) { TexturePresence.ensure(container.textures, doc.theme) }
        history = EditorHistory(doc.theme)
        overrides = doc.overrides
        val recent = withContext(Dispatchers.IO) { container.textures.recent() }
        _ui.value = EditorUi(loading = false, name = doc.name, theme = doc.theme, items = items, recentTextures = recent)
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

    /** The strip's drawables on screen: only they are drawn. */
    fun setVisible(drawables: Set<Int>) {
        if (drawables == visible) return
        visible = drawables
        visibleChanges.value = drawables
    }

    fun messageShown() {
        _ui.value = _ui.value.copy(message = null, undoable = false)
    }

    private fun say(message: String, undoable: Boolean = false) {
        _ui.value = _ui.value.copy(message = message, undoable = undoable)
    }

    /** Opens the properties page of row [id], or back to the list with null. */
    fun openPage(id: String?) {
        _ui.value = _ui.value.copy(page = id)
    }

    fun showAdd(show: Boolean) {
        _ui.value = _ui.value.copy(adding = show)
    }

    /** Adds a layer on top of the stack, over the icon, and opens its page. */
    fun addLayer(source: Paint) {
        val h = history ?: return
        val layer = LayerStack.newLayer(source)
        _ui.value = _ui.value.copy(adding = false)
        commit(LayerStack.insertAbove(h.theme, layer, null))
        openPage(layer.id)
    }

    /** Adds an effect where it shows, Task 9's placement, and opens its page. */
    fun addEffect(effect: Effect) {
        val h = history ?: return
        val layer = LayerStack.newEffect(h.theme, effect)
        _ui.value = _ui.value.copy(adding = false)
        commit(LayerStack.insertEffect(h.theme, layer))
        openPage(layer.id)
    }

    /** Renames this theme. A blank name is ignored. */
    fun rename(name: String) {
        val n = name.trim().ifEmpty { return }
        _ui.value = _ui.value.copy(name = n)
        container.scope.launch {
            try {
                repo.rename(id, n)
            } catch (e: IOException) {
                Log.w(TAG, "Not renamed", e)
                say("Not renamed. Storage error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
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

    /** One undo step; the snackbar offers it right away. */
    fun removeLayer(id: String) {
        val h = history ?: return
        val label = LayerStack.layer(h.theme, id)?.let { LayerStack.label(h.theme, it) } ?: return
        commit(LayerStack.remove(h.theme, id))
        removal = h.mark()
        if (_ui.value.page == id) openPage(null)
        say("Deleted $label", undoable = true)
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
        if (!undone) say("Not undone: the theme changed after the layer was deleted.")
    }

    /** Opens the texture chooser for [target]. */
    fun chooseTexture(target: TextureTarget) {
        textureTarget = target
        _ui.value = _ui.value.copy(adding = false, choosingTexture = true)
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
                say("Image not added. ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Texture not imported", e)
                say("Image not added. This image could not be read.")
            } catch (e: IOException) {
                say("Image not added. Storage error: ${e.message ?: e.javaClass.simpleName}")
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
            if (missing.isNotEmpty()) return@launch say("This image could not be read.")
            val h = history ?: return@launch
            val recent = withContext(Dispatchers.IO) { container.textures.recent() }
            _ui.value = _ui.value.copy(recentTextures = recent)
            when (val t = textureTarget) {
                TextureTarget.NewLayer -> addLayer(Paint.Texture(hash))
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
        editAt = System.nanoTime()
        shownLogged = 0
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
        val theme = TexturePresence.without(h.theme, absent)
        val byDrawable = HashMap<Int, IconOverride>()
        for ((name, o) in overrides) itemByName[name]?.let { byDrawable[it] = o }
        if (byDrawable.keys != ui.overridden) _ui.value = _ui.value.copy(overridden = byDrawable.keys.toSet())
        val resolution = container.settings.resolution.value
        val drawables = ui.items.map { it.drawable }
        // While a slider moves, only the cells on screen: the drag's frames come sooner.
        previews.request(PreviewRequest(theme, drawables, onScreen(drawables, if (h.dragging) 0 else MARGIN), byDrawable, resolution))
        val hero = ui.focused?.takeIf { f -> ui.items.any { it.drawable == f } } ?: ui.items.firstOrNull()?.drawable
        if (hero != null) {
            heroPreviews.request(PreviewRequest(theme, listOf(hero), null, byDrawable.filterKeys { it == hero }, resolution))
        }
    }

    /**
     * The strip cells to draw: those on screen and [margin] either side, so a short scroll
     * finds them ready. Before the strip is laid out, the first screenful.
     */
    private fun onScreen(drawables: List<Int>, margin: Int): Set<Int> {
        val seen = visible
        if (seen.isEmpty()) return drawables.take(FIRST_SCREEN).toSet()
        val first = drawables.indexOfFirst { it in seen }
        val last = drawables.indexOfLast { it in seen }
        if (first < 0) return drawables.take(FIRST_SCREEN).toSet()
        return drawables.subList((first - margin).coerceAtLeast(0), (last + margin + 1).coerceAtMost(drawables.size)).toSet()
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
                say("Change not saved. Storage error: ${e.message ?: e.javaClass.simpleName}")
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
        private const val PERF_TAG = "IconoclastPerf"

        /** Never below 128 px; see [dev.iconoclast.app.ThemePreviews.SIZE]. Also while dragging. */
        const val SIZE = 128
        /** The big preview: 256 px, close to one to one at its 104 dp on a 420 dpi screen. */
        const val HERO_SIZE = 256
        const val SAVE_DELAY_MS = 300L
        const val SEARCH_LIMIT = 30
        /** Strip cells drawn beyond each side of the screen: two columns of three. */
        private const val MARGIN = 6
        /** Strip cells drawn before the strip reports what it shows: about one screen. */
        private const val FIRST_SCREEN = 18

        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, id) }
        }
    }
}
