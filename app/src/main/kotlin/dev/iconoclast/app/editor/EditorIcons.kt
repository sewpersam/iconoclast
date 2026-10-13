package dev.iconoclast.app.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The editor's few icons, drawn here as 24 dp strokes so the app still ships no icon
 * library. Material's Icon tints them with the content colour.
 */
object EditorIcons {
    val Undo = icon("Undo") {
        moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
        moveTo(4f, 9f); lineTo(14.5f, 9f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14.5f, y1 = 20f)
        lineTo(11f, 20f)
    }
    val Redo = icon("Redo") {
        moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
        moveTo(20f, 9f); lineTo(9.5f, 9f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 9.5f, y1 = 20f)
        lineTo(13f, 20f)
    }
    val Visible = icon("Visible") { eye() }
    val Hidden = icon("Hidden") {
        eye()
        moveTo(4f, 4f); lineTo(20f, 20f)
    }
    val Remove = icon("Remove") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
    /** Two columns of three dots: grab here to drag. */
    val DragHandle = icon("Drag") {
        for (x in listOf(9f, 15f)) for (y in listOf(6f, 12f, 18f)) {
            moveTo(x - 0.5f, y); lineTo(x + 0.5f, y)
        }
    }

    private fun PathBuilder.eye() {
        moveTo(2f, 12f)
        curveTo(5f, 6f, 19f, 6f, 22f, 12f)
        curveTo(19f, 18f, 5f, 18f, 2f, 12f)
        close()
        moveTo(15f, 12f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9f, y1 = 12f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 15f, y1 = 12f)
    }

    private fun icon(name: String, width: Float = 2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = if (name == "Drag") 3f else width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()
}
