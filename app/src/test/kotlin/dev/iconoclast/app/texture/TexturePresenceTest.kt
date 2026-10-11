package dev.iconoclast.app.texture

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.texture.TextureStore
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TexturePresenceTest {
    private val cracks = File(System.getProperty("golden.dir"), GoldenSet.TEXTURE_FILE).readBytes()
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val name = "fdroid"
    private val icon = bundle.icon(GoldenSet.SAMPLES.indexOf(name))
    private val theme = GoldenSet.LOOKS.getValue("layers_etched")

    /**
     * The home card's case: the texture passes the check, then its file is lost and the
     * store evicts it before the draw. The renderer throws; the card shows the theme
     * without that layer instead of crashing, and says the result is not to be cached.
     */
    @Test
    fun textureLostMidRenderIsHiddenNotThrown() {
        var loads = 0
        val store = TextureStore(load = { if (loads++ == 0) cracks else null })
        val renderer = IconRenderer(textures = store)
        var attempts = 0
        val shown = TexturePresence.render(store, theme) { t ->
            if (attempts++ == 0) store.remove(GoldenSet.TEXTURE)
            renderer.render(name, icon, t, 64).pixels
        }
        assertEquals(2, attempts, "the first draw failed and was retried")
        assertFalse(shown.complete)
        val bare = IconRenderer().render(name, icon, TexturePresence.without(theme, setOf(GoldenSet.TEXTURE)), 64).pixels
        assertContentEquals(bare, shown.value)
    }

    @Test
    fun allTexturesPresentIsComplete() {
        val store = TextureStore().apply { put(cracks) }
        val shown = TexturePresence.render(store, theme) { t -> IconRenderer(textures = store).render(name, icon, t, 64).pixels }
        assertTrue(shown.complete)
    }
}
