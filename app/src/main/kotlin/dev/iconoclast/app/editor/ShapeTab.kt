package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.DotMode
import dev.iconoclast.render.LineCap
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme

/** Spec 2.3, Shape. Miter limit shows only for miter joins. */
@Composable
fun ShapeTab(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val s = theme.shape
    val d = ShapeParams()
    fun with(f: ShapeParams) = theme.copy(shape = f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SliderControl("Stroke weight", s.strokeWeight, 0.25f..4f, d.strokeWeight, decimals(2, " u"),
            { drag(with(s.copy(strokeWeight = it))) }, done, { commit(with(s.copy(strokeWeight = d.strokeWeight))) })
        ChoiceControl("Line caps", listOf(LineCap.BUTT to "Butt", LineCap.ROUND to "Round", LineCap.SQUARE to "Square"),
            s.cap, d.cap, { commit(with(s.copy(cap = it))) })
        ChoiceControl("Line joins", listOf(LineJoin.MITER to "Miter", LineJoin.ROUND to "Round", LineJoin.BEVEL to "Bevel"),
            s.join, d.join, { commit(with(s.copy(join = it))) })
        if (s.join == LineJoin.MITER) {
            SliderControl("Miter limit", s.miterLimit, 1f..10f, d.miterLimit, decimals(1),
                { drag(with(s.copy(miterLimit = it))) }, done, { commit(with(s.copy(miterLimit = d.miterLimit))) })
        }
        SliderControl("Icon scale in safe zone", s.iconScale, 0.5f..1f, d.iconScale, percent,
            { drag(with(s.copy(iconScale = it))) }, done, { commit(with(s.copy(iconScale = d.iconScale))) })
        SliderControl("Offset X", s.offsetX, -0.1f..0.1f, d.offsetX, signedPercent,
            { drag(with(s.copy(offsetX = it))) }, done, { commit(with(s.copy(offsetX = d.offsetX))) })
        SliderControl("Offset Y", s.offsetY, -0.1f..0.1f, d.offsetY, signedPercent,
            { drag(with(s.copy(offsetY = it))) }, done, { commit(with(s.copy(offsetY = d.offsetY))) })
        ChoiceControl("Filled dots", listOf(DotMode.SCALE_WITH_STROKE to "Scale with stroke", DotMode.KEEP_ORIGINAL to "Keep original"),
            s.dots, d.dots, { commit(with(s.copy(dots = it))) })
    }
}
