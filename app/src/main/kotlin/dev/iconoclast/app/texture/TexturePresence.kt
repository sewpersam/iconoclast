package dev.iconoclast.app.texture

import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.texture.TextureStore

/**
 * The renderer throws on a texture that is not in its store. Every preview and every
 * pack build checks first, here, and the app reports the gap instead.
 */
object TexturePresence {

    /** Loads [theme]'s textures from disk. Returns those still missing. Slow; not on the main thread. */
    fun ensure(files: TextureFiles, theme: Theme): Set<String> = files.load(theme.textureHashes())

    /** The hashes of [theme] that are not in [store]. Cheap; no disk access. */
    fun absent(store: TextureStore, theme: Theme): Set<String> = theme.textureHashes().filterTo(LinkedHashSet()) { it !in store }

    /** [theme] without the layers whose texture is in [absent]: what a preview can still show. */
    fun without(theme: Theme, absent: Set<String>): Theme =
        if (absent.isEmpty()) theme
        else theme.copy(layers = theme.layers.filterNot { ((it as? PaintLayer)?.source as? Paint.Texture)?.sha256 in absent })

    /** The user's explanation. */
    fun message(count: Int): String =
        if (count == 1) "A texture this theme uses is not on this phone. Its layer is hidden. Import the texture again or remove the layer."
        else "$count textures this theme uses are not on this phone. Their layers are hidden. Import them again or remove the layers."
}
