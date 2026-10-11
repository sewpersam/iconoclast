package dev.iconoclast.app.editor

import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.util.UUID

/**
 * Edits of the layer stack, spec 2.3 "Layers", as pure functions of the [Theme]. Index 0
 * is the bottom of the stack; "up" moves a layer toward the top, painted later.
 */
object LayerStack {
    /** The background tab's texture layer: clipped to the background, right above its paint. */
    const val BACKGROUND_TEXTURE_ID = "background-texture"

    fun layer(theme: Theme, id: String?): Layer? = theme.layers.firstOrNull { it.id == id }

    fun replace(theme: Theme, layer: Layer): Theme = theme.copy(layers = theme.layers.map { if (it.id == layer.id) layer else it })

    /** Puts [layer] directly above [belowId], or on top when that is null or gone. */
    fun insertAbove(theme: Theme, layer: Layer, belowId: String?): Theme {
        val i = theme.layers.indexOfFirst { it.id == belowId }
        val at = if (i < 0) theme.layers.size else i + 1
        return theme.copy(layers = theme.layers.toMutableList().apply { add(at, layer) })
    }

    /** Swaps the layer with its neighbour above ([up]) or below. At either end, unchanged. */
    fun move(theme: Theme, id: String, up: Boolean): Theme {
        val i = theme.layers.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in theme.layers.indices) return theme
        val l = theme.layers.toMutableList()
        l[i] = l[j].also { l[j] = l[i] }
        return theme.copy(layers = l)
    }

    /** Removes a paint layer. The effects layer stays: a stack holds exactly one. */
    fun remove(theme: Theme, id: String): Theme =
        if (layer(theme, id) is EffectsLayer) theme else theme.copy(layers = theme.layers.filterNot { it.id == id })

    /** A new layer with a fresh id over the icon, normal and opaque. */
    fun newLayer(source: Paint): PaintLayer = PaintLayer(UUID.randomUUID().toString(), source, clip = ClipTarget.ICON)

    fun backgroundTexture(theme: Theme): PaintLayer? = layer(theme, BACKGROUND_TEXTURE_ID) as? PaintLayer

    /**
     * Sets the background's texture layer to [hash]: an existing one keeps its settings, a
     * new one goes right above the background paint, clipped to the background shape.
     */
    fun withBackgroundTexture(theme: Theme, hash: String): Theme {
        val existing = backgroundTexture(theme)
        if (existing != null) {
            val stops = (existing.source as? Paint.Texture)?.stops
            return replace(theme, existing.copy(source = Paint.Texture(hash, stops)))
        }
        val layer = PaintLayer(BACKGROUND_TEXTURE_ID, Paint.Texture(hash), clip = ClipTarget.BACKGROUND)
        val bottom = layer(theme, Layer.BACKGROUND_ID)?.id ?: return theme.copy(layers = listOf(layer) + theme.layers)
        return insertAbove(theme, layer, bottom)
    }

    /** The name shown in the list. Layers of one kind are numbered from the bottom. */
    fun label(theme: Theme, layer: Layer): String {
        when (layer.id) {
            Layer.BACKGROUND_ID -> return "Background"
            Layer.EFFECTS_ID -> return "Effects"
            Layer.ICON_FILL_ID -> return "Icon fill"
            Layer.ICON_LINES_ID -> return "Icon lines"
            BACKGROUND_TEXTURE_ID -> return "Background texture"
        }
        val kind = kindName(layer)
        val same = theme.layers.filter { it.id !in NAMED && kindName(it) == kind }
        return if (same.size < 2) kind else "$kind ${same.indexOf(layer) + 1}"
    }

    private fun kindName(layer: Layer): String = when (val s = (layer as? PaintLayer)?.source) {
        is Paint.Solid -> "Colour"
        is Paint.LinearGradient -> "Linear gradient"
        is Paint.RadialGradient -> "Radial gradient"
        is Paint.Texture -> "Texture"
        is Paint.Noise -> "Noise"
        null -> "Effects"
    }

    private val NAMED = setOf(Layer.BACKGROUND_ID, Layer.EFFECTS_ID, Layer.ICON_FILL_ID, Layer.ICON_LINES_ID, BACKGROUND_TEXTURE_ID)
}
