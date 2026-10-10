package dev.iconoclast.app

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** One preview icon. [label] is its content description. */
class PreviewIcon(val label: String, val image: ImageBitmap)

/**
 * The live preview icons on a theme card, spec 2.2 screen 1: a fixed sample set,
 * rendered off the main thread by the shared renderer, cached by theme.
 */
class ThemePreviews(private val container: AppContainer) {
    // Keyed by the Theme itself: a data class, so equal recipes share one entry.
    private val cache = LruCache<Theme, List<PreviewIcon>>(CACHED_THEMES)

    suspend fun icons(theme: Theme): List<PreviewIcon> = cache.get(theme) ?: withContext(Dispatchers.Default) {
        val source = container.iconSource()
        SAMPLES.mapNotNull { name ->
            val index = source.indexOf(name).takeIf { it >= 0 } ?: return@mapNotNull null
            val image = container.renderer.render(index, source.icon(index), theme, SIZE)
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            // ARGB_8888 stores premultiplied RGBA bytes, the renderer's layout.
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(image.pixels))
            PreviewIcon(name.replace('_', ' '), bitmap.asImageBitmap())
        }
    }.also { cache.put(theme, it) }

    companion object {
        const val SIZE = 64
        private const val CACHED_THEMES = 32

        /** Nine of the golden sample icons: varied shapes, dots, enclosed and open areas. */
        val SAMPLES = listOf("fdroid", "camera", "firefox", "signal", "calculator", "clock", "settings", "weather", "termux")
    }
}
