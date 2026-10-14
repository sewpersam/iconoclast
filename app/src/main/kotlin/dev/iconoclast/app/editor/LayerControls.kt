package dev.iconoclast.app.editor

import androidx.compose.runtime.Composable
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.util.Locale

/** The mix modes by their standard names, in spec 2.3's order. */
val BLEND_NAMES: Map<BlendMode, String> = linkedMapOf(
    BlendMode.NORMAL to "Normal", BlendMode.MULTIPLY to "Multiply", BlendMode.SCREEN to "Screen",
    BlendMode.OVERLAY to "Overlay", BlendMode.DARKEN to "Darken", BlendMode.LIGHTEN to "Lighten",
    BlendMode.COLOR_DODGE to "Color Dodge", BlendMode.COLOR_BURN to "Color Burn", BlendMode.HARD_LIGHT to "Hard Light",
    BlendMode.SOFT_LIGHT to "Soft Light", BlendMode.DIFFERENCE to "Difference", BlendMode.EXCLUSION to "Exclusion",
    BlendMode.HUE to "Hue", BlendMode.SATURATION to "Saturation", BlendMode.COLOR to "Color",
    BlendMode.LUMINOSITY to "Luminosity", BlendMode.ADD to "Add",
)

/** The parts a layer can be limited to. Effects below: what the enabled effects below the layer paint. */
val CLIP_NAMES: Map<ClipTarget, String> = linkedMapOf(
    ClipTarget.ICON to "Whole icon", ClipTarget.ICON_FILL to "Icon fill", ClipTarget.ICON_LINES to "Icon lines",
    ClipTarget.BACKGROUND to "Background", ClipTarget.CANVAS to "Everywhere", ClipTarget.EFFECTS to "Effects below",
)

fun clipName(clip: ClipTarget): String = CLIP_NAMES.getValue(clip)

/**
 * The page of an added colour, gradient, image or pattern layer, docs/naming-spec.md 1.3:
 * what it paints, how solid, and where, in view; an image's placement with its fill; the
 * brightness cut-out and the mix mode under More options, with the "Effects below" target.
 */
@Composable
fun LayerPage(layer: PaintLayer, theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: TextureActions) {
    val d = defaultsOf(layer)
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    val more = rememberMore(layer.id)
    PaintControl(Tips.FILL, layer, d.source, { drag(with(it)) }, done, { commit(with(it)) }, textures = textures, more = more.value)
    SliderControl(Tips.OPACITY, layer.opacity, 0f..1f, d.opacity, percent,
        { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = d.opacity))) })
    val targets = CLIP_NAMES.toList().filter { it.first != ClipTarget.EFFECTS || more.value || layer.clip == ClipTarget.EFFECTS }
    DropdownControl(Tips.SHOW_ON, targets, layer.clip, d.clip, { commit(with(LayerStack.withClip(layer, it))) })
    // Outside everything is nothing: Outside is greyed out for Everywhere.
    ChoiceControl(
        Tips.INSIDE_OUTSIDE, listOf(false to "Inside", true to "Outside"), layer.clipInverse, d.clipInverse,
        { commit(with(layer.copy(clipInverse = it))) },
        enabled = { !it || layer.clip != ClipTarget.CANVAS },
    )
    MoreOptions(more) {
        MatteControls(layer.matte, { drag(with(layer.copy(matte = it))) }, done, { commit(with(layer.copy(matte = it))) })
        BlendControl(Tips.MIX, layer.blend, d.blend) { commit(with(layer.copy(blend = it))) }
    }
}

/** [layer] painting [p]. A layer turning into an image starts at the new image placement, as a new one would. */
internal fun withSource(layer: PaintLayer, p: Paint): PaintLayer =
    if (p is Paint.Texture && layer.source !is Paint.Texture) LayerStack.withTexture(layer, p.sha256).copy(source = p) else layer.copy(source = p)

/** The base stack's own settings for its layers; a new layer's for the rest. */
internal fun defaultsOf(layer: PaintLayer): PaintLayer =
    Theme().layers.firstOrNull { it.id == layer.id } as? PaintLayer
        ?: if (layer.id == LayerStack.BACKGROUND_TEXTURE_ID) layer.copy(clip = ClipTarget.BACKGROUND, clipInverse = false,
            blend = BlendMode.NORMAL, opacity = 1f, mapping = LayerStack.NEW_TEXTURE_MAPPING, matte = null)
        else LayerStack.newLayer(Paint.Solid(0xFFFFFFFF.toInt())).copy(id = layer.id)

/** Levels keep black below white by at least [GAP], so a drag cannot make an invalid matte. */
@Composable
private fun MatteControls(m: Matte?, onDrag: (Matte?) -> Unit, onDone: () -> Unit, onCommit: (Matte?) -> Unit) {
    Section("Cut-out")
    SwitchControl(Tips.CUT_OUT, m != null, false) { onCommit(if (it) Matte() else null) }
    if (m == null) return
    val d = Matte()
    ChoiceControl(Tips.EDGES, listOf(MatteMode.LEVELS to "Soft", MatteMode.THRESHOLD to "Hard"), m.mode, d.mode, { onCommit(m.copy(mode = it)) })
    if (m.mode == MatteMode.LEVELS) {
        SliderControl(Tips.HIDDEN_BELOW, m.black, 0f..1f, d.black, percent,
            { onDrag(m.copy(black = it.coerceAtMost(m.white - GAP).coerceAtLeast(0f))) }, onDone,
            { onCommit(m.copy(black = d.black.coerceAtMost(m.white - GAP))) })
        SliderControl(Tips.SHOWN_ABOVE, m.white, 0f..1f, d.white, percent,
            { onDrag(m.copy(white = it.coerceAtLeast(m.black + GAP).coerceAtMost(1f))) }, onDone,
            { onCommit(m.copy(white = d.white)) })
    } else {
        SliderControl(Tips.CUT_OFF, m.threshold, 0f..1f, d.threshold, percent,
            { onDrag(m.copy(threshold = it)) }, onDone, { onCommit(m.copy(threshold = d.threshold)) })
    }
    ChoiceControl(Tips.KEEP, listOf(false to "Light parts", true to "Dark parts"), m.invert, d.invert, { onCommit(m.copy(invert = it)) })
    SliderControl(Tips.SOFTEN_EDGE, m.feather, 0f..0.05f, d.feather, { String.format(Locale.ROOT, "%.1f%%", it * 100) },
        { onDrag(m.copy(feather = it)) }, onDone, { onCommit(m.copy(feather = d.feather)) })
}

private const val GAP = 0.01f
