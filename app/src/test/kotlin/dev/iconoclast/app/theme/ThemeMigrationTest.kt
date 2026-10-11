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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer

class ThemeMigrationTest {
    private fun fixture(name: String) = javaClass.getResource("/themes/$name")!!.readText()

    @Test
    fun version0Migrates() {
        val doc = migrate(fixture("v0.json"), now = { 1_700_000_000_000 }, newId = { "fixed-id" })
        val expected = ThemeDocument(
            schemaVersion = ThemeDocument.CURRENT_SCHEMA,
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
     * documents as the schema 2 presets, kept as fixtures, and render byte for byte the PNGs
     * the M4 renderer made, the plain and jade goldens. Plain covers the transparent
     * background and outline, Jade the shaped background and invert fill. The shipped Jade
     * is the M6 one since Task 10, so the M4 Jade lives on only in these fixtures.
     */
    @Test
    fun version1PresetsMigrateToTheBaseStackAndRenderTheM4Bytes() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        for ((file, dir) in listOf("plain-arcticons.json" to "plain", "jade.json" to "jade")) {
            val v1 = fixture("v1-$file")
            assertTrue("\"schemaVersion\": 1" in v1)
            val migrated = migrate(v1, now = { error("no clock") }, newId = { error("no id") })
            assertEquals(migrate(fixture("v2-$file")), migrated, file)
            assertEquals(GoldenSet.PRESETS.getValue(dir), migrated.theme)
            val renderer = IconRenderer()
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    /**
     * The schema 3 migration test: the schema 2 preset files, kept as fixtures, migrate
     * unchanged but for the version, with no clock and no new id, and render the M4 preset
     * goldens byte for byte. Plain Arcticons still ships as it was, so its schema 3 file is
     * the migrated fixture.
     */
    @Test
    fun version2PresetsMigrateUnchanged() {
        val presets = File(requireNotNull(System.getProperty("presets.dir")))
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        for ((file, dir) in listOf("plain-arcticons.json" to "plain", "jade.json" to "jade")) {
            val v2 = fixture("v2-$file")
            assertTrue("\"schemaVersion\": 2" in v2)
            val migrated = migrate(v2, now = { error("no clock") }, newId = { error("no id") })
            assertEquals(ThemeDocument.CURRENT_SCHEMA, migrated.schemaVersion)
            if (file == "plain-arcticons.json") assertEquals(migrate(File(presets, file).readText()), migrated, file)
            assertEquals(withOverrides(v2), json(migrated.toJson()), "only the version changes, and no overrides")
            val renderer = IconRenderer()
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    /**
     * The schema 4 migration test: the shipped schema 3 starters, kept as fixtures, gain an
     * empty overrides map and nothing else, need no clock and no id, and render their
     * starter goldens byte for byte.
     */
    @Test
    fun version3StartersMigrateToAnEmptyOverrideMap() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        for ((file, dir) in listOf("jade.json" to "preset_jade", "neon.json" to "preset_neon")) {
            val v3 = fixture("v3-$file")
            assertTrue("\"schemaVersion\": 3" in v3)
            val migrated = migrate(v3, now = { error("no clock") }, newId = { error("no id") })
            assertEquals(4, migrated.schemaVersion)
            assertEquals(emptyMap(), migrated.overrides)
            assertEquals(withOverrides(v3), json(migrated.toJson()), file)
            assertEquals(GoldenSet.STARTERS.getValue(dir), migrated.theme)
            val renderer = IconRenderer()
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    @Test
    fun version3WithOverridesIsDamaged() {
        val v3 = fixture("v3-jade.json").trimEnd().removeSuffix("}") + ", \"overrides\": {}}"
        assertFailsWith<ThemeFormatException> { migrate(v3) }
    }

    private fun json(text: String) = ThemeDocument.JSON.parseToJsonElement(text)

    /** [old] at the current version with an empty overrides map: what migration must make of it. */
    private fun withOverrides(old: String): JsonObject {
        val o = json(old) as JsonObject
        return JsonObject(o + mapOf("schemaVersion" to JsonPrimitive(ThemeDocument.CURRENT_SCHEMA), "overrides" to JsonObject(emptyMap())))
    }

    @Test
    fun version2WithoutLayersIsDamaged() {
        val v2 = fixture("v2-jade.json")
        val noLayers = v2.substring(0, v2.indexOf(",\n        \"layers\"")) + "\n    }\n}"
        assertFailsWith<ThemeFormatException> { migrate(noLayers) }
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
            .replace("\"schemaVersion\": ${ThemeDocument.CURRENT_SCHEMA}", "\"schemaVersion\": 99")
        for (bad in listOf(newer, "not json", "[]", "{}", "{\"schemaVersion\": \"x\"}", "{\"title\": \"no theme\"}")) {
            assertFailsWith<ThemeFormatException>(bad) { migrate(bad) }
        }
    }
}
