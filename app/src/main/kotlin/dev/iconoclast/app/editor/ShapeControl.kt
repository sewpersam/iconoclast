package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BackgroundShape

enum class ShapeKind { CIRCLE, SQUIRCLE, ROUNDED, TEARDROP, SQUARE }

val SHAPE_NAMES: Map<ShapeKind, String> = linkedMapOf(
    ShapeKind.CIRCLE to "Circle", ShapeKind.SQUIRCLE to "Soft square", ShapeKind.ROUNDED to "Rounded square",
    ShapeKind.TEARDROP to "Teardrop", ShapeKind.SQUARE to "Square",
)

private val SHAPE_TILES: List<TileOption<ShapeKind>> = listOf(
    TileOption(ShapeKind.CIRCLE, SHAPE_NAMES.getValue(ShapeKind.CIRCLE)) { circle(it) },
    TileOption(ShapeKind.SQUIRCLE, SHAPE_NAMES.getValue(ShapeKind.SQUIRCLE)) { squircle(it) },
    TileOption(ShapeKind.ROUNDED, SHAPE_NAMES.getValue(ShapeKind.ROUNDED)) { rounded(it) },
    TileOption(ShapeKind.TEARDROP, SHAPE_NAMES.getValue(ShapeKind.TEARDROP)) { teardrop(it) },
    TileOption(ShapeKind.SQUARE, SHAPE_NAMES.getValue(ShapeKind.SQUARE)) { square(it) },
)

/**
 * The background shape, by pictures: circle, soft square with its squareness, rounded
 * square with its corner roundness, teardrop, or square. Each slider shows only for its
 * shape.
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
        TileChoice(Tips.SHAPE, SHAPE_TILES, shapeKindOf(shape), shapeKindOf(default)) { k ->
            if (k != shapeKindOf(shape)) onCommit(if (k == shapeKindOf(default)) default else make(k))
        }
        when (shape) {
            is BackgroundShape.Squircle -> SliderControl(
                Tips.SQUARENESS, shape.exponent, 2f..12f, BackgroundShape.Squircle().exponent, decimals(1),
                { onDrag(BackgroundShape.Squircle(it)) }, onDone, { onCommit(BackgroundShape.Squircle()) },
            )
            is BackgroundShape.RoundedSquare -> SliderControl(
                Tips.CORNER_ROUNDNESS, shape.cornerRadius, 0f..0.5f, BackgroundShape.RoundedSquare().cornerRadius, percent,
                { onDrag(BackgroundShape.RoundedSquare(it)) }, onDone, { onCommit(BackgroundShape.RoundedSquare()) },
            )
            else -> Unit
        }
    }
}

fun shapeKindOf(s: BackgroundShape) = when (s) {
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
