package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BackgroundShape

private enum class ShapeKind { CIRCLE, SQUIRCLE, ROUNDED, TEARDROP, SQUARE }

/**
 * The background shape, spec 2.3: circle, squircle with its superellipse exponent,
 * rounded square with its corner radius, teardrop, or square. The exponent and radius
 * sliders show only for their shape.
 */
@Composable
fun ShapeControl(
    shape: BackgroundShape,
    default: BackgroundShape,
    onDrag: (BackgroundShape) -> Unit,
    onDone: () -> Unit,
    onCommit: (BackgroundShape) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            "Shape",
            listOf(
                ShapeKind.CIRCLE to "Circle", ShapeKind.SQUIRCLE to "Squircle", ShapeKind.ROUNDED to "Rounded square",
                ShapeKind.TEARDROP to "Teardrop", ShapeKind.SQUARE to "Square",
            ),
            selected = kindOf(shape),
            default = kindOf(default),
            onSelect = { k -> onCommit(if (k == kindOf(default)) default else make(k)) },
        )
        when (shape) {
            is BackgroundShape.Squircle -> SliderControl(
                "Squircle exponent", shape.exponent, 2f..12f, BackgroundShape.Squircle().exponent, decimals(1),
                { onDrag(BackgroundShape.Squircle(it)) }, onDone, { onCommit(BackgroundShape.Squircle()) },
            )
            is BackgroundShape.RoundedSquare -> SliderControl(
                "Corner radius", shape.cornerRadius, 0f..0.5f, BackgroundShape.RoundedSquare().cornerRadius, percent,
                { onDrag(BackgroundShape.RoundedSquare(it)) }, onDone, { onCommit(BackgroundShape.RoundedSquare()) },
            )
            else -> Unit
        }
    }
}

private fun kindOf(s: BackgroundShape) = when (s) {
    BackgroundShape.Circle -> ShapeKind.CIRCLE
    is BackgroundShape.Squircle -> ShapeKind.SQUIRCLE
    is BackgroundShape.RoundedSquare -> ShapeKind.ROUNDED
    BackgroundShape.Teardrop -> ShapeKind.TEARDROP
    BackgroundShape.Square -> ShapeKind.SQUARE
}

private fun make(k: ShapeKind): BackgroundShape = when (k) {
    ShapeKind.CIRCLE -> BackgroundShape.Circle
    ShapeKind.SQUIRCLE -> BackgroundShape.Squircle()
    ShapeKind.ROUNDED -> BackgroundShape.RoundedSquare()
    ShapeKind.TEARDROP -> BackgroundShape.Teardrop
    ShapeKind.SQUARE -> BackgroundShape.Square
}
