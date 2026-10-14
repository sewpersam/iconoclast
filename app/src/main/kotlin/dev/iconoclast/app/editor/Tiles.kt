package dev.iconoclast.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.FillMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** One picture option: its value, its name, and how to draw it in a colour. */
class TileOption<T>(val value: T, val name: String, val draw: DrawScope.(Color) -> Unit)

/**
 * A choice made by pictures: each option a small drawing over its name, the picked one on
 * a solid tile. For choices that are easier seen than read, fill style and shape.
 */
@Composable
fun <T> TileChoice(tip: Tip, options: List<TileOption<T>>, selected: T, default: T, onSelect: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        LabelRow(tip, canReset = selected != default, onReset = { onSelect(default) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) {
                val picked = o.value == selected
                Column(
                    Modifier.weight(1f)
                        .background(if (picked) c.onSurface else c.surfaceVariant, RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onSelect(o.value) }
                        .semantics { this.selected = picked }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ink = if (picked) c.surface else c.onSurface
                    Canvas(Modifier.size(36.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { o.draw(this, ink) }
                    Text(
                        o.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall,
                        color = ink, textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
            }
        }
    }
}

/** A small house as the sample icon: a roof and walls, with a door that closes a shape. */
private fun house(): Path = Path().apply {
    moveTo(0.18f, 0.5f); lineTo(0.5f, 0.18f); lineTo(0.82f, 0.5f)
    moveTo(0.28f, 0.42f); lineTo(0.28f, 0.84f); lineTo(0.72f, 0.84f); lineTo(0.72f, 0.42f)
}

private fun houseBody(): Path = Path().apply {
    moveTo(0.5f, 0.18f); lineTo(0.82f, 0.5f); lineTo(0.72f, 0.5f); lineTo(0.72f, 0.84f)
    lineTo(0.28f, 0.84f); lineTo(0.28f, 0.5f); lineTo(0.18f, 0.5f); close()
}

private fun DrawScope.scaled(p: Path): Path = Path().apply { addPath(p); transform(androidx.compose.ui.graphics.Matrix().apply { scale(size.width, size.height) }) }

/** The four fill styles drawn on the house: lines, cut-out, solid, solid with lines. */
val FILL_STYLE_TILES: List<TileOption<FillMode>> = listOf(
    TileOption(FillMode.OUTLINE, "Lines only") { ink ->
        drawPath(scaled(house()), ink, style = Stroke(width = size.width * 0.07f))
    },
    TileOption(FillMode.INVERT, "Cut-out") { ink ->
        drawPath(scaled(houseBody()), ink)
        drawPath(scaled(house()), Color.Transparent, style = Stroke(width = size.width * 0.13f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
    },
    TileOption(FillMode.SILHOUETTE, "Solid shape") { ink ->
        drawPath(scaled(houseBody()), ink)
        drawPath(scaled(house()), ink, style = Stroke(width = size.width * 0.07f))
    },
    TileOption(FillMode.OUTLINE_SILHOUETTE, "Solid shape with lines") { ink ->
        drawPath(scaled(houseBody()), ink.copy(alpha = 0.4f))
        drawPath(scaled(house()), ink, style = Stroke(width = size.width * 0.07f))
    },
)

/** The background shapes drawn as their outline, filled. */
internal fun DrawScope.circle(ink: Color) = drawCircle(ink, size.minDimension * 0.44f)

internal fun DrawScope.square(ink: Color) {
    val s = size.minDimension * 0.82f
    drawRect(ink, Offset((size.width - s) / 2, (size.height - s) / 2), Size(s, s))
}

internal fun DrawScope.rounded(ink: Color) {
    val s = size.minDimension * 0.82f
    drawRoundRect(ink, Offset((size.width - s) / 2, (size.height - s) / 2), Size(s, s), CornerRadius(s * 0.22f))
}

internal fun DrawScope.squircle(ink: Color) {
    val r = size.minDimension * 0.42f
    val p = Path()
    for (i in 0..72) {
        val t = i / 72.0 * 2 * Math.PI
        val x = cos(t).let { sign(it) * abs(it).pow(2.0 / 4) }
        val y = sin(t).let { sign(it) * abs(it).pow(2.0 / 4) }
        val px = center.x + (x * r).toFloat()
        val py = center.y + (y * r).toFloat()
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    drawPath(p, ink)
}

internal fun DrawScope.teardrop(ink: Color) {
    val s = size.minDimension * 0.82f
    val o = Offset((size.width - s) / 2, (size.height - s) / 2)
    val big = s / 2
    val small = s * 0.15f
    val p = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                o.x, o.y, o.x + s, o.y + s,
                topLeftCornerRadius = CornerRadius(big), topRightCornerRadius = CornerRadius(big),
                bottomRightCornerRadius = CornerRadius(small), bottomLeftCornerRadius = CornerRadius(big),
            ),
        )
    }
    drawPath(p, ink)
}
