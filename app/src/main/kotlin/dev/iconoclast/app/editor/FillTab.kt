package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.Theme

/**
 * Spec 2.3, Fill. Each control shows only for the modes that read it: gap width,
 * fallback threshold and keep lone lines for invert fill; gap closing for invert fill and
 * both silhouette modes, whose flood fill it seals too. Outline reads none.
 */
@Composable
fun FillTab(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit) {
    val f = theme.fill
    val d = FillParams()
    fun with(p: FillParams) = theme.copy(fill = p)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChoiceControl(
            "Mode",
            listOf(
                FillMode.OUTLINE to "Outline", FillMode.INVERT to "Invert fill",
                FillMode.SILHOUETTE to "Silhouette", FillMode.OUTLINE_SILHOUETTE to "Outline + Silhouette",
            ),
            f.mode, d.mode, { commit(with(f.copy(mode = it))) },
        )
        val invert = f.mode == FillMode.INVERT
        if (invert) {
            SliderControl("Gap width", f.gapWidth, 0f..4f, d.gapWidth, decimals(2, " u"),
                { drag(with(f.copy(gapWidth = it))) }, done, { commit(with(f.copy(gapWidth = d.gapWidth))) })
        }
        if (f.mode != FillMode.OUTLINE) {
            SliderControl("Gap closing", f.gapClosing, 0f..2f, d.gapClosing, decimals(2, " u"),
                { drag(with(f.copy(gapClosing = it))) }, done, { commit(with(f.copy(gapClosing = d.gapClosing))) })
        }
        if (invert) {
            SliderControl("Fallback threshold", f.fallbackThreshold, 0f..0.3f, d.fallbackThreshold, percent,
                { drag(with(f.copy(fallbackThreshold = it))) }, done, { commit(with(f.copy(fallbackThreshold = d.fallbackThreshold))) })
            SwitchControl("Keep lone lines", f.keepLoneLines, d.keepLoneLines) { commit(with(f.copy(keepLoneLines = it))) }
        }
    }
}
