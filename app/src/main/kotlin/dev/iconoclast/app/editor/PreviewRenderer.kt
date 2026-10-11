package dev.iconoclast.app.editor

import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * What to show: [theme] over [drawables], in grid order. [first], when set, limits this
 * pass to those drawables, the visible ones during a drag; the rest keep their last image
 * until a full request. [overrides] are the per-icon overrides by drawable, and
 * [resolution] the pack size the previews stand for, which sets their grain cells.
 */
data class PreviewRequest(
    val theme: Theme,
    val drawables: List<Int>,
    val first: Set<Int>? = null,
    val overrides: Map<Int, IconOverride> = emptyMap(),
    val resolution: OutputResolution = OutputResolution.FULL,
)

/** One icon's look: the theme, its override if any, and the resolution. Part of the cache key. */
data class PreviewLook(val theme: Theme, val override: IconOverride? = null, val resolution: OutputResolution = OutputResolution.FULL)

/** The images on screen. [complete] when every drawable shows [theme]. */
class PreviewFrame<T>(val theme: Theme?, val images: Map<Int, T>, val complete: Boolean)

/**
 * The editor's single-flight preview renderer. One pass at a time runs on [dispatcher];
 * requests made meanwhile replace each other, and only the newest runs next. None is
 * queued and none is cancelled midway, so a fast drag still shows every pass it
 * finishes. Images are cached per (look, drawable, size), bounded by [cacheEntries], so a
 * changed override redraws only its own icon.
 *
 * [render] draws one icon, called in parallel on [dispatcher]. [log] receives one timing
 * line per pass.
 */
class PreviewRenderer<T : Any>(
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val size: Int,
    private val render: (drawable: Int, look: PreviewLook) -> T,
    private val cacheEntries: Int = 512,
    private val log: (String) -> Unit = {},
) {
    private data class Key(val look: PreviewLook, val drawable: Int, val size: Int)

    private val cache = object : LinkedHashMap<Key, T>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, T>) = size > cacheEntries
    }

    private val requests = MutableStateFlow<PreviewRequest?>(null)

    private val _frame = MutableStateFlow(PreviewFrame<T>(null, emptyMap(), false))
    val frame: StateFlow<PreviewFrame<T>> = _frame

    /** Passes run, for the gate log and tests. */
    @Volatile var passes = 0; private set

    /** Passes that failed, for tests. */
    @Volatile var failures = 0; private set

    init {
        // A StateFlow is conflated: while a pass runs, newer requests overwrite each other.
        scope.launch { requests.filterNotNull().collect { pass(it) } }
    }

    fun request(r: PreviewRequest) {
        requests.value = r
    }

    private suspend fun pass(r: PreviewRequest) {
        val t0 = System.nanoTime()
        val wanted = r.first?.let { f -> r.drawables.filter { it in f } } ?: r.drawables
        fun key(d: Int) = Key(PreviewLook(r.theme, r.overrides[d], r.resolution), d, size)
        val missing = synchronized(cache) { wanted.filter { cache[key(it)] == null } }
        try {
            val drawn = coroutineScope { missing.map { d -> async(dispatcher) { d to render(d, key(d).look) } }.awaitAll() }
            synchronized(cache) { for ((d, img) in drawn) cache[key(d)] = img }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Forget the request, so asking again for the same one is not swallowed as equal.
            requests.compareAndSet(r, null)
            failures++
            log("preview pass failed: $e")
            return
        }
        val shown = _frame.value.images
        var complete = true
        val images = synchronized(cache) {
            r.drawables.mapNotNull { d ->
                val img = cache[key(d)] ?: shown[d].also { complete = false }
                img?.let { d to it }
            }.toMap()
        }
        passes++
        _frame.value = PreviewFrame(r.theme, images, complete)
        val ms = (System.nanoTime() - t0) / 1_000_000
        log("preview pass: ${missing.size} drawn, ${wanted.size - missing.size} cached, ${r.drawables.size} shown at $size px in $ms ms" +
            if (r.first != null) ", visible first" else "")
    }
}
