package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.Paint
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

class ThemeMigrationTest {
    private fun fixture(name: String) = javaClass.getResource("/themes/$name")!!.readText()

    @Test
    fun version0Migrates() {
        val doc = migrate(fixture("v0.json"), now = { 1_700_000_000_000 }, newId = { "fixed-id" })
        val expected = ThemeDocument(
            schemaVersion = 2,
            id = "fixed-id",
            name = "Old teal",
            created = 1_700_000_000_000,
            modified = 1_700_000_000_000,
            theme = Theme(
                shape = ShapeParams(strokeWeight = 1.5f, join = LineJoin.MITER),
                fill = FillParams(mode = FillMode.INVERT),
                background = BackgroundParams(
                    shape = BackgroundShape.Circle,
                ),
                layers = Theme.baseLayers(
                    Paint.LinearGradient(0f, 0f, 1f, 1f, listOf(ColorStop(0f, 0xFF1B3B36.toInt()), ColorStop(1f, 0xFF0E1A18.toInt()))),
                    body = 0xFF7FD1B9.toInt(), line = 0xFFFFFFFF.toInt(),
                ),
            ),
        )
        assertEquals(expected, doc)
    }

    /**
     * The schema 2 migration test: the M4 preset files, schema 1, migrate to the same
     * documents as the rewritten schema 2 presets, and render byte for byte the PNGs the M4
     * renderer made, the plain and jade goldens. Plain covers the transparent background
     * and outline, Jade the shaped background and invert fill.
     */
    @Test
    fun version1PresetsMigrateToTheBaseStackAndRenderTheM4Bytes() {
        val presets = File(requireNotNull(System.getProperty("presets.dir")))
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        for ((file, dir) in listOf("plain-arcticons.json" to "plain", "jade.json" to "jade")) {
            val v1 = fixture("v1-$file")
            assertTrue("\"schemaVersion\": 1" in v1)
            val migrated = migrate(v1, now = { error("no clock") }, newId = { error("no id") })
            assertEquals(migrate(File(presets, file).readText()), migrated, file)
            assertEquals(GoldenSet.PRESETS.getValue(dir), migrated.theme)
            val renderer = IconRenderer()
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    @Test
    fun version1MissingGlyphIsDamaged() {
        val v1 = fixture("v1-jade.json")
        val noGlyph = v1.substring(0, v1.indexOf(",\n        \"glyph\"")) + "\n    }\n}"
        assertFailsWith<ThemeFormatException> { migrate(noGlyph) }
    }

    @Test
    fun currentVersionPassesThrough() {
        val doc = ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = Theme())
        assertEquals(doc, migrate(doc.toJson(), now = { error("no clock") }, newId = { error("no id") }))
    }

    @Test
    fun unreadableFilesAreRejected() {
        val newer = ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = Theme()).toJson()
            .replace("\"schemaVersion\": 2", "\"schemaVersion\": 99")
        for (bad in listOf(newer, "not json", "[]", "{}", "{\"schemaVersion\": \"x\"}", "{\"title\": \"no theme\"}")) {
            assertFailsWith<ThemeFormatException>(bad) { migrate(bad) }
        }
    }
}
