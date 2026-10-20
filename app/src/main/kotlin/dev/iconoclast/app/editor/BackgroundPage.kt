package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.Border
import dev.iconoclast.render.Layer
import dev.iconoclast.render.MIN_BACKGROUND_SIZE
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme

/**
 * The Background row's page, docs/naming-spec.md 1.3: on or off, the shape and its size,
 * the colour and the border width; opacity, mix mode and the border's colour under More
 * options. Size sits under the shape as Icon size sits on the Icon page. The colour is the stack's background layer. The border is painted at the
 * effects marker, but people see it as the background's edge, so it lives here. With no
 * border set the width reads 0, and the colour and opacity start from white at full.
 */
@Composable
fun BackgroundPage(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: TextureActions) {
    val b = theme.background
    val d = BackgroundParams()
    val border = b.border ?: NO_BORDER
    fun with(p: BackgroundParams) = theme.copy(background = p)
    fun withBorder(x: Border) = with(b.copy(border = x))
    val layer = LayerStack.layer(theme, Layer.BACKGROUND_ID) as? PaintLayer
    SwitchControl(Tips.SHOW_BACKGROUND, b.enabled, d.enabled) { commit(with(b.copy(enabled = it))) }
    if (!b.enabled) {
        Text(
            "No background. Icons sit straight on your wallpaper.", Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val more = rememberMore(Layer.BACKGROUND_ID)
    ShapeControl(b.shape, d.shape, { drag(with(b.copy(shape = it))) }, done, { commit(with(b.copy(shape = it))) })
    SliderControl(Tips.BACKGROUND_SIZE, b.size, MIN_BACKGROUND_SIZE..1f, d.size, percent,
        { drag(with(b.copy(size = it))) }, done, { commit(with(b.copy(size = d.size))) })
    Section("Colour")
    layer?.let {
        val dl = defaultsOf(it)
        fun withL(l: PaintLayer) = LayerStack.replace(theme, l)
        PaintControl(Tips.BACKGROUND_COLOUR, it, dl.source, { l -> drag(withL(l)) }, done, { l -> commit(withL(l)) }, textures = textures, more = more.value)
        FinishControls(it, theme, drag, done, commit, cut = false, more = more.value)
    }
    Section("Border")
    SliderControl(Tips.BORDER_WIDTH, border.width, 0f..0.1f, 0f, percent,
        { drag(withBorder(border.copy(width = it))) }, done, { commit(with(b.copy(border = d.border))) })
    MoreOptions(more) {
        layer?.let { BlendControl(Tips.BACKGROUND_MIX, it.blend, defaultsOf(it).blend) { b -> commit(LayerStack.replace(theme, it.copy(blend = b))) } }
        if (border.width > 0f) {
            ColorControl(Tips.BORDER_COLOUR, border.argb, NO_BORDER.argb,
                { drag(withBorder(border.copy(argb = it))) }, done, { commit(withBorder(border.copy(argb = it))) })
            SliderControl(Tips.BORDER_OPACITY, border.opacity, 0f..1f, NO_BORDER.opacity, percent,
                { drag(withBorder(border.copy(opacity = it))) }, done, { commit(withBorder(border.copy(opacity = NO_BORDER.opacity))) })
        }
    }
}

private val NO_BORDER = Border(0f, 0xFFFFFFFF.toInt(), 1f)
