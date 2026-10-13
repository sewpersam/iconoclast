package dev.iconoclast.app.theme

import dev.iconoclast.render.FillMode
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        assertEquals(listOf("preset-plain-arcticons", "preset-jade", "preset-neon", "preset-brush-a", "preset-brush-b", "preset-brush-c"), docs.map { it.id })
        assertEquals(listOf("Plain Arcticons", "Jade", "Neon", "Brush A", "Brush B", "Brush C"), docs.map { it.name })
        assertTrue(docs.all { it.schemaVersion == ThemeDocument.CURRENT_SCHEMA })
        for (doc in docs) {
            val png = IconRenderer().render(GoldenSet.SAMPLES[0], bundle.icon(0), doc.theme, 64)
            assertTrue(png.pixels.any { it != 0.toByte() }, "${doc.name} renders nothing")
        }
    }

    /** The golden set builds the presets in code; the files must say the same. */
    @Test
    fun presetFilesMatchTheGoldenSet() {
        assertEquals(GoldenSet.PRESETS.getValue("plain"), preset("plain-arcticons.json").theme)
        assertEquals(GoldenSet.STARTERS.getValue("preset_jade"), preset("jade.json").theme)
        assertEquals(GoldenSet.STARTERS.getValue("preset_neon"), preset("neon.json").theme)
        for (v in listOf("a", "b", "c")) assertEquals(GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_$v"), preset("brush-$v.json").theme, v)
    }

    /** Each preset file, as shipped, renders its golden set byte for byte: 24 samples, the brush variants 6. */
    @Test
    fun presetFilesRenderTheirGoldens() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val renderer = IconRenderer()
        val cases = listOf("plain-arcticons.json" to "plain", "jade.json" to "preset_jade", "neon.json" to "preset_neon").map { Triple(it.first, it.second, GoldenSet.SAMPLES) } +
            listOf("a", "b", "c").map { Triple("brush-$it.json", "preset_brush_$it", GoldenSet.LOOK_SAMPLES) }
        for ((file, dir, samples) in cases) {
            val theme = preset(file).theme
            for (name in samples) {
                val i = GoldenSet.SAMPLES.indexOf(name)
                val png = renderer.renderPng(name, bundle.icon(i), theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    @Test
    fun presetLooks() {
        val plain = preset("plain-arcticons.json").theme
        assertFalse(plain.background.enabled, "Plain Arcticons is white line on transparent")
        assertEquals(FillMode.OUTLINE, plain.fill.mode)
        assertEquals(dev.iconoclast.render.Paint.Solid(0xFFFFFFFF.toInt()), plain.layer(dev.iconoclast.render.Layer.ICON_LINES_ID).source)
        val jade = preset("jade.json").theme
        assertEquals(FillMode.INVERT, jade.fill.mode)
        assertTrue(jade.shape.strokeWeight in 1.5f..2f, "thicker icons")
        val strokes = jade.layers.filterIsInstance<dev.iconoclast.render.EffectLayer>().single()
        assertTrue(strokes.effect is dev.iconoclast.render.Effect.BrushStrokes && strokes.opacity < 1f, "translucent brush strokes are its only effect")
        val ids = jade.layers.map { it.id }
        assertTrue(ids.indexOf(Layer.BACKGROUND_ID) < ids.indexOf(strokes.id) && ids.indexOf(strokes.id) < ids.indexOf(Layer.ICON_FILL_ID),
            "strokes between background and icon")
        val link = (strokes.effect as dev.iconoclast.render.Effect.BrushStrokes).paintLayer
        assertEquals(Layer.ICON_FILL_ID, link, "strokes paint from the jade fill")
        val source = jade.layer(Layer.ICON_FILL_ID).source
        assertTrue(source is dev.iconoclast.render.Paint.Noise || source is dev.iconoclast.render.Paint.Texture, "the fill is a texture: $source")
        assertEquals(dev.iconoclast.render.BackgroundShape.RoundedSquare(0.2f), jade.background.shape)
        assertEquals(dev.iconoclast.render.Paint.Solid(0xFF000000.toInt()), jade.layer(dev.iconoclast.render.Layer.BACKGROUND_ID).source)
    }

    private fun dev.iconoclast.render.Theme.layer(id: String) = layers.first { it.id == id } as dev.iconoclast.render.PaintLayer
}
