package dev.iconoclast.app.editor

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.NoiseKind
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

    /**
     * The Icon row's id in the list. Its two layers, the icon fill and the icon lines, move,
     * hide and show together, and nothing goes between them.
     */
    const val ICON_ROW = "icon"

    /**
     * Puts [layer] directly above the row [belowId], or on top when that is null or gone.
     * Above the Icon row means above both its layers.
     */
    fun insertAbove(theme: Theme, layer: Layer, belowId: String?): Theme {
        val units = units(theme)
        val i = units.indexOfFirst { u -> u.any { it.id == belowId } || (belowId == ICON_ROW && isIcon(u)) }
        val l = units.toMutableList().apply { add(if (i < 0) size else i + 1, listOf(layer)) }
        return theme.copy(layers = l.flatten())
    }

    /**
     * The stack bottom to top in the units the list moves: one layer each, except the icon
     * fill and icon lines, one unit when they sit next to each other. A stack from an older
     * file is migrated to that on load, schema 7.
     */
    fun units(theme: Theme): List<List<Layer>> {
        val out = mutableListOf<List<Layer>>()
        var i = 0
        val l = theme.layers
        while (i < l.size) {
            val next = l.getOrNull(i + 1)
            if (next != null && setOf(l[i].id, next.id) == ICON_IDS) {
                out += listOf(l[i], next)
                i += 2
            } else {
                out += listOf(l[i])
                i++
            }
        }
        return out
    }

    private fun isIcon(unit: List<Layer>) = unit.size == 2 && unit.map { it.id }.toSet() == ICON_IDS

    private fun unitId(unit: List<Layer>) = if (isIcon(unit)) ICON_ROW else unit.single().id

    /** One row of the layer list: [id] is a layer's, or [ICON_ROW] for the icon's two. */
    data class Row(val id: String, val layers: List<Layer>) {
        val first: Layer get() = layers.first()
        val isIcon: Boolean get() = id == ICON_ROW
    }

    /** The layers row [id] stands for: the icon's two for [ICON_ROW], else that one layer. */
    fun rowLayers(theme: Theme, id: String?): List<Layer> =
        if (id == ICON_ROW) theme.layers.filter { it.id in ICON_IDS } else listOfNotNull(layer(theme, id))

    /** Swaps the layer with its neighbour above ([up]) or below. At either end, unchanged. */
    fun move(theme: Theme, id: String, up: Boolean): Theme {
        val i = theme.layers.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in theme.layers.indices) return theme
        val l = theme.layers.toMutableList()
        l[i] = l[j].also { l[j] = l[i] }
        return theme.copy(layers = l)
    }

    /**
     * Moves layer [id] into the place of [targetId], the drag-to-reorder step: moving up
     * it lands just above the target, moving down just below. Layers between the two,
     * such as the effects marker the list hides, keep their order. Unknown ids: unchanged.
     */
    fun moveOnto(theme: Theme, id: String, targetId: String): Theme {
        val units = units(theme)
        val from = units.indexOfFirst { unitId(it) == id || (!isIcon(it) && it.single().id == id) }
        val to = units.indexOfFirst { unitId(it) == targetId || (!isIcon(it) && it.single().id == targetId) }
        if (from < 0 || to < 0 || from == to) return theme
        val l = units.toMutableList()
        l.add(to, l.removeAt(from))
        return theme.copy(layers = l.flatten())
    }

    /** The rows the layer list shows, top of the stack first. The effects marker is not one. */
    fun rows(theme: Theme): List<Row> =
        units(theme).asReversed().filter { it.single0() !is EffectsLayer }.map { Row(unitId(it), it) }

    private fun List<Layer>.single0(): Layer? = singleOrNull()

    /** A row shows while any of its layers does. */
    fun isVisible(theme: Theme, row: Row): Boolean = row.layers.any(::isVisible)

    /** A paint layer is hidden by its eye; an effect when switched off. Opacity plays no part. */
    fun isVisible(layer: Layer): Boolean = when (layer) {
        is PaintLayer -> layer.visible
        is EffectLayer -> layer.enabled
        is EffectsLayer -> true
    }

    /**
     * Shows or hides layer [id]: a paint layer's eye, an effect's switch. Opacity is never
     * touched, so a layer shown again, after a restart too, has the opacity it had.
     */
    fun withVisible(theme: Theme, id: String, visible: Boolean): Theme =
        if (id == ICON_ROW) rowLayers(theme, id).fold(theme) { t, l -> withVisible(t, l.id, visible) } else when (val l = layer(theme, id)) {
        is PaintLayer -> if (l.visible == visible) theme else replace(theme, l.copy(visible = visible))
        is EffectLayer -> if (l.enabled == visible) theme else replace(theme, l.copy(enabled = visible))
        else -> theme
    }

    /**
     * Removes a paint or effect layer. The effects marker stays, a stack holds exactly one,
     * and so do the base stack's own layers: the background and the icon only hide.
     */
    fun remove(theme: Theme, id: String): Theme =
        if (!canRemove(theme, id)) theme else theme.copy(layers = theme.layers.filterNot { it.id == id })

    fun canRemove(theme: Theme, id: String): Boolean {
        val l = layer(theme, id) ?: return false
        return l !is EffectsLayer && id != Layer.BACKGROUND_ID && id !in ICON_IDS
    }

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

    /** The name a row shows in the list and on its page. */
    fun rowLabel(theme: Theme, row: Row): String = if (row.isIcon) "Icon" else label(theme, row.first)

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
        is PaintLayer -> when (val source = layer.source) {
            is Paint.Solid -> "Colour"
            is Paint.LinearGradient, is Paint.RadialGradient -> "Gradient"
            is Paint.Texture -> "Image"
            is Paint.Noise -> patternName(source.kind)
        }
        else -> "Effects"
    }

    fun effectName(effect: Effect): String = when (effect) {
        is Effect.Streaks -> "Streaks"
        is Effect.BrushStrokes -> "Brush strokes"
        is Effect.Bevel -> "Raised edge"
        is Effect.DropShadow -> "Shadow"
        is Effect.InnerShadow -> "Inner shadow"
        is Effect.OuterGlow -> "Glow"
        is Effect.InnerGlow -> "Inner glow"
        is Effect.Grain -> "Film grain"
    }

    fun patternName(kind: NoiseKind): String = when (kind) {
        NoiseKind.FBM -> "Clouds"
        NoiseKind.RIDGED -> "Veins"
        NoiseKind.GRAIN -> "Speckle"
    }

    /** Effects that draw only on the icon: below the Icon row they are hidden behind it. */
    fun drawsOnIconOnly(effect: Effect): Boolean = effect is Effect.Bevel || effect is Effect.InnerShadow || effect is Effect.InnerGlow

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

    private val ICON_IDS = setOf(Layer.ICON_FILL_ID, Layer.ICON_LINES_ID)

    private val NAMED = setOf(Layer.BACKGROUND_ID, Layer.EFFECTS_ID, Layer.ICON_FILL_ID, Layer.ICON_LINES_ID, BACKGROUND_TEXTURE_ID)
}
