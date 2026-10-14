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
    /** A bin: delete. */
    val Delete = icon("Delete") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
        moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
    }
    /** A circular arrow: back to the default. */
    val Reset = icon("Reset") {
        moveTo(3f, 4f); lineTo(3.5f, 9f); lineTo(8.5f, 8.5f)
        moveTo(3.5f, 9f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3.6f, y1 = 15f)
    }
    /** A circled i: what this page is for. */
    val Info = icon("Info") {
        moveTo(21f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 12f)
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.5f); lineTo(12f, 7.6f)
    }
    /** A downward chevron: opens a list or a section. */
    val Chevron = icon("Chevron") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    /** A left arrow: back. */
    val Back = icon("Back") { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    /** A plus: add. */
    val Add = icon("Add") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    /** A four-point sparkle: an effect. */
    val Sparkle = icon("Sparkle", width = 1.75f) {
        moveTo(12f, 3f)
        curveTo(12.8f, 8.5f, 15.5f, 11.2f, 21f, 12f)
        curveTo(15.5f, 12.8f, 12.8f, 15.5f, 12f, 21f)
        curveTo(11.2f, 15.5f, 8.5f, 12.8f, 3f, 12f)
        curveTo(8.5f, 11.2f, 11.2f, 8.5f, 12f, 3f)
        close()
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
