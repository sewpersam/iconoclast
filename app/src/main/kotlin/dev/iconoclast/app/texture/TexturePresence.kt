package dev.iconoclast.app.texture

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.texture.TextureStore

/**
 * The renderer throws on a texture that is not in its store. Every preview and every
 * pack build checks first, here, and the app reports the gap instead.
 */
object TexturePresence {

    /** Decodes each of [theme]'s textures through the store. Returns those that cannot render. Slow; not on the main thread. */
    fun ensure(files: TextureFiles, theme: Theme): Set<String> = files.load(theme.textureHashes())

    /** The hashes of [theme] that are not in [store]. Cheap; no disk access. */
    fun absent(store: TextureStore, theme: Theme): Set<String> = theme.textureHashes().filterTo(LinkedHashSet()) { it !in store }

    /** A render of [theme] with some layers hidden when [complete] is false; such a result is not cached. */
    class Shown<T>(val value: T, val complete: Boolean)

    /**
     * Renders what of [theme] can show: a texture [store] cannot supply hides its layer. A
     * texture damaged or deleted between that check and the draw makes the renderer throw
     * mid-way; it is checked again and hidden, and as a last resort every texture is.
     * Decodes through the store; not on the main thread.
     */
    fun <T> render(store: TextureStore, theme: Theme, render: (Theme) -> T): Shown<T> {
        val all = theme.textureHashes()
        var absent = missing(store, all)
        while (true) {
            try {
                return Shown(render(without(theme, absent)), absent.isEmpty())
            } catch (e: IllegalStateException) {
                if (absent == all) throw e
                val again = absent + missing(store, all)
                absent = if (again == absent) all else again
            }
        }
    }

    private fun missing(store: TextureStore, hashes: Set<String>): Set<String> = hashes.filterTo(LinkedHashSet()) { store[it] == null }

    /**
     * [theme] without the layers whose texture is in [absent]: what a preview can still
     * show. A paint layer goes with its texture, a streak with its breakup texture. A
     * streak whose linked paint layer went paints its own colour. Built-in brushes are
     * never absent: the store always has them.
     */
    fun without(theme: Theme, absent: Set<String>): Theme =
        if (absent.isEmpty()) theme
        else theme.copy(layers = theme.layers.filterNot {
            when (it) {
                is PaintLayer -> (it.source as? Paint.Texture)?.sha256 in absent
                is EffectLayer -> (it.effect as? Effect.Streaks)?.breakup?.texture in absent
                else -> false
            }
        })

    /** The user's explanation. */
    fun message(count: Int): String =
        if (count == 1) "An image this theme uses is not on this phone. Its layer is hidden. Add the image again or delete the layer."
        else "$count images this theme uses are not on this phone. Their layers are hidden. Add them again or delete the layers."
}
