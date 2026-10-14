package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.io.File

/**
 * One row of the layer list: a drag handle, a swatch of what it paints, its name and a
 * plain summary, an eye to hide or show it, and delete when the row can go. Tap the row
 * to open its properties page. TalkBack users reorder through the row's move up and move
 * down actions, [onMoveUp] and [onMoveDown], which take no drag.
 */
@Composable
fun StackRow(
    theme: Theme,
    row: LayerStack.Row,
    label: String,
    summary: String,
    onOpen: () -> Unit,
    onVisible: (Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    file: (String) -> File,
    modifier: Modifier = Modifier,
    handle: Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val visible = LayerStack.isVisible(theme, row)
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background), verticalAlignment = Alignment.CenterVertically) {
        NamedIcon(Tips.REORDER.label) {
            Box(handle.size(40.dp, 56.dp).semantics { contentDescription = "Reorder $label" }, contentAlignment = Alignment.Center) {
                Icon(EditorIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Change $label", onClick = onOpen)
                .semantics {
                    val actions = listOfNotNull(
                        onMoveUp?.let { CustomAccessibilityAction("Move $label up") { it(); true } },
                        onMoveDown?.let { CustomAccessibilityAction("Move $label down") { it(); true } },
                    )
                    if (actions.isNotEmpty()) customActions = actions
                }
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp)
                .alpha(if (visible) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatch(swatchLayer(theme, row), file)
            Column(Modifier.padding(start = 12.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (summary.isNotEmpty()) Text(
                    summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NamedIcon(if (visible) "Hide" else "Show") {
            IconButton(
                onClick = { onVisible(!visible) },
                modifier = Modifier.semantics {
                    contentDescription = if (visible) "Hide $label" else "Show $label"
                    stateDescription = if (visible) "Shown" else "Hidden"
                },
            ) { Icon(if (visible) EditorIcons.Visible else EditorIcons.Hidden, contentDescription = null) }
        }
        if (onRemove != null) {
            NamedIcon(Tips.DELETE.label) {
                IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Delete $label" }) {
                    Icon(EditorIcons.Delete, contentDescription = null)
                }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/** The layer whose paint stands for the row: the Icon row shows its fill, or its lines in Lines only. */
private fun swatchLayer(theme: Theme, row: LayerStack.Row): Layer =
    if (!row.isIcon) row.first
    else row.layers.firstOrNull { it.id == (if (theme.fill.mode == FillMode.OUTLINE) Layer.ICON_LINES_ID else Layer.ICON_FILL_ID) } ?: row.first

/** What the layer paints, at a glance: its colour, gradient or image; a sparkle for an effect. */
@Composable
private fun Swatch(layer: Layer, file: (String) -> File) {
    val shape = RoundedCornerShape(8.dp)
    val m = Modifier.size(32.dp).border(1.dp, MaterialTheme.colorScheme.outline, shape)
    when (val source = (layer as? PaintLayer)?.source) {
        is Paint.Texture -> TextureThumbnail(source.sha256, file, null, 32.dp)
        is Paint.Solid -> Box(m.background(Color(source.argb), shape))
        is Paint.LinearGradient -> Box(m.background(Brush.horizontalGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        is Paint.RadialGradient -> Box(m.background(Brush.radialGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        is Paint.Noise -> Box(m.background(Brush.linearGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        null -> Box(m.background(MaterialTheme.colorScheme.surfaceVariant, shape), contentAlignment = Alignment.Center) {
            Icon(EditorIcons.Sparkle, contentDescription = null, Modifier.size(18.dp))
        }
    }
}

private fun List<Color>.atLeastTwo() = if (size >= 2) this else listOf(first(), first())

/** Where a clip target shows, in the words of the Show on choice, lower case. */
private val WHERE: Map<ClipTarget, String> = mapOf(
    ClipTarget.ICON to "whole icon", ClipTarget.ICON_FILL to "icon fill", ClipTarget.ICON_LINES to "icon lines",
    ClipTarget.BACKGROUND to "background", ClipTarget.EFFECTS to "effects below", ClipTarget.CANVAS to "everywhere",
)

/** The fill styles by their names. */
val FILL_STYLE_NAMES: Map<FillMode, String> = linkedMapOf(
    FillMode.OUTLINE to "Lines only", FillMode.INVERT to "Cut-out",
    FillMode.SILHOUETTE to "Solid shape", FillMode.OUTLINE_SILHOUETTE to "Solid shape with lines",
)

/**
 * A row's summary in plain words, defaults left out: "Hidden", or where it shows, then the
 * mix mode and opacity only when they are not Normal and 100%:
 * "Inside icon fill · Screen · 50%". The Icon row says its fill style, the Background its
 * shape. An effect says whether it is behind the icon or over it, and an effect that only
 * draws on the icon, sitting below it, says it is hidden.
 */
fun rowSummary(theme: Theme, row: LayerStack.Row): String {
    if (!LayerStack.isVisible(theme, row)) return "Hidden"
    if (row.isIcon) return FILL_STYLE_NAMES.getValue(theme.fill.mode)
    val layer = row.first
    if (layer.id == Layer.BACKGROUND_ID && !theme.background.enabled) return "Off"
    val parts = mutableListOf<String>()
    when (layer) {
        is PaintLayer -> {
            if (layer.id != Layer.BACKGROUND_ID) {
                val where = WHERE.getValue(layer.clip)
                parts += when {
                    layer.clip == ClipTarget.CANVAS -> "Everywhere"
                    layer.clipInverse -> "Outside $where"
                    else -> "Inside $where"
                }
                if (layer.matte != null) parts += "cut out by brightness"
            } else {
                parts += SHAPE_NAMES.getValue(shapeKindOf(theme.background.shape))
            }
        }
        is EffectLayer -> {
            val above = LayerStack.aboveIcon(theme, layer)
            if (!above && LayerStack.drawsOnIconOnly(layer.effect)) return "Hidden behind the icon"
            parts += if (above) "Over the icon" else "Behind the icon"
        }
        else -> Unit
    }
    val blend = (layer as? PaintLayer)?.blend ?: (layer as? EffectLayer)?.takeIf { it.effect !is Effect.Bevel }?.blend
    val usual = if (layer is EffectLayer && layer.effect is Effect.Grain) BlendMode.OVERLAY else BlendMode.NORMAL
    if (blend != null && blend != usual) parts += BLEND_NAMES.getValue(blend)
    val opacity = when (layer) {
        is PaintLayer -> layer.opacity
        is EffectLayer -> layer.opacity
        else -> 1f
    }
    if (opacity < 1f) parts += "${Math.round(opacity * 100)}%"
    return parts.joinToString(" · ")
}
