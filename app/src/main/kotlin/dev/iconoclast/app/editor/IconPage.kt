package dev.iconoclast.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
 * and the size, each with only what most people touch; the rest under More options.
 * What the style draws nothing with is hidden: no line colour in Solid shape, where the
 * thickness still shapes the solid, and no fill in Lines only. The colours are the icon lines and icon fill layers of the stack; their
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
        Panel("Lines (the outlines)", IconPart.LINES) {
            linesLayer?.let { IconPaint(it, theme, Tips.LINE_COLOUR, drag, done, commit, textures(it.id), more.value) }
            thickness()
            linesLayer?.let {
                Section("Finish")
                FinishControls(it, theme, drag, done, commit, cut = true, more = more.value)
            }
        }
    }
    if (body) {
        Panel("Fill (inside the shape)", IconPart.FILL) {
            fillLayer?.let { IconPaint(it, theme, Tips.FILL_COLOUR, drag, done, commit, textures(it.id), more.value) }
            if (!lines) thickness()
            fillLayer?.let {
                Section("Finish")
                FinishControls(it, theme, drag, done, commit, cut = true, more = more.value)
            }
        }
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
            if (lines) linesLayer?.let { BlendControl(Tips.LINES_MIX, it.blend, defaultsOf(it).blend) { b -> commit(LayerStack.replace(theme, it.copy(blend = b))) } }
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
            fillLayer?.let { BlendControl(Tips.FILL_MIX, it.blend, defaultsOf(it).blend) { b -> commit(LayerStack.replace(theme, it.copy(blend = b))) } }
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
    PaintControl(tip, layer, d.source, { drag(with(it)) }, done, { commit(with(it)) }, textures = textures, more = more)
}

/** Which part of the icon a panel edits, for its thumbnail. */
enum class IconPart { LINES, FILL }

/**
 * One part of the icon, phone feedback round 1: a big heading with a small picture of an
 * icon whose edited part is solid and the other faint, in its own bordered panel with
 * clear space around it, so lines and fill never read as one list.
 */
@Composable
private fun Panel(title: String, part: IconPart, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).border(1.dp, c.outline, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PartThumbnail(part)
            Text(title, Modifier.padding(start = 12.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        content()
    }
}

/** A circle drawn as the icon: the part being edited strong, the other faint and dashed. */
@Composable
fun PartThumbnail(part: IconPart) {
    val c = MaterialTheme.colorScheme
    Canvas(Modifier.size(40.dp).clearAndSetSemantics {}) {
        val r = size.minDimension / 2
        val centre = Offset(size.width / 2, size.height / 2)
        val ring = r * 0.72f
        val stroke = r * 0.22f
        if (part == IconPart.FILL) {
            drawCircle(c.onSurface, ring, centre)
            drawCircle(c.outlineVariant, ring, centre, style = Stroke(stroke * 0.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        } else {
            drawCircle(c.surfaceVariant, ring, centre)
            drawCircle(c.onSurface, ring, centre, style = Stroke(stroke))
        }
    }
}

/** A base layer's opacity, in view on its page: how see-through it is. */
@Composable
internal fun LayerOpacity(layer: PaintLayer, theme: Theme, tip: Tip, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val d = defaultsOf(layer)
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    SliderControl(tip, layer.opacity, 0f..1f, d.opacity, percent,
        { drag(with(layer.copy(opacity = it))) }, done, { commit(with(layer.copy(opacity = d.opacity))) })
}
