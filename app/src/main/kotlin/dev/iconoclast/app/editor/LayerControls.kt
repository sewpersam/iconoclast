package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.util.Locale

/** Spec 2.3's blend modes by their Photoshop names, in the spec's order. */
val BLEND_NAMES: Map<BlendMode, String> = linkedMapOf(
    BlendMode.NORMAL to "Normal", BlendMode.MULTIPLY to "Multiply", BlendMode.SCREEN to "Screen",
    BlendMode.OVERLAY to "Overlay", BlendMode.DARKEN to "Darken", BlendMode.LIGHTEN to "Lighten",
    BlendMode.COLOR_DODGE to "Color Dodge", BlendMode.COLOR_BURN to "Color Burn", BlendMode.HARD_LIGHT to "Hard Light",
    BlendMode.SOFT_LIGHT to "Soft Light", BlendMode.DIFFERENCE to "Difference", BlendMode.EXCLUSION to "Exclusion",
    BlendMode.HUE to "Hue", BlendMode.SATURATION to "Saturation", BlendMode.COLOR to "Color",
    BlendMode.LUMINOSITY to "Luminosity", BlendMode.ADD to "Add",
)

val CLIP_NAMES: Map<ClipTarget, String> = linkedMapOf(
    ClipTarget.ICON to "Icon", ClipTarget.ICON_FILL to "Icon fill", ClipTarget.ICON_LINES to "Icon lines",
    ClipTarget.BACKGROUND to "Background", ClipTarget.EFFECTS to "Effects", ClipTarget.CANVAS to "Whole canvas",
)

/**
 * Every setting of one paint layer, spec 2.3 "Layers": source, clip and its inverse,
 * blend mode, opacity, texture mapping for textures and noise, and the mask adjustments.
 */
@Composable
fun LayerControls(layer: PaintLayer, theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: TextureActions) {
    val d = defaultsOf(layer)
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PaintControl(layer.source, d.source, { drag(with(layer.copy(source = it))) }, done, { commit(with(layer.copy(source = it))) }, textures)
        ChoiceControl("Clip to", CLIP_NAMES.toList(), layer.clip, d.clip, { commit(with(layer.copy(clip = it))) })
        SwitchControl("Inverse clip", layer.clipInverse, d.clipInverse) { commit(with(layer.copy(clipInverse = it))) }
        BlendControl(layer.blend, d.blend) { commit(with(layer.copy(blend = it))) }
        SliderControl("Opacity", layer.opacity, 0f..1f, d.opacity, percent,
            { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = d.opacity))) })
        if (layer.source is Paint.Texture || layer.source is Paint.Noise) {
            MappingControls(layer.mapping, { drag(with(layer.copy(mapping = it))) }, done, { commit(with(layer.copy(mapping = it))) })
        }
        MatteControls(layer.matte, { drag(with(layer.copy(matte = it))) }, done, { commit(with(layer.copy(matte = it))) })
    }
}

/** The base stack's own settings for its layers; a new layer's for the rest. */
private fun defaultsOf(layer: PaintLayer): PaintLayer =
    Theme().layers.firstOrNull { it.id == layer.id } as? PaintLayer
        ?: if (layer.id == LayerStack.BACKGROUND_TEXTURE_ID) layer.copy(clip = ClipTarget.BACKGROUND, clipInverse = false,
            blend = BlendMode.NORMAL, opacity = 1f, mapping = Mapping(), matte = null)
        else LayerStack.newLayer(Paint.Solid(0xFFFFFFFF.toInt())).copy(id = layer.id)

@Composable
private fun BlendControl(blend: BlendMode, default: BlendMode, onSelect: (BlendMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Blend mode", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "Blend mode ${BLEND_NAMES.getValue(blend)}" }) {
                Text(BLEND_NAMES.getValue(blend))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((mode, name) in BLEND_NAMES) DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(mode) })
            }
        }
        ResetButton("Blend mode", enabled = blend != default) { onSelect(default) }
    }
}

@Composable
private fun MappingControls(m: Mapping, onDrag: (Mapping) -> Unit, onDone: () -> Unit, onCommit: (Mapping) -> Unit) {
    val d = Mapping()
    ChoiceControl("Mapping", listOf(MappingMode.FIT to "Fit", MappingMode.FILL to "Fill", MappingMode.TILE to "Tile"),
        m.mode, d.mode, { onCommit(m.copy(mode = it)) })
    SliderControl("Scale", m.scale, 0.25f..4f, d.scale, decimals(2, "×"), { onDrag(m.copy(scale = it)) }, onDone, { onCommit(m.copy(scale = d.scale)) })
    SliderControl("Rotation", m.rotation, -180f..180f, d.rotation, decimals(0, "°"),
        { onDrag(m.copy(rotation = it)) }, onDone, { onCommit(m.copy(rotation = d.rotation)) })
    SwitchControl("Same patch on every icon", m.lockOffset, d.lockOffset) { onCommit(m.copy(lockOffset = it)) }
}

/** Levels keep black below white by at least [GAP], so a drag cannot make an invalid matte. */
@Composable
private fun MatteControls(m: Matte?, onDrag: (Matte?) -> Unit, onDone: () -> Unit, onCommit: (Matte?) -> Unit) {
    SwitchControl("Mask from luminance", m != null, false) { onCommit(if (it) Matte() else null) }
    if (m == null) return
    val d = Matte()
    ChoiceControl("Mask mode", listOf(MatteMode.LEVELS to "Levels", MatteMode.THRESHOLD to "Threshold"), m.mode, d.mode, { onCommit(m.copy(mode = it)) })
    if (m.mode == MatteMode.LEVELS) {
        SliderControl("Black point", m.black, 0f..1f, d.black, percent,
            { onDrag(m.copy(black = it.coerceAtMost(m.white - GAP).coerceAtLeast(0f))) }, onDone,
            { onCommit(m.copy(black = d.black.coerceAtMost(m.white - GAP))) })
        SliderControl("White point", m.white, 0f..1f, d.white, percent,
            { onDrag(m.copy(white = it.coerceAtLeast(m.black + GAP).coerceAtMost(1f))) }, onDone,
            { onCommit(m.copy(white = d.white)) })
    } else {
        SliderControl("Threshold", m.threshold, 0f..1f, d.threshold, percent,
            { onDrag(m.copy(threshold = it)) }, onDone, { onCommit(m.copy(threshold = d.threshold)) })
    }
    SwitchControl("Invert mask", m.invert, d.invert) { onCommit(m.copy(invert = it)) }
    SliderControl("Feather", m.feather, 0f..0.05f, d.feather, { String.format(Locale.ROOT, "%.1f%%", it * 100) },
        { onDrag(m.copy(feather = it)) }, onDone, { onCommit(m.copy(feather = d.feather)) })
}

private const val GAP = 0.01f
