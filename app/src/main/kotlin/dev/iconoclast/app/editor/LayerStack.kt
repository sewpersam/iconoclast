package dev.iconoclast.app.editor

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
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

    /** Removes a paint or effect layer. The effects marker stays: a stack holds exactly one. */
    fun remove(theme: Theme, id: String): Theme =
        if (layer(theme, id) is EffectsLayer) theme else theme.copy(layers = theme.layers.filterNot { it.id == id })

    /**
     * The mapping a new texture layer starts with: scale 2, so the texture leaves room for
     * every icon to show its own patch. At scale 1 a square texture fills the canvas exactly
     * and every icon shows the same one.
     */
    val NEW_TEXTURE_MAPPING = Mapping(scale = 2f)

    /** A new layer with a fresh id over the icon, normal and opaque; a texture at [NEW_TEXTURE_MAPPING]. */
    fun newLayer(source: Paint): PaintLayer = PaintLayer(
        UUID.randomUUID().toString(), source, clip = ClipTarget.ICON,
        mapping = if (source is Paint.Texture) NEW_TEXTURE_MAPPING else Mapping(),
    )

    /**
     * [layer] painting texture [hash]. A texture layer keeps its stops and mapping; a layer
     * that painted something else starts at [NEW_TEXTURE_MAPPING], as a new one would.
     */
    fun withTexture(layer: PaintLayer, hash: String): PaintLayer {
        val old = layer.source as? Paint.Texture
        return layer.copy(source = Paint.Texture(hash, old?.stops), mapping = if (old != null) layer.mapping else NEW_TEXTURE_MAPPING)
    }

    /** [layer] clipped to [clip]. The whole canvas has no outside, so its inverse is dropped. */
    fun withClip(layer: PaintLayer, clip: ClipTarget): PaintLayer =
        layer.copy(clip = clip, clipInverse = layer.clipInverse && clip != ClipTarget.CANVAS)

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
        val layer = PaintLayer(BACKGROUND_TEXTURE_ID, Paint.Texture(hash), clip = ClipTarget.BACKGROUND, mapping = NEW_TEXTURE_MAPPING)
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

    private fun kindName(layer: Layer): String = when (layer) {
        is EffectLayer -> effectName(layer.effect)
        is PaintLayer -> when (layer.source) {
            is Paint.Solid -> "Colour"
            is Paint.LinearGradient -> "Linear gradient"
            is Paint.RadialGradient -> "Radial gradient"
            is Paint.Texture -> "Texture"
            is Paint.Noise -> "Noise"
        }
        else -> "Effects"
    }

    fun effectName(effect: Effect): String = when (effect) {
        is Effect.Streaks -> "Scanlines"
        is Effect.BrushStrokes -> "Brush strokes"
        is Effect.Bevel -> "Bevel and emboss"
        is Effect.DropShadow -> "Drop shadow"
        is Effect.InnerShadow -> "Inner shadow"
        is Effect.OuterGlow -> "Outer glow"
        is Effect.InnerGlow -> "Inner glow"
        is Effect.Grain -> "Grain"
    }

    /** The paint layers a streak can take its paint from: textures and noise. */
    fun streakSources(theme: Theme): List<PaintLayer> =
        theme.layers.filterIsInstance<PaintLayer>().filter { it.source is Paint.Texture || it.source is Paint.Noise }

    /**
     * A new effect layer with a fresh id. Streaks and brush strokes take their paint from the first texture
     * or noise layer, if any; grain overlays, as its contract asks.
     */
    fun newEffect(theme: Theme, effect: Effect): EffectLayer {
        val first = streakSources(theme).firstOrNull()?.id
        val e = when {
            effect is Effect.Streaks && effect.paintLayer == null -> effect.copy(paintLayer = first)
            effect is Effect.BrushStrokes && effect.paintLayer == null -> effect.copy(paintLayer = first)
            else -> effect
        }
        return EffectLayer(UUID.randomUUID().toString(), e, blend = if (e is Effect.Grain) BlendMode.OVERLAY else BlendMode.NORMAL)
    }

    /**
     * Inserts [layer] where its effect shows, Task 9's contract: streaks, brush strokes, drop shadow and
     * outer glow just above the effects marker and the effects on it, below the icon;
     * bevel, inner shadow, inner glow and grain on top of the stack.
     */
    fun insertEffect(theme: Theme, layer: EffectLayer): Theme = when (layer.effect) {
        is Effect.Streaks, is Effect.BrushStrokes, is Effect.DropShadow, is Effect.OuterGlow -> theme.withEffect(layer)
        else -> theme.copy(layers = theme.layers + layer)
    }

    /** Whether [layer] paints after the icon's fill or lines layer, so on top of the icon. */
    fun aboveIcon(theme: Theme, layer: Layer): Boolean {
        val i = theme.layers.indexOf(layer)
        val icon = theme.layers.indexOfFirst { it.id == Layer.ICON_FILL_ID || it.id == Layer.ICON_LINES_ID }
        return icon in 0 until i
    }

    private val NAMED = setOf(Layer.BACKGROUND_ID, Layer.EFFECTS_ID, Layer.ICON_FILL_ID, Layer.ICON_LINES_ID, BACKGROUND_TEXTURE_ID)
}
