package dev.iconoclast.app.editor

import androidx.compose.runtime.Composable
import dev.iconoclast.render.DotMode
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.Layer
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.MAX_ICON_SCALE
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.SAFE_ZONE
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme

/**
 * The Icon row's page, docs/naming-spec.md 1.3: the fill style, then the lines, the fill
 * and the size, each with only what most people touch; the rest under More options. A
 * section the style draws nothing for is hidden: no lines in Solid shape, no fill in
 * Lines only. The colours are the icon lines and icon fill layers of the stack; their
 * opacity and mix mode live here too, so the icon has one page.
 */
@Composable
fun IconPage(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: (String) -> TextureActions) {
    val s = theme.shape
    val ds = ShapeParams()
    val f = theme.fill
    val df = FillParams()
    fun shape(x: ShapeParams) = theme.copy(shape = x)
    fun fill(x: FillParams) = theme.copy(fill = x)
    val lines = f.mode != FillMode.SILHOUETTE
    val body = f.mode != FillMode.OUTLINE
    val cutOut = f.mode == FillMode.INVERT
    val linesLayer = LayerStack.layer(theme, Layer.ICON_LINES_ID) as? PaintLayer
    val fillLayer = LayerStack.layer(theme, Layer.ICON_FILL_ID) as? PaintLayer
    val more = rememberMore(LayerStack.ICON_ROW)

    TileChoice(Tips.FILL_STYLE, FILL_STYLE_TILES, f.mode, df.mode) { commit(fill(f.copy(mode = it))) }

    // Thickness shapes the solid too, so it stays when the lines themselves draw nothing.
    val thickness = @Composable {
        SliderControl(Tips.LINE_THICKNESS, s.strokeWeight, 0.25f..4f, ds.strokeWeight, times,
            { drag(shape(s.copy(strokeWeight = it))) }, done, { commit(shape(s.copy(strokeWeight = ds.strokeWeight))) })
    }
    if (lines) {
        Section("Lines")
        linesLayer?.let { IconPaint(it, theme, Tips.LINE_COLOUR, drag, done, commit, textures(it.id), more.value) }
        thickness()
    }
    if (body) {
        Section("Fill")
        fillLayer?.let { IconPaint(it, theme, Tips.FILL_COLOUR, drag, done, commit, textures(it.id), more.value) }
        if (!lines) thickness()
    }
    Section("Size and position")
    SliderControl(Tips.ICON_SIZE, s.iconScale, 0.5f..MAX_ICON_SCALE, ds.iconScale, iconSize,
        { drag(shape(s.copy(iconScale = it))) }, done, { commit(shape(s.copy(iconScale = ds.iconScale))) })

    MoreOptions(more) {
        Section(if (lines) "Lines" else "Outline")
        run {
            ChoiceControl(Tips.LINE_ENDS, listOf(LineCap.ROUND to "Round", LineCap.BUTT to "Flat", LineCap.SQUARE to "Square"),
                s.cap, ds.cap, { commit(shape(s.copy(cap = it))) })
            ChoiceControl(Tips.CORNERS, listOf(LineJoin.ROUND to "Round", LineJoin.MITER to "Pointed", LineJoin.BEVEL to "Flattened"),
                s.join, ds.join, { commit(shape(s.copy(join = it))) })
            if (s.join == LineJoin.MITER) {
                SliderControl(Tips.SPIKE_LIMIT, s.miterLimit, 1f..10f, ds.miterLimit, decimals(1),
                    { drag(shape(s.copy(miterLimit = it))) }, done, { commit(shape(s.copy(miterLimit = ds.miterLimit))) })
            }
            ChoiceControl(Tips.DOT_SIZE, listOf(DotMode.SCALE_WITH_STROKE to "Match line thickness", DotMode.KEEP_ORIGINAL to "Keep original"),
                s.dots, ds.dots, { commit(shape(s.copy(dots = it))) })
            if (lines) linesLayer?.let { OpacityAndMix(it, theme, Tips.LINES_OPACITY, Tips.LINES_MIX, drag, done, commit) }
        }
        if (body) {
            Section("Fill")
            if (cutOut) {
                SliderControl(Tips.GAP_AROUND_LINES, f.gapWidth, 0f..4f, df.gapWidth, times,
                    { drag(fill(f.copy(gapWidth = it))) }, done, { commit(fill(f.copy(gapWidth = df.gapWidth))) })
            }
            SliderControl(Tips.CLOSE_SMALL_GAPS, f.gapClosing, 0f..2f, df.gapClosing, times,
                { drag(fill(f.copy(gapClosing = it))) }, done, { commit(fill(f.copy(gapClosing = df.gapClosing))) })
            if (cutOut) {
                SliderControl(Tips.LINES_ONLY_BELOW, f.fallbackThreshold, 0f..0.3f, df.fallbackThreshold, percent,
                    { drag(fill(f.copy(fallbackThreshold = it))) }, done, { commit(fill(f.copy(fallbackThreshold = df.fallbackThreshold))) })
                SwitchControl(Tips.KEEP_LOOSE_LINES, f.keepLoneLines, df.keepLoneLines) { commit(fill(f.copy(keepLoneLines = it))) }
            }
            fillLayer?.let { OpacityAndMix(it, theme, Tips.FILL_OPACITY, Tips.FILL_MIX, drag, done, commit) }
        }
        Section("Position")
        SliderControl(Tips.MOVE_X, s.offsetX, -0.1f..0.1f, ds.offsetX, signedPercent,
            { drag(shape(s.copy(offsetX = it))) }, done, { commit(shape(s.copy(offsetX = ds.offsetX))) })
        SliderControl(Tips.MOVE_Y, s.offsetY, -0.1f..0.1f, ds.offsetY, signedPercent,
            { drag(shape(s.copy(offsetY = it))) }, done, { commit(shape(s.copy(offsetY = ds.offsetY))) })
    }
}

/**
 * The icon size as the share of the tile the 48-unit drawing box spans: 100% reaches the
 * edges. The theme stores it against the launcher's safe zone, unchanged, so saved themes
 * draw exactly as before.
 */
val iconSize: (Float) -> String = { percent(it * SAFE_ZONE) }

@Composable
private fun IconPaint(
    layer: PaintLayer, theme: Theme, tip: Tip,
    drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: TextureActions, more: Boolean,
) {
    val d = defaultsOf(layer)
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    PaintControl(tip, layer.source, d.source, { drag(with(withSource(layer, it))) }, done, { commit(with(withSource(layer, it))) },
        memory = layer.id, textures = textures, more = more)
}

/** A base layer's opacity and mix mode, under its page's More options. */
@Composable
internal fun OpacityAndMix(layer: PaintLayer, theme: Theme, opacity: Tip, mix: Tip, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val d = defaultsOf(layer)
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    SliderControl(opacity, layer.opacity, 0f..1f, d.opacity, percent,
        { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = d.opacity))) })
    BlendControl(mix, layer.blend, d.blend) { commit(with(layer.copy(blend = it))) }
}
