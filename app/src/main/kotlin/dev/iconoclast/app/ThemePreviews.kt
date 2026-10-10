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
 * rendered off the main thread by the shared renderer at [SIZE], cached by theme.
 */
class ThemePreviews(private val container: AppContainer) {
    // Keyed by the Theme itself: a data class, so equal recipes share one entry. Bounded by bytes.
    private val cache = object : LruCache<Theme, List<PreviewIcon>>(CACHE_BYTES) {
        override fun sizeOf(key: Theme, value: List<PreviewIcon>) = value.size * SIZE * SIZE * 4
    }

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
        /**
         * Never below 128 px. At 64 px a 1-unit stroke is about one pixel, enclosed areas
         * leak and invert fill falls back to outline, so the card would not show the pack.
         */
        const val SIZE = 128
        /** About 28 themes of nine 128 px icons. */
        private const val CACHE_BYTES = 16 * 1024 * 1024

        /** Nine of the golden sample icons: varied shapes, dots, enclosed and open areas. */
        val SAMPLES = listOf("fdroid", "camera", "firefox", "signal", "calculator", "clock", "settings", "weather", "termux")
    }
}
