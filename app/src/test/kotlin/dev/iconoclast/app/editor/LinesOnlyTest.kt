package dev.iconoclast.app.editor

import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Every layer hidden with the eye but Icon lines, on each preset. What shows is what the
 * lines layer paints alone: hidden layers leak nothing, through clips or linked effects.
 * In Outline the lines are the whole line art. In Invert fill they are only the lone
 * lines, the strokes that border no fill: the outlines there are the gaps in the fill.
 */
class LinesOnlyTest {
    private val dir = File(requireNotNull(System.getProperty("presets.dir")))
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val renderer = IconRenderer()

    private fun preset(name: String) = migrate(File(dir, name).readText(), now = { error("current version") }, newId = { error("fixed id") }).theme

    private fun linesOnly(theme: Theme): Theme = theme.layers
        .filter { (it is PaintLayer || it is EffectLayer) && it.id != Layer.ICON_LINES_ID }
        .fold(theme) { t, l -> LayerStack.withVisible(t, l.id, false) }

    private fun alpha(theme: Theme, sample: Int): Long {
        val px = renderer.render(GoldenSet.SAMPLES[sample], bundle.icon(sample), theme, SIZE).pixels
        return (3 until px.size step 4).sumOf { (px[it].toInt() and 0xFF).toLong() }
    }

    @Test
    fun hiddenLayersPaintNothingAtAll() {
        for (file in Presets.FILES) {
            val theme = preset(file)
            val hidden = linesOnly(theme)
            val removed = theme.copy(layers = theme.layers.filter { it !is PaintLayer && it !is EffectLayer || it.id == Layer.ICON_LINES_ID })
            for (s in SAMPLES) {
                val name = GoldenSet.SAMPLES[s]
                assertContentEquals(
                    renderer.render(name, bundle.icon(s), removed, SIZE).pixels,
                    renderer.render(name, bundle.icon(s), hidden, SIZE).pixels,
                    "$file, $name",
                )
            }
        }
    }

    @Test
    fun outlineShowsTheLineArtAndInvertOnlyItsLoneLines() {
        for (file in Presets.FILES) {
            val theme = linesOnly(preset(file))
            val outline = theme.copy(fill = theme.fill.copy(mode = FillMode.OUTLINE))
            for (s in SAMPLES) {
                val shown = alpha(theme, s)
                val all = alpha(outline, s)
                assertTrue(all > 0, "$file sample $s draws no line art")
                if (theme.fill.mode == FillMode.OUTLINE) assertTrue(shown == all, "$file sample $s")
                else assertTrue(shown < all / 2, "$file sample $s: invert lines-only shows $shown of $all")
            }
        }
    }

    private companion object {
        const val SIZE = 128
        val SAMPLES = 0 until 6
    }
}
