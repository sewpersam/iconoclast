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
import androidx.compose.material3.TextButton
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
import dev.iconoclast.render.BevelStyle
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Breakup
import dev.iconoclast.render.Decay
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.util.Locale
import kotlin.random.Random

/**
 * Every setting of one effect layer, Task 9's contract: blend (not for the bevel, whose
 * highlight and shadow carry their own) and opacity, then the effect's own parameters at
 * their ranges, each with a reset to the model's default. Lengths are shown as a
 * percentage of the canvas.
 */
@Composable
fun EffectControls(layer: EffectLayer, theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, breakup: BreakupActions) {
    fun with(l: EffectLayer) = LayerStack.replace(theme, l)
    val e = layer.effect
    val dragE: (Effect) -> Unit = { drag(with(layer.copy(effect = it))) }
    val commitE: (Effect) -> Unit = { commit(with(layer.copy(effect = it))) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (e !is Effect.Bevel) {
            val d = if (e is Effect.Grain) BlendMode.OVERLAY else BlendMode.NORMAL
            BlendControl(layer.blend, d) { commit(with(layer.copy(blend = it))) }
        }
        SliderControl("Opacity", layer.opacity, 0f..1f, 1f, percent,
            { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = 1f))) })
        when (e) {
            is Effect.Streaks -> StreakControls(e, theme, layer.id, dragE, done, commitE, breakup)
            is Effect.Bevel -> BevelControls(e, dragE, done, commitE)
            is Effect.DropShadow -> {
                val d = Effect.DropShadow()
                ShadowControls(e.angle, e.distance, e.blur, e.color, d.angle, d.distance, d.blur, d.color,
                    { a, di, b, c -> dragE(Effect.DropShadow(a, di, b, c)) }, done, { a, di, b, c -> commitE(Effect.DropShadow(a, di, b, c)) })
            }
            is Effect.InnerShadow -> {
                val d = Effect.InnerShadow()
                ShadowControls(e.angle, e.distance, e.blur, e.color, d.angle, d.distance, d.blur, d.color,
                    { a, di, b, c -> dragE(Effect.InnerShadow(a, di, b, c)) }, done, { a, di, b, c -> commitE(Effect.InnerShadow(a, di, b, c)) })
            }
            is Effect.OuterGlow -> GlowControls(e.size, e.spread, e.color,
                { s, sp, c -> dragE(Effect.OuterGlow(s, sp, c)) }, done, { s, sp, c -> commitE(Effect.OuterGlow(s, sp, c)) })
            is Effect.InnerGlow -> GlowControls(e.size, e.spread, e.color,
                { s, sp, c -> dragE(Effect.InnerGlow(s, sp, c)) }, done, { s, sp, c -> commitE(Effect.InnerGlow(s, sp, c)) })
            is Effect.Grain -> GrainControls(e, dragE, done, commitE)
        }
    }
}

private val degrees = decimals(0, "°")

/** A fraction of the canvas as a percentage with one decimal: lengths and sizes. */
private val canvas: (Float) -> String = { String.format(Locale.ROOT, "%.1f%%", it * 100) }

@Composable
private fun StreakControls(
    e: Effect.Streaks, theme: Theme, layerId: String,
    onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit, actions: BreakupActions,
) {
    val d = Effect.Streaks()
    SliderControl("Angle", e.angle, 0f..360f, d.angle, degrees, { onDrag(e.copy(angle = it)) }, onDone, { onCommit(e.copy(angle = d.angle)) })
    SliderControl("Length", e.length, 0f..0.6f, d.length, canvas, { onDrag(e.copy(length = it)) }, onDone, { onCommit(e.copy(length = d.length)) })
    ChoiceControl("Decay", listOf(Decay.EXPONENTIAL to "Exponential", Decay.LINEAR to "Linear"), e.decay, d.decay, { onCommit(e.copy(decay = it)) })
    StreakPaint(e, theme, onDrag, onDone, onCommit)
    SwitchControl("Breakup", e.breakup != null, d.breakup != null) { onCommit(e.copy(breakup = if (it) Breakup() else null)) }
    val b = e.breakup ?: return
    val db = Breakup()
    fun dragB(x: Breakup) = onDrag(e.copy(breakup = x))
    fun commitB(x: Breakup) = onCommit(e.copy(breakup = x))
    BreakupPicker(b.texture, actions, layerId) { commitB(b.copy(texture = it)) }
    SliderControl("Breakup scale", b.scale, 0.25f..4f, db.scale, decimals(2, "×"), { dragB(b.copy(scale = it)) }, onDone, { commitB(b.copy(scale = db.scale)) })
    SliderControl("Stretch", b.stretch, 1f..32f, db.stretch, decimals(1, "×"), { dragB(b.copy(stretch = it)) }, onDone, { commitB(b.copy(stretch = db.stretch)) })
    SliderControl("Density", b.density, 0f..1f, db.density, percent, { dragB(b.copy(density = it)) }, onDone, { commitB(b.copy(density = db.density)) })
    SliderControl("Contrast", b.contrast, 0f..1f, db.contrast, percent, { dragB(b.copy(contrast = it)) }, onDone, { commitB(b.copy(contrast = db.contrast)) })
    if (b.texture == null) SeedControl("Breakup seed", b.seed, db.seed) { commitB(b.copy(seed = it)) }
}

/**
 * Where a streak's paint comes from: a texture or noise layer of the theme, by name, or a
 * colour. A link to a layer that is gone paints the colour, and says so.
 */
@Composable
private fun StreakPaint(e: Effect.Streaks, theme: Theme, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit) {
    val sources = LayerStack.streakSources(theme)
    val linked = theme.layers.firstOrNull { it.id == e.paintLayer } as? PaintLayer
    val name = when {
        linked != null -> LayerStack.label(theme, linked)
        e.paintLayer != null -> "Colour (linked layer gone)"
        else -> "Colour"
    }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Paint from", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "Paint from $name" }) { Text(name) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Colour") }, onClick = { open = false; onCommit(e.copy(paintLayer = null)) })
                for (l in sources) {
                    DropdownMenuItem(text = { Text(LayerStack.label(theme, l)) }, onClick = { open = false; onCommit(e.copy(paintLayer = l.id)) })
                }
            }
        }
    }
    if (linked == null) {
        ColorControl("Streak colour", e.color, Effect.Streaks().color, { onDrag(e.copy(color = it)) }, onDone, { onCommit(e.copy(color = it)) })
    }
}

@Composable
private fun BevelControls(e: Effect.Bevel, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit) {
    val d = Effect.Bevel()
    ChoiceControl("Style", listOf(BevelStyle.INNER to "Inner bevel", BevelStyle.EMBOSS to "Emboss"), e.style, d.style, { onCommit(e.copy(style = it)) })
    SliderControl("Light angle", e.lightAngle, 0f..360f, d.lightAngle, degrees,
        { onDrag(e.copy(lightAngle = it)) }, onDone, { onCommit(e.copy(lightAngle = d.lightAngle)) })
    SliderControl("Depth", e.depth, 0f..4f, d.depth, decimals(2), { onDrag(e.copy(depth = it)) }, onDone, { onCommit(e.copy(depth = d.depth)) })
    SliderControl("Size", e.size, 0.001f..0.05f, d.size, canvas, { onDrag(e.copy(size = it)) }, onDone, { onCommit(e.copy(size = d.size)) })
    SliderControl("Softness", e.softness, 0f..1f, d.softness, percent, { onDrag(e.copy(softness = it)) }, onDone, { onCommit(e.copy(softness = d.softness)) })
    ColorControl("Highlight", e.highlight, d.highlight, { onDrag(e.copy(highlight = it)) }, onDone, { onCommit(e.copy(highlight = it)) })
    BlendControl(e.highlightBlend, d.highlightBlend, "Highlight blend") { onCommit(e.copy(highlightBlend = it)) }
    ColorControl("Shadow", e.shadow, d.shadow, { onDrag(e.copy(shadow = it)) }, onDone, { onCommit(e.copy(shadow = it)) })
    BlendControl(e.shadowBlend, d.shadowBlend, "Shadow blend") { onCommit(e.copy(shadowBlend = it)) }
}

/** Drop and inner shadow share their parameters: light angle, distance, blur, colour. */
@Composable
private fun ShadowControls(
    angle: Float, distance: Float, blur: Float, color: Int,
    dAngle: Float, dDistance: Float, dBlur: Float, dColor: Int,
    onDrag: (Float, Float, Float, Int) -> Unit, onDone: () -> Unit, onCommit: (Float, Float, Float, Int) -> Unit,
) {
    SliderControl("Light angle", angle, 0f..360f, dAngle, degrees,
        { onDrag(it, distance, blur, color) }, onDone, { onCommit(dAngle, distance, blur, color) })
    SliderControl("Distance", distance, 0f..0.1f, dDistance, canvas,
        { onDrag(angle, it, blur, color) }, onDone, { onCommit(angle, dDistance, blur, color) })
    SliderControl("Blur", blur, 0f..0.1f, dBlur, canvas, { onDrag(angle, distance, it, color) }, onDone, { onCommit(angle, distance, dBlur, color) })
    ColorControl("Shadow colour", color, dColor, { onDrag(angle, distance, blur, it) }, onDone, { onCommit(angle, distance, blur, it) })
}

/** Outer and inner glow share their parameters: size, spread, colour. */
@Composable
private fun GlowControls(
    size: Float, spread: Float, color: Int,
    onDrag: (Float, Float, Int) -> Unit, onDone: () -> Unit, onCommit: (Float, Float, Int) -> Unit,
) {
    val d = Effect.OuterGlow()
    SliderControl("Size", size, 0.001f..0.1f, d.size, canvas, { onDrag(it, spread, color) }, onDone, { onCommit(d.size, spread, color) })
    SliderControl("Spread", spread, 0f..1f, d.spread, percent, { onDrag(size, it, color) }, onDone, { onCommit(size, d.spread, color) })
    ColorControl("Glow colour", color, d.color, { onDrag(size, spread, it) }, onDone, { onCommit(size, spread, it) })
}

@Composable
private fun GrainControls(e: Effect.Grain, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit) {
    val d = Effect.Grain()
    SliderControl("Amount", e.amount, 0f..1f, d.amount, percent, { onDrag(e.copy(amount = it)) }, onDone, { onCommit(e.copy(amount = d.amount)) })
    SliderControl("Grain size", e.size, 1f..4f, d.size, decimals(1, " px"), { onDrag(e.copy(size = it)) }, onDone, { onCommit(e.copy(size = d.size)) })
    SeedControl("Grain seed", e.seed, d.seed) { onCommit(e.copy(seed = it)) }
}

/** A seed: shown, rerolled, or reset. Any 32-bit integer is valid. */
@Composable
private fun SeedControl(label: String, seed: Int, default: Int, onCommit: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(seed.toString(), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onCommit(Random.nextInt()) }) { Text("New seed") }
        ResetButton(label, enabled = seed != default) { onCommit(default) }
    }
}
