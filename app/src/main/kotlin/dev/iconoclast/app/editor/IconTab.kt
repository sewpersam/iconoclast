package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.DotMode
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.Layer
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme

/**
 * Everything about the icon itself on one tab, spec 2.3 Shape and Fill: its colours
 * first, then its lines, its fill, then its size and place. The colours are the base
 * stack's icon lines and icon fill layers; their other settings, blend, opacity and
 * textures, open in the Layers tab through [openLayer]. Controls a mode does not read
 * stay hidden: the miter limit for other joins, the fill settings for Outline.
 */
@Composable
fun IconTab(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, openLayer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Section("Colour")
        IconPaint(theme, Layer.ICON_LINES_ID, "Line colour", drag, done, commit, openLayer)
        if (theme.fill.mode != FillMode.OUTLINE) IconPaint(theme, Layer.ICON_FILL_ID, "Fill colour", drag, done, commit, openLayer)
        Section("Lines")
        LineControls(theme, drag, done, commit)
        Section("Fill")
        FillControls(theme, drag, done, commit)
        Section("Size and position")
        PlaceControls(theme, drag, done, commit)
    }
}

@Composable
internal fun Section(title: String) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** A solid colour gets swatches and the colour sliders; a gradient its own controls; a texture a way to the layer. */
@Composable
private fun IconPaint(
    theme: Theme, id: String, label: String,
    drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, openLayer: (String) -> Unit,
) {
    val layer = LayerStack.layer(theme, id) as? PaintLayer ?: return
    val default = defaultsOf(layer).source
    fun with(p: Paint) = LayerStack.replace(theme, layer.copy(source = p))
    when (val p = layer.source) {
        is Paint.Solid -> ColorControl(label, p.argb, (default as? Paint.Solid)?.argb ?: WHITE,
            { drag(with(Paint.Solid(it))) }, done, { commit(with(Paint.Solid(it))) }, swatches = true)
        is Paint.LinearGradient, is Paint.RadialGradient -> {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            PaintControl(p, default, { drag(with(it)) }, done, { commit(with(it)) })
        }
        is Paint.Texture, is Paint.Noise -> Text(
            "$label: painted by a ${if (p is Paint.Texture) "texture" else "noise"}.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    TextButton(onClick = { openLayer(id) }) { Text("More ${label.lowercase()} options") }
}

@Composable
private fun LineControls(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val s = theme.shape
    val d = ShapeParams()
    fun with(f: ShapeParams) = theme.copy(shape = f)
    SliderControl("Line thickness", s.strokeWeight, 0.25f..4f, d.strokeWeight, decimals(2, " u"),
        { drag(with(s.copy(strokeWeight = it))) }, done, { commit(with(s.copy(strokeWeight = d.strokeWeight))) })
    ChoiceControl("Line ends", listOf(LineCap.ROUND to "Round", LineCap.BUTT to "Flat", LineCap.SQUARE to "Square"),
        s.cap, d.cap, { commit(with(s.copy(cap = it))) })
    ChoiceControl("Corners", listOf(LineJoin.ROUND to "Round", LineJoin.MITER to "Sharp", LineJoin.BEVEL to "Cut"),
        s.join, d.join, { commit(with(s.copy(join = it))) })
    if (s.join == LineJoin.MITER) {
        SliderControl("Sharp corner limit", s.miterLimit, 1f..10f, d.miterLimit, decimals(1),
            { drag(with(s.copy(miterLimit = it))) }, done, { commit(with(s.copy(miterLimit = d.miterLimit))) })
    }
    ChoiceControl("Dots", listOf(DotMode.SCALE_WITH_STROKE to "Grow with the lines", DotMode.KEEP_ORIGINAL to "Keep their size"),
        s.dots, d.dots, { commit(with(s.copy(dots = it))) })
}

/**
 * Gap width, fallback threshold and keep lone lines are read by invert fill only; gap
 * closing by invert fill and both silhouette modes, whose flood fill it seals too.
 */
@Composable
private fun FillControls(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val f = theme.fill
    val d = FillParams()
    fun with(p: FillParams) = theme.copy(fill = p)
    ChoiceControl(
        "Style",
        listOf(
            FillMode.OUTLINE to "Outline", FillMode.INVERT to "Invert fill",
            FillMode.SILHOUETTE to "Silhouette", FillMode.OUTLINE_SILHOUETTE to "Outline + Silhouette",
        ),
        f.mode, d.mode, { commit(with(f.copy(mode = it))) },
    )
    Text(FILL_HINTS.getValue(f.mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val invert = f.mode == FillMode.INVERT
    if (invert) {
        SliderControl("Gap around lines", f.gapWidth, 0f..4f, d.gapWidth, decimals(2, " u"),
            { drag(with(f.copy(gapWidth = it))) }, done, { commit(with(f.copy(gapWidth = d.gapWidth))) })
    }
    if (f.mode != FillMode.OUTLINE) {
        SliderControl("Close small gaps", f.gapClosing, 0f..2f, d.gapClosing, decimals(2, " u"),
            { drag(with(f.copy(gapClosing = it))) }, done, { commit(with(f.copy(gapClosing = d.gapClosing))) })
    }
    if (invert) {
        SliderControl("Use outline below", f.fallbackThreshold, 0f..0.3f, d.fallbackThreshold, percent,
            { drag(with(f.copy(fallbackThreshold = it))) }, done, { commit(with(f.copy(fallbackThreshold = d.fallbackThreshold))) })
        Text("Icons whose fill would cover less than this much of the outline stay as outlines.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchControl("Keep stray lines", f.keepLoneLines, d.keepLoneLines) { commit(with(f.copy(keepLoneLines = it))) }
    }
}

@Composable
private fun PlaceControls(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val s = theme.shape
    val d = ShapeParams()
    fun with(f: ShapeParams) = theme.copy(shape = f)
    SliderControl("Icon size", s.iconScale, 0.5f..1f, d.iconScale, percent,
        { drag(with(s.copy(iconScale = it))) }, done, { commit(with(s.copy(iconScale = d.iconScale))) })
    SliderControl("Move left or right", s.offsetX, -0.1f..0.1f, d.offsetX, signedPercent,
        { drag(with(s.copy(offsetX = it))) }, done, { commit(with(s.copy(offsetX = d.offsetX))) })
    SliderControl("Move up or down", s.offsetY, -0.1f..0.1f, d.offsetY, signedPercent,
        { drag(with(s.copy(offsetY = it))) }, done, { commit(with(s.copy(offsetY = d.offsetY))) })
}

private val FILL_HINTS = mapOf(
    FillMode.OUTLINE to "Just the lines.",
    FillMode.INVERT to "The shape is filled and the lines are cut out of it.",
    FillMode.SILHOUETTE to "Closed shapes and lines are filled solid.",
    FillMode.OUTLINE_SILHOUETTE to "A solid silhouette with the lines drawn over it.",
)

private const val WHITE = 0xFFFFFFFF.toInt()
