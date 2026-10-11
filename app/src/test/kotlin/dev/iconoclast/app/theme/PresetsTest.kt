package dev.iconoclast.app.theme

import dev.iconoclast.render.FillMode
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetsTest {
    private val dir = File(requireNotNull(System.getProperty("presets.dir")))
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))

    private fun preset(name: String) = migrate(File(dir, name).readText(), now = { error("current version") }, newId = { error("fixed id") })

    @Test
    fun everyPresetFileIsListed() {
        assertEquals(Presets.FILES.sorted(), dir.list()!!.sorted())
    }

    @Test
    fun presetsParseWithFixedIdsAndRender() {
        val docs = Presets.FILES.map(::preset)
        assertEquals(listOf("preset-plain-arcticons", "preset-jade"), docs.map { it.id })
        assertEquals(listOf("Plain Arcticons", "Jade"), docs.map { it.name })
        for (doc in docs) {
            val png = IconRenderer().render(GoldenSet.SAMPLES[0], bundle.icon(0), doc.theme, 64)
            assertTrue(png.pixels.any { it != 0.toByte() }, "${doc.name} renders nothing")
        }
    }

    /** The golden set builds the presets in code; the files must say the same. */
    @Test
    fun presetFilesMatchTheGoldenSet() {
        assertEquals(GoldenSet.PRESETS.getValue("plain"), preset("plain-arcticons.json").theme)
        assertEquals(GoldenSet.PRESETS.getValue("jade"), preset("jade.json").theme)
    }

    @Test
    fun presetLooks() {
        val plain = preset("plain-arcticons.json").theme
        assertFalse(plain.background.enabled, "Plain Arcticons is white line on transparent")
        assertEquals(FillMode.OUTLINE, plain.fill.mode)
        assertEquals(dev.iconoclast.render.Paint.Solid(0xFFFFFFFF.toInt()), plain.layer(dev.iconoclast.render.Layer.ICON_LINES_ID).source)
        val jade = preset("jade.json").theme
        assertEquals(FillMode.INVERT, jade.fill.mode)
        assertEquals(dev.iconoclast.render.BackgroundShape.RoundedSquare(0.2f), jade.background.shape)
        assertEquals(dev.iconoclast.render.Paint.Solid(0xFF000000.toInt()), jade.layer(dev.iconoclast.render.Layer.BACKGROUND_ID).source)
    }

    private fun dev.iconoclast.render.Theme.layer(id: String) = layers.first { it.id == id } as dev.iconoclast.render.PaintLayer
}
