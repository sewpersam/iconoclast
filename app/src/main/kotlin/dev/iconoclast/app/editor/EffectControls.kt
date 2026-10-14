package dev.iconoclast.app.editor

import androidx.compose.runtime.Composable
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
 * An effect's page, docs/naming-spec.md 2.11: the few settings that define it and its
 * strength in view, the rest under More options with its mix mode (none for the raised
 * edge, whose two sides carry their own). Each setting resets to the model's default.
 * Lengths show as a percentage of the tile. Where it draws is its place in the list.
 */
@Composable
fun EffectPage(layer: EffectLayer, theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, breakup: BreakupActions) {
    fun with(l: EffectLayer) = LayerStack.replace(theme, l)
    val e = layer.effect
    val dragE: (Effect) -> Unit = { drag(with(layer.copy(effect = it))) }
    val commitE: (Effect) -> Unit = { commit(with(layer.copy(effect = it))) }
    val more = rememberMore(layer.id)
    val o = Open(more.value)
    when (e) {
        is Effect.Streaks -> StreakControls(e, theme, layer.id, dragE, done, commitE, breakup, o)
        is Effect.BrushStrokes -> BrushStrokeControls(e, theme, dragE, done, commitE, o)
        is Effect.Bevel -> BevelControls(e, dragE, done, commitE, o)
        is Effect.DropShadow -> {
            val d = Effect.DropShadow()
            ShadowControls(e.angle, e.distance, e.blur, e.color, d.angle, d.distance, d.blur, d.color,
                { a, di, b, c -> dragE(Effect.DropShadow(a, di, b, c)) }, done, { a, di, b, c -> commitE(Effect.DropShadow(a, di, b, c)) }, o)
        }
        is Effect.InnerShadow -> {
            val d = Effect.InnerShadow()
            ShadowControls(e.angle, e.distance, e.blur, e.color, d.angle, d.distance, d.blur, d.color,
                { a, di, b, c -> dragE(Effect.InnerShadow(a, di, b, c)) }, done, { a, di, b, c -> commitE(Effect.InnerShadow(a, di, b, c)) }, o)
        }
        is Effect.OuterGlow -> {
            val d = Effect.OuterGlow()
            GlowControls(e.size, e.spread, e.color, d.size, d.spread, d.color,
                { s, sp, c -> dragE(Effect.OuterGlow(s, sp, c)) }, done, { s, sp, c -> commitE(Effect.OuterGlow(s, sp, c)) }, o)
        }
        is Effect.InnerGlow -> {
            val d = Effect.InnerGlow()
            GlowControls(e.size, e.spread, e.color, d.size, d.spread, d.color,
                { s, sp, c -> dragE(Effect.InnerGlow(s, sp, c)) }, done, { s, sp, c -> commitE(Effect.InnerGlow(s, sp, c)) }, o)
        }
        is Effect.Grain -> GrainControls(e, dragE, done, commitE, o)
    }
    SliderControl(Tips.STRENGTH, layer.opacity, 0f..1f, 1f, percent,
        { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = 1f))) })
    MoreOptions(more) {
        o.rare.forEach { it() }
        if (e !is Effect.Bevel) {
            val d = if (e is Effect.Grain) BlendMode.OVERLAY else BlendMode.NORMAL
            BlendControl(Tips.EFFECT_MIX, layer.blend, d) { commit(with(layer.copy(blend = it))) }
        }
    }
}

/**
 * Collects an effect's rare settings while its page draws the visible ones, so they all
 * land under one More options row at the bottom of the page.
 */
private class Open(val showing: Boolean) {
    val rare = mutableListOf<@Composable () -> Unit>()
    fun more(block: @Composable () -> Unit) { rare += block }
}

private val degrees = decimals(0, "°")

/** A fraction of the tile as a percentage with one decimal: lengths and sizes. */
private val canvas: (Float) -> String = { String.format(Locale.ROOT, "%.1f%%", it * 100) }

@Composable
private fun StreakControls(
    e: Effect.Streaks, theme: Theme, layerId: String,
    onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit, actions: BreakupActions, o: Open,
) {
    val d = Effect.Streaks()
    SliderControl(Tips.STREAK_DIRECTION, e.angle, 0f..360f, d.angle, degrees, { onDrag(e.copy(angle = it)) }, onDone, { onCommit(e.copy(angle = d.angle)) })
    SliderControl(Tips.STREAK_LENGTH, e.length, 0f..0.6f, d.length, canvas, { onDrag(e.copy(length = it)) }, onDone, { onCommit(e.copy(length = d.length)) })
    val linked = theme.layers.firstOrNull { it.id == e.paintLayer } as? PaintLayer
    if (linked == null) {
        ColorControl(Tips.STREAK_COLOUR, e.color, d.color, { onDrag(e.copy(color = it)) }, onDone, { onCommit(e.copy(color = it)) })
    }
    SwitchControl(Tips.BROKEN_STREAKS, e.breakup != null, d.breakup != null) { onCommit(e.copy(breakup = if (it) Breakup() else null)) }
    val b = e.breakup
    val db = Breakup()
    fun dragB(x: Breakup) = onDrag(e.copy(breakup = x))
    fun commitB(x: Breakup) = onCommit(e.copy(breakup = x))
    if (b != null) {
        SliderControl(Tips.COVERAGE, b.density, 0f..1f, db.density, percent, { dragB(b.copy(density = it)) }, onDone, { commitB(b.copy(density = db.density)) })
    }
    o.more {
        ChoiceControl(Tips.FADE, listOf(Decay.EXPONENTIAL to "Long tail", Decay.LINEAR to "Even"), e.decay, d.decay, { onCommit(e.copy(decay = it)) })
        ColourFrom(Tips.STREAK_COLOUR_FROM, e.paintLayer, theme) { onCommit(e.copy(paintLayer = it)) }
        if (b != null) {
            BreakupPicker(b.texture, actions, layerId) { commitB(b.copy(texture = it)) }
            SliderControl(Tips.MARK_SIZE, b.scale, 0.25f..4f, db.scale, decimals(2, "×"), { dragB(b.copy(scale = it)) }, onDone, { commitB(b.copy(scale = db.scale)) })
            SliderControl(Tips.MARK_LENGTH, b.stretch, 1f..32f, db.stretch, decimals(1, "×"), { dragB(b.copy(stretch = it)) }, onDone, { commitB(b.copy(stretch = db.stretch)) })
            SliderControl(Tips.MARK_EDGES, b.contrast, 0f..1f, db.contrast, percent, { dragB(b.copy(contrast = it)) }, onDone, { commitB(b.copy(contrast = db.contrast)) })
            if (b.texture == null) ShuffleControl(Tips.SHUFFLE_MARKS, b.seed != db.seed, { commitB(b.copy(seed = Random.nextInt())) }, { commitB(b.copy(seed = db.seed)) })
        }
    }
}

/**
 * Where a streak's or a stroke's colour comes from: one colour, or an image or pattern
 * layer of the theme, by name. A link to a layer that is gone paints the colour, and says so.
 */
@Composable
private fun ColourFrom(tip: Tip, paintLayer: String?, theme: Theme, onSelect: (String?) -> Unit) {
    val sources = LayerStack.streakSources(theme)
    val options: List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "One colour") + sources.map { it.id to LayerStack.label(theme, it) }
    val linked = theme.layers.firstOrNull { it.id == paintLayer } as? PaintLayer
    val name: (String?) -> String = { id ->
        when {
            id == null -> "One colour"
            linked != null && id == linked.id -> LayerStack.label(theme, linked)
            else -> "One colour (layer gone)"
        }
    }
    DropdownControl(tip, options, paintLayer, null, onSelect, name)
}

/** Brush strokes: how many, how far and wide, their character, where they lean, and their colour. */
@Composable
private fun BrushStrokeControls(e: Effect.BrushStrokes, theme: Theme, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit, o: Open) {
    val d = Effect.BrushStrokes()
    SliderControl(Tips.STROKE_COUNT, e.count.toFloat(), 1f..400f, d.count.toFloat(), decimals(0),
        { onDrag(e.copy(count = (it + 0.5f).toInt().coerceIn(1, 400))) }, onDone, { onCommit(e.copy(count = d.count)) })
    SliderControl(Tips.STROKE_LENGTH, e.length, 0.02f..0.5f, d.length, canvas, { onDrag(e.copy(length = it)) }, onDone, { onCommit(e.copy(length = d.length)) })
    SliderControl(Tips.STROKE_WIDTH, e.width, 0.002f..0.08f, d.width, canvas, { onDrag(e.copy(width = it)) }, onDone, { onCommit(e.copy(width = d.width)) })
    SliderControl(Tips.BLUR_TIP, e.blur, 0f..1f, d.blur, percent, { onDrag(e.copy(blur = it)) }, onDone, { onCommit(e.copy(blur = d.blur)) })
    val linked = theme.layers.firstOrNull { it.id == e.paintLayer } as? PaintLayer
    if (linked == null) {
        ColorControl(Tips.STROKE_COLOUR, e.color, d.color, { onDrag(e.copy(color = it)) }, onDone, { onCommit(e.copy(color = it)) })
    }
    o.more {
        SliderControl(Tips.STROKE_SOFTNESS, e.softness, 0f..1f, d.softness, percent, { onDrag(e.copy(softness = it)) }, onDone, { onCommit(e.copy(softness = d.softness)) })
        SliderControl(Tips.PAINT_RUNS_OUT, e.fade, 0f..1f, d.fade, percent, { onDrag(e.copy(fade = it)) }, onDone, { onCommit(e.copy(fade = d.fade)) })
        SliderControl(Tips.DRY_BRUSH, e.bristles, 0f..1f, d.bristles, percent, { onDrag(e.copy(bristles = it)) }, onDone, { onCommit(e.copy(bristles = d.bristles)) })
        SliderControl(Tips.VARIETY, e.randomness, 0f..1f, d.randomness, percent,
            { onDrag(e.copy(randomness = it)) }, onDone, { onCommit(e.copy(randomness = d.randomness)) })
        SliderControl(Tips.CURL, e.bend, -1f..1f, d.bend, signedPercent, { onDrag(e.copy(bend = it)) }, onDone, { onCommit(e.copy(bend = d.bend)) })
        SliderControl(Tips.LEAN, e.drift, 0f..1f, d.drift, percent, { onDrag(e.copy(drift = it)) }, onDone, { onCommit(e.copy(drift = d.drift)) })
        if (e.drift > 0f) {
            SliderControl(Tips.LEAN_DIRECTION, e.angle, 0f..360f, d.angle, degrees, { onDrag(e.copy(angle = it)) }, onDone, { onCommit(e.copy(angle = d.angle)) })
        }
        ColourFrom(Tips.STROKE_COLOUR_FROM, e.paintLayer, theme) { onCommit(e.copy(paintLayer = it)) }
        ShuffleControl(Tips.SHUFFLE_STROKES, e.seed != d.seed, { onCommit(e.copy(seed = Random.nextInt())) }, { onCommit(e.copy(seed = d.seed)) })
    }
}

@Composable
private fun BevelControls(e: Effect.Bevel, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit, o: Open) {
    val d = Effect.Bevel()
    ChoiceControl(Tips.RAISED, listOf(BevelStyle.INNER to "Inside the edge", BevelStyle.EMBOSS to "Across the edge"), e.style, d.style, { onCommit(e.copy(style = it)) })
    SliderControl(Tips.RAISED_LIGHT, e.lightAngle, 0f..360f, d.lightAngle, degrees,
        { onDrag(e.copy(lightAngle = it)) }, onDone, { onCommit(e.copy(lightAngle = d.lightAngle)) })
    SliderControl(Tips.HEIGHT, e.depth, 0f..4f, d.depth, decimals(2), { onDrag(e.copy(depth = it)) }, onDone, { onCommit(e.copy(depth = d.depth)) })
    o.more {
        SliderControl(Tips.EDGE_WIDTH, e.size, 0.001f..0.05f, d.size, canvas, { onDrag(e.copy(size = it)) }, onDone, { onCommit(e.copy(size = d.size)) })
        SliderControl(Tips.ROUNDNESS, e.softness, 0f..1f, d.softness, percent, { onDrag(e.copy(softness = it)) }, onDone, { onCommit(e.copy(softness = d.softness)) })
        ColorControl(Tips.LIT_COLOUR, e.highlight, d.highlight, { onDrag(e.copy(highlight = it)) }, onDone, { onCommit(e.copy(highlight = it)) })
        BlendControl(Tips.LIT_MIX, e.highlightBlend, d.highlightBlend) { onCommit(e.copy(highlightBlend = it)) }
        ColorControl(Tips.SHADED_COLOUR, e.shadow, d.shadow, { onDrag(e.copy(shadow = it)) }, onDone, { onCommit(e.copy(shadow = it)) })
        BlendControl(Tips.SHADED_MIX, e.shadowBlend, d.shadowBlend) { onCommit(e.copy(shadowBlend = it)) }
    }
}

/** Shadow and inner shadow share their settings: light direction, distance, softness, colour. */
@Composable
private fun ShadowControls(
    angle: Float, distance: Float, blur: Float, color: Int,
    dAngle: Float, dDistance: Float, dBlur: Float, dColor: Int,
    onDrag: (Float, Float, Float, Int) -> Unit, onDone: () -> Unit, onCommit: (Float, Float, Float, Int) -> Unit, o: Open,
) {
    SliderControl(Tips.SHADOW_LIGHT, angle, 0f..360f, dAngle, degrees,
        { onDrag(it, distance, blur, color) }, onDone, { onCommit(dAngle, distance, blur, color) })
    SliderControl(Tips.DISTANCE, distance, 0f..0.1f, dDistance, canvas,
        { onDrag(angle, it, blur, color) }, onDone, { onCommit(angle, dDistance, blur, color) })
    SliderControl(Tips.SHADOW_SOFTNESS, blur, 0f..0.1f, dBlur, canvas, { onDrag(angle, distance, it, color) }, onDone, { onCommit(angle, distance, dBlur, color) })
    o.more {
        ColorControl(Tips.SHADOW_COLOUR, color, dColor, { onDrag(angle, distance, blur, it) }, onDone, { onCommit(angle, distance, blur, it) })
    }
}

/** Glow and inner glow share their settings: size, solidity, colour. */
@Composable
private fun GlowControls(
    size: Float, spread: Float, color: Int,
    dSize: Float, dSpread: Float, dColor: Int,
    onDrag: (Float, Float, Int) -> Unit, onDone: () -> Unit, onCommit: (Float, Float, Int) -> Unit, o: Open,
) {
    SliderControl(Tips.GLOW_SIZE, size, 0.001f..0.1f, dSize, canvas, { onDrag(it, spread, color) }, onDone, { onCommit(dSize, spread, color) })
    ColorControl(Tips.GLOW_COLOUR, color, dColor, { onDrag(size, spread, it) }, onDone, { onCommit(size, spread, it) })
    o.more {
        SliderControl(Tips.GLOW_SOLIDITY, spread, 0f..1f, dSpread, percent, { onDrag(size, it, color) }, onDone, { onCommit(size, dSpread, color) })
    }
}

/**
 * Film grain. Its cost, measured by PhoneBudgetTest.applyOf300IconsGrain: about 330 KB per
 * icon at full amount, docs/verification/m6.md. The Amount help says so in words.
 */
@Composable
private fun GrainControls(e: Effect.Grain, onDrag: (Effect) -> Unit, onDone: () -> Unit, onCommit: (Effect) -> Unit, o: Open) {
    val d = Effect.Grain()
    SliderControl(Tips.GRAIN_AMOUNT, e.amount, 0f..1f, d.amount, percent, { onDrag(e.copy(amount = it)) }, onDone, { onCommit(e.copy(amount = d.amount)) })
    o.more {
        SliderControl(Tips.GRAIN_SIZE, e.size, 1f..4f, d.size, decimals(1), { onDrag(e.copy(size = it)) }, onDone, { onCommit(e.copy(size = d.size)) })
        ShuffleControl(Tips.SHUFFLE_GRAIN, e.seed != d.seed, { onCommit(e.copy(seed = Random.nextInt())) }, { onCommit(e.copy(seed = d.seed)) })
    }
}
