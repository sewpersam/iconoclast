package dev.iconoclast.app

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import dev.iconoclast.app.texture.TexturePresence
import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.applyTo
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.Theme
import kotlinx.coroutines.withContext

/** One preview icon. [label] is its content description. */
class PreviewIcon(val label: String, val image: ImageBitmap)

/**
 * The live preview icons on a theme card, spec 2.2 screen 1: a fixed sample set,
 * rendered off the main thread by the shared renderer at [SIZE], cached by theme.
 */
class ThemePreviews(private val container: AppContainer) {
    /** The theme, the samples' overrides and the resolution: equal recipes share one entry. */
    private data class Key(val theme: Theme, val overrides: Map<String, IconOverride>, val resolution: OutputResolution)

    // Bounded by bytes.
    private val cache = object : LruCache<Key, List<PreviewIcon>>(CACHE_BYTES) {
        override fun sizeOf(key: Key, value: List<PreviewIcon>) = value.size * SIZE * SIZE * 4
    }

    /**
     * The card's icons. A texture missing from the phone, or lost while rendering, hides
     * its layer here, and the result is not cached, so the card redraws once the texture
     * arrives. Each icon is drawn as Apply draws it: its override and swap applied, at the
     * chosen resolution's grain.
     */
    suspend fun icons(doc: ThemeDocument): List<PreviewIcon> {
        val resolution = container.settings.resolution.value
        val key = Key(doc.theme, doc.overrides.filterKeys { it in SAMPLES }, resolution)
        return cache.get(key) ?: withContext(container.previewThreads) {
            val source = container.iconSource()
            val shown = TexturePresence.render(container.textures.store, doc.theme) { t ->
                SAMPLES.mapNotNull { name ->
                    if (source.indexOf(name) < 0) return@mapNotNull null
                    val o = key.overrides[name]
                    val geometry = o?.swapDrawable?.takeIf { source.indexOf(it) >= 0 } ?: name
                    val theme = o?.applyTo(t) ?: t
                    val image = container.renderer.render(geometry, source.icon(source.indexOf(geometry)), name, theme, SIZE, resolution)
                    PreviewIcon(name.replace('_', ' '), image.toImageBitmap())
                }
            }
            shown.value.also { if (shown.complete) cache.put(key, it) }
        }
    }

    companion object {
        /**
         * Never below 128 px. At 64 px a 1-unit stroke is about one pixel, enclosed areas
         * leak and invert fill falls back to outline, so the card would not show the pack.
         */
        const val SIZE = 128
        /** About 28 themes of nine 128 px icons. */
        private const val CACHE_BYTES = 16 * 1024 * 1024

        /** Nine of [dev.iconoclast.render.SampleIcons]: varied shapes, dots, enclosed and open areas. */
        val SAMPLES = listOf("fdroid", "camera", "firefox", "signal", "calculator", "clock", "settings", "weather", "termux")
    }
}
