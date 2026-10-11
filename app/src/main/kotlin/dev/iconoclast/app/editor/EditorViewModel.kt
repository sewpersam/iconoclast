package dev.iconoclast.app.editor

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.iconoclast.app.InstalledApps
import dev.iconoclast.app.container
import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.CoverageMatcher
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

enum class PreviewSource { INSTALLED, SAMPLES }

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

    private val previews = PreviewRenderer(
        viewModelScope,
        Dispatchers.Default.limitedParallelism(RENDER_THREADS),
        SIZE,
        render = { d, theme -> draw(d, theme) },
        log = { Log.i(TAG, it) },
    )

    /** The preview images by drawable index. */
    val frame: StateFlow<PreviewFrame<ImageBitmap>> = previews.frame

    /** The newest theme not yet saved. */
    private val pending = MutableStateFlow<Theme?>(null)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { pending.filterNotNull().debounce(SAVE_DELAY_MS).collect { save(it) } }
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
        samples = SAMPLES.mapNotNull { name -> icons.indexOf(name).takeIf { it >= 0 }?.let { PreviewItem(it, name.replace('_', ' ')) } }
        installed = withContext(Dispatchers.IO) { installedItems(app, icons) }
        history = EditorHistory(doc.theme)
        // The toggle survives process death; the first visit shows installed apps when there are any.
        val start = saved.get<String>(SOURCE_KEY)?.let(PreviewSource::valueOf)
            ?: if (installed.isEmpty()) PreviewSource.SAMPLES else PreviewSource.INSTALLED
        _ui.value = EditorUi(loading = false, name = doc.name, theme = doc.theme, source = start, items = itemsFor(start))
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

    /** Press and hold: the preview shows plain Arcticons while [on]. */
    fun compare(on: Boolean) {
        if (_ui.value.comparing == on) return
        _ui.value = _ui.value.copy(comparing = on)
        refresh()
    }

    fun setSource(s: PreviewSource) {
        if (_ui.value.source == s) return
        saved[SOURCE_KEY] = s.name
        _ui.value = _ui.value.copy(source = s, items = itemsFor(s))
        refresh()
    }

    /** The drawables on screen, which a drag renders first. */
    fun setVisible(drawables: Set<Int>) {
        visible = drawables
    }

    fun messageShown() {
        _ui.value = _ui.value.copy(message = null)
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
        val theme = if (ui.comparing) plain ?: return else h.theme
        val first = visible.takeIf { h.dragging && !ui.comparing && it.isNotEmpty() }
        previews.request(PreviewRequest(theme, ui.items.map { it.drawable }, first))
    }

    private fun draw(drawable: Int, theme: Theme): ImageBitmap {
        val icons = requireNotNull(source)
        val image = container.renderer.render(icons.names.name(drawable), icons.icon(drawable), theme, SIZE)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        // ARGB_8888 stores premultiplied RGBA bytes, the renderer's layout.
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(image.pixels))
        return bitmap.asImageBitmap()
    }

    private suspend fun save(theme: Theme) {
        val doc = repo.get(id) ?: return
        if (doc.theme == theme) return
        try {
            repo.save(doc.copy(theme = theme))
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
        const val RENDER_THREADS = 4
        const val SAVE_DELAY_MS = 300L
        private const val SOURCE_KEY = "previewSource"

        /**
         * The sample set, spec 2.2: dense, sparse, text-heavy, dotted and open-line
         * icons. The 24 golden samples, which were chosen for that variety.
         */
        val SAMPLES = listOf(
            "fdroid", "camera", "google_chrome", "firefox", "signal", "telegram", "whatsapp", "google_maps",
            "youtube", "spotify", "calculator", "clock", "contacts", "gallery", "messages", "phone",
            "settings", "weather", "notes", "termux", "vlc", "wikipedia", "keepassdx", "newpipe",
        )

        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, createSavedStateHandle(), id) }
        }
    }
}
