package dev.iconoclast.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.EffectsLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import java.io.File

/**
 * One row of the layer and effect lists: a drag handle when [handle] is given, a swatch of
 * what the layer paints, its name and a plain summary, an eye to hide or show it, and
 * remove. Tap the row to open its settings. TalkBack users reorder through the row's
 * move up and move down actions, [onMoveUp] and [onMoveDown], which take no drag.
 */
@Composable
fun StackRow(
    layer: Layer,
    label: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onVisible: (Boolean) -> Unit,
    onRemove: () -> Unit,
    file: (String) -> File,
    modifier: Modifier = Modifier,
    handle: Modifier? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val visible = LayerStack.isVisible(layer)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (handle != null) {
                Box(handle.size(40.dp, 56.dp), contentAlignment = Alignment.Center) {
                    Icon(EditorIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.weight(1f)
                    .clickable(role = Role.Button, onClickLabel = if (selected) "Close $label" else "Edit $label", onClick = onSelect)
                    .semantics {
                        this.selected = selected
                        val actions = listOfNotNull(
                            onMoveUp?.let { CustomAccessibilityAction("Move $label up") { it(); true } },
                            onMoveDown?.let { CustomAccessibilityAction("Move $label down") { it(); true } },
                        )
                        if (actions.isNotEmpty()) customActions = actions
                    }
                    .padding(start = if (handle == null) 12.dp else 0.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                    .alpha(if (visible) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(layer, file)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(
                onClick = { onVisible(!visible) },
                modifier = Modifier.semantics {
                    contentDescription = if (visible) "Hide $label" else "Show $label"
                    stateDescription = if (visible) "Shown" else "Hidden"
                },
            ) { Icon(if (visible) EditorIcons.Visible else EditorIcons.Hidden, contentDescription = null) }
            IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove $label" }) {
                Icon(EditorIcons.Remove, contentDescription = null)
            }
        }
    }
}

/** What the layer paints, at a glance: its colour, gradient or texture; "fx" for an effect. */
@Composable
private fun Swatch(layer: Layer, file: (String) -> File) {
    val shape = RoundedCornerShape(8.dp)
    val m = Modifier.size(32.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    val source = (layer as? PaintLayer)?.source
    when (source) {
        is Paint.Texture -> TextureThumbnail(source.sha256, file, null, 32.dp)
        is Paint.Solid -> Box(m.background(Color(source.argb), shape))
        is Paint.LinearGradient -> Box(m.background(Brush.horizontalGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        is Paint.RadialGradient -> Box(m.background(Brush.radialGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        is Paint.Noise -> Box(m.background(Brush.linearGradient(source.stops.map { Color(it.argb) }.atLeastTwo()), shape))
        null -> Box(m.background(MaterialTheme.colorScheme.tertiaryContainer, shape), contentAlignment = Alignment.Center) {
            Text("fx", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

private fun List<Color>.atLeastTwo() = if (size >= 2) this else listOf(first(), first())

/** Where a clip target shows, in words: "the icon fill". */
private val WHERE: Map<ClipTarget, String> = mapOf(
    ClipTarget.ICON to "the icon", ClipTarget.ICON_FILL to "the icon fill", ClipTarget.ICON_LINES to "the icon lines",
    ClipTarget.BACKGROUND to "the background", ClipTarget.EFFECTS to "the effects", ClipTarget.CANVAS to "everything",
)

/**
 * A row's summary in plain words, defaults left out: "Hidden", or where it shows, then the
 * blend and opacity only when they are not Normal and 100%. "On the icon fill, Screen
 * blend, 50% opacity". An effect says whether it is behind the icon or on it.
 */
fun layerSummary(layer: Layer, aboveIcon: Boolean): String {
    if (!LayerStack.isVisible(layer)) return "Hidden"
    val parts = mutableListOf<String>()
    when (layer) {
        is PaintLayer -> {
            val where = WHERE.getValue(layer.clip)
            parts += when {
                layer.clipInverse -> "Outside $where"
                layer.clip == ClipTarget.CANVAS -> "Everywhere"
                else -> "On $where"
            }
            if (layer.matte != null) parts += "masked by brightness"
        }
        is EffectLayer -> parts += if (aboveIcon) "On top of the icon" else "Behind the icon"
        is EffectsLayer -> return "Background border"
    }
    val blend = (layer as? PaintLayer)?.blend ?: (layer as? EffectLayer)?.takeIf { it.effect !is Effect.Bevel }?.blend
    if (blend != null && blend != BlendMode.NORMAL) parts += "${BLEND_NAMES.getValue(blend)} blend"
    val opacity = when (layer) {
        is PaintLayer -> layer.opacity
        is EffectLayer -> layer.opacity
        else -> 1f
    }
    if (opacity < 1f) parts += "${Math.round(opacity * 100)}% opacity"
    return parts.joinToString(", ")
}
