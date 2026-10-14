package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.Adjust
import dev.iconoclast.render.Finish
import dev.iconoclast.render.Glass
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import java.util.Locale

/**
 * A fill's finish, schema 9, the same on every fill: how see-through it is (the layer's
 * opacity, shown the other way round), frost and grain in view, the light rim once set or
 * under [more], and the Glass button, a starting point that sets all four. [cut] adds
 * Cut through to wallpaper, offered on the icon's lines and fill.
 */
@Composable
fun FinishControls(layer: PaintLayer, theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, cut: Boolean, more: Boolean) {
    val d = defaultsOf(layer)
    val f = layer.finish
    val df = Finish()
    fun with(l: PaintLayer) = LayerStack.replace(theme, l)
    fun withF(x: Finish) = with(layer.copy(finish = x))
    SliderControl(Tips.SEE_THROUGH_FILL, 1f - layer.opacity, 0f..1f, 1f - d.opacity, percent,
        { drag(with(layer.copy(opacity = 1f - it))) }, done, { commit(with(layer.copy(opacity = d.opacity))) })
    SliderControl(Tips.FROST, f.frost, 0f..0.1f, df.frost, { String.format(Locale.ROOT, "%.0f%%", it * 1000) },
        { drag(withF(f.copy(frost = it))) }, done, { commit(withF(f.copy(frost = df.frost))) })
    SliderControl(Tips.FINISH_GRAIN, f.grain, 0f..1f, df.grain, percent,
        { drag(withF(f.copy(grain = it))) }, done, { commit(withF(f.copy(grain = df.grain))) })
    if (more || f.rim > 0f) {
        SliderControl(Tips.RIM, f.rim, 0f..1f, df.rim, percent,
            { drag(withF(f.copy(rim = it))) }, done, { commit(withF(f.copy(rim = df.rim))) })
    }
    if (cut) {
        SliderControl(Tips.CUT_THROUGH, f.cut, 0f..1f, df.cut, percent,
            { drag(withF(f.copy(cut = it))) }, done, { commit(withF(f.copy(cut = df.cut))) })
    }
    HelpAnchor(Tips.GLASS) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedButton(onClick = { commit(with(layer.copy(opacity = Glass.FILL_OPACITY, finish = Glass.FILL_FINISH.copy(cut = f.cut)))) }) {
                Text("Make it glass")
            }
        }
    }
}

/**
 * An image's adjustments, schema 9: brightness, contrast, saturation and warmth, -100 to
 * +100, each with its reset. Non-destructive: the image file never changes.
 */
@Composable
fun AdjustControls(a: Adjust?, onDrag: (Adjust?) -> Unit, onDone: () -> Unit, onCommit: (Adjust?) -> Unit) {
    val x = a ?: Adjust()
    fun tidy(v: Adjust): Adjust? = if (v.isIdentity) null else v
    val signed: (Float) -> String = { val p = Math.round(it * 100); if (p > 0) "+$p" else "$p" }
    Section("Adjust")
    SliderControl(Tips.IMG_BRIGHTNESS, x.brightness, -1f..1f, 0f, signed,
        { onDrag(tidy(x.copy(brightness = it))) }, onDone, { onCommit(tidy(x.copy(brightness = 0f))) })
    SliderControl(Tips.IMG_CONTRAST, x.contrast, -1f..1f, 0f, signed,
        { onDrag(tidy(x.copy(contrast = it))) }, onDone, { onCommit(tidy(x.copy(contrast = 0f))) })
    SliderControl(Tips.IMG_SATURATION, x.saturation, -1f..1f, 0f, signed,
        { onDrag(tidy(x.copy(saturation = it))) }, onDone, { onCommit(tidy(x.copy(saturation = 0f))) })
    SliderControl(Tips.IMG_WARMTH, x.warmth, -1f..1f, 0f, signed,
        { onDrag(tidy(x.copy(warmth = it))) }, onDone, { onCommit(tidy(x.copy(warmth = 0f))) })
}
