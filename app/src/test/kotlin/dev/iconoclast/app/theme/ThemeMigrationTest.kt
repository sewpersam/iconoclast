package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.LineJoin
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
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
import kotlinx.serialization.json.JsonArray
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
        for ((file, theme, dir) in listOf(
            Triple("jade.json", GoldenSet.JADE_M6, "jade_m6"),
            Triple("neon.json", GoldenSet.STARTERS.getValue("preset_neon"), "preset_neon"),
        )) {
            val v3 = fixture("v3-$file")
            assertTrue("\"schemaVersion\": 3" in v3)
            val migrated = migrate(v3, now = { error("no clock") }, newId = { error("no id") })
            assertEquals(ThemeDocument.CURRENT_SCHEMA, migrated.schemaVersion)
            assertEquals(emptyMap(), migrated.overrides)
            assertEquals(withOverrides(v3), json(migrated.toJson()), file)
            assertEquals(theme, migrated.theme)
            val renderer = IconRenderer()
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    /**
     * The schema 5 migration test: the M6 Jade at schema 4, its streaks the effect now
     * called Scanlines, changes only its version, needs no clock and no id, and renders the
     * M6 Jade goldens byte for byte: old themes look exactly as they did.
     */
    @Test
    fun version4MigratesUnchangedAndRendersTheSame() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        val v4 = fixture("v4-jade.json")
        assertTrue("\"schemaVersion\": 4" in v4)
        val migrated = migrate(v4, now = { error("no clock") }, newId = { error("no id") })
        assertEquals(ThemeDocument.CURRENT_SCHEMA, migrated.schemaVersion)
        assertEquals(withOverrides(v4), json(migrated.toJson()), "only the version and the eyes change")
        assertEquals(GoldenSet.JADE_M6, migrated.theme)
        val renderer = IconRenderer()
        for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
            val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
            assertContentEquals(File(goldens, "jade_m6/$name.png").readBytes(), png, "jade_m6/$name")
        }
    }

    /**
     * The schema 6 migration test: the shipped Jade at schema 5, kept as a fixture, gains
     * `"visible": true` on each paint layer and nothing else, needs no clock and no id, keeps
     * every opacity, and renders the Jade starter goldens byte for byte.
     */
    @Test
    fun version5GainsVisibleLayersAndRendersTheSame() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        val v5 = fixture("v5-jade.json")
        assertTrue("\"schemaVersion\": 5" in v5)
        assertTrue("\"visible\"" !in v5)
        val migrated = migrate(v5, now = { error("no clock") }, newId = { error("no id") })
        assertEquals(6, migrated.schemaVersion)
        val old = json(v5) as JsonObject
        assertEquals(JsonObject(withVisible(old) + ("schemaVersion" to JsonPrimitive(6))), json(migrated.toJson()))
        assertTrue(migrated.theme.layers.filterIsInstance<PaintLayer>().all { it.visible })
        assertEquals(GoldenSet.STARTERS.getValue("preset_jade"), migrated.theme)
        val renderer = IconRenderer()
        for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
            val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
            assertContentEquals(File(goldens, "preset_jade/$name.png").readBytes(), png, "preset_jade/$name")
        }
    }

    @Test
    fun version5WithVisibleIsDamaged() {
        val v5 = fixture("v5-jade.json").replaceFirst("\"type\": \"paint\",", "\"type\": \"paint\", \"visible\": true,")
        assertTrue("\"visible\"" in v5)
        assertFailsWith<ThemeFormatException> { migrate(v5) }
    }

    @Test
    fun aHiddenLayerKeepsItsOpacityThroughASave() {
        val base = Theme()
        val hidden = base.copy(layers = base.layers + PaintLayer("x", Paint.Solid(-1), opacity = 0.3f, visible = false))
        val doc = ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = hidden)
        assertTrue("\"visible\": false" in doc.toJson())
        assertEquals(doc, migrate(doc.toJson(), now = { error("no clock") }, newId = { error("no id") }))
    }

    @Test
    fun version4WithBrushStrokesIsDamaged() {
        val current = ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = GoldenSet.STARTERS.getValue("preset_jade")).toJson()
        val v4 = current.replace("\"schemaVersion\": ${ThemeDocument.CURRENT_SCHEMA}", "\"schemaVersion\": 4")
        assertTrue("brushStrokes" in v4)
        assertFailsWith<ThemeFormatException> { migrate(v4) }
        assertEquals(GoldenSet.STARTERS.getValue("preset_jade"), migrate(current).theme)
    }

    @Test
    fun version3WithOverridesIsDamaged() {
        val v3 = fixture("v3-jade.json").trimEnd().removeSuffix("}") + ", \"overrides\": {}}"
        assertFailsWith<ThemeFormatException> { migrate(v3) }
    }

    private fun json(text: String) = ThemeDocument.JSON.parseToJsonElement(text)

    /**
     * [old] at the current version with an empty overrides map and every paint layer
     * visible: what migration must make of it.
     */
    private fun withOverrides(old: String): JsonObject {
        val o = json(old) as JsonObject
        return JsonObject(withVisible(o) + mapOf("schemaVersion" to JsonPrimitive(ThemeDocument.CURRENT_SCHEMA), "overrides" to JsonObject(emptyMap())))
    }

    /** [doc] with `"visible": true` on each paint layer, the schema 6 step. */
    private fun withVisible(doc: JsonObject): JsonObject {
        val theme = doc["theme"] as JsonObject
        val layers = (theme["layers"] as JsonArray).map { l ->
            l as JsonObject
            if ((l["type"] as JsonPrimitive).content == "paint") JsonObject(l + ("visible" to JsonPrimitive(true))) else l
        }
        return JsonObject(doc + ("theme" to JsonObject(theme + ("layers" to JsonArray(layers)))))
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
