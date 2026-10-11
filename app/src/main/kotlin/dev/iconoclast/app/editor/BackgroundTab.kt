package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.Border
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme

/**
 * Spec 2.3, Background. The paint is the base stack's background layer. Its texture
 * option is a layer of its own, clipped to the background right above that paint: the
 * switch adds or removes it, through [textures], and its controls show here as in the
 * Layers tab. The shape is
 * baked into the PNG, so the tab says to turn off launcher masking if the two conflict. With no border set, the width reads 0 and the
 * colour and opacity start from white at full opacity. Resetting the width removes the
 * border, back to the default of none.
 */
@Composable
fun BackgroundTab(theme: Theme, drag: (Theme) -> Unit, done: () -> Unit, commit: (Theme) -> Unit, textures: TextureActions) {
    val b = theme.background
    val d = BackgroundParams()
    val border = b.border ?: NO_BORDER
    fun with(p: BackgroundParams) = theme.copy(background = p)
    fun withBorder(x: Border) = with(b.copy(border = x))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchControl("Background", b.enabled, d.enabled) { commit(with(b.copy(enabled = it))) }
        if (!b.enabled) {
            Text("Off: the icons have a transparent background.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Text(
            "The shape is drawn into each icon. If your launcher also masks icons to a shape, turn its icon shape off.",
            style = MaterialTheme.typography.bodySmall,
        )
        ShapeControl(b.shape, d.shape, { drag(with(b.copy(shape = it))) }, done, { commit(with(b.copy(shape = it))) })
        SliderControl("Size", b.size, 0.7f..1f, d.size, percent,
            { drag(with(b.copy(size = it))) }, done, { commit(with(b.copy(size = d.size))) })
        backgroundPaint(theme)?.let { paint ->
            PaintControl(paint, DEFAULT_PAINT, { drag(withBackgroundPaint(theme, it)) }, done, { commit(withBackgroundPaint(theme, it)) })
        }
        val texture = LayerStack.backgroundTexture(theme)
        SwitchControl("Texture layer", texture != null, false) { on ->
            if (on) textures.choose() else commit(LayerStack.remove(theme, LayerStack.BACKGROUND_TEXTURE_ID))
        }
        if (texture != null) {
            Column(Modifier.padding(start = 8.dp)) { LayerControls(texture, theme, drag, done, commit, textures) }
        }
        SliderControl("Border width", border.width, 0f..0.1f, 0f, percent,
            { drag(withBorder(border.copy(width = it))) }, done, { commit(with(b.copy(border = d.border))) })
        if (border.width > 0f) {
            ColorControl("Border colour", border.argb, NO_BORDER.argb,
                { drag(withBorder(border.copy(argb = it))) }, done, { commit(withBorder(border.copy(argb = it))) })
            SliderControl("Border opacity", border.opacity, 0f..1f, NO_BORDER.opacity, percent,
                { drag(withBorder(border.copy(opacity = it))) }, done, { commit(withBorder(border.copy(opacity = NO_BORDER.opacity))) })
        }
    }
}

private val NO_BORDER = Border(0f, 0xFFFFFFFF.toInt(), 1f)

/** The default background paint: the base stack's background layer source. */
private val DEFAULT_PAINT: Paint = (Theme().layers.first { it.id == Layer.BACKGROUND_ID } as PaintLayer).source

/**
 * The paint of the base stack's background layer, when it is a colour or gradient this tab
 * can edit. Texture and noise sources, or a removed background layer, belong to the Layers tab.
 */
private fun backgroundPaint(theme: Theme): Paint? =
    (theme.layers.firstOrNull { it.id == Layer.BACKGROUND_ID } as? PaintLayer)?.source
        ?.takeIf { it is Paint.Solid || it is Paint.LinearGradient || it is Paint.RadialGradient }

private fun withBackgroundPaint(theme: Theme, paint: Paint): Theme =
    theme.copy(layers = theme.layers.map { if (it.id == Layer.BACKGROUND_ID && it is PaintLayer) it.copy(source = paint) else it })
