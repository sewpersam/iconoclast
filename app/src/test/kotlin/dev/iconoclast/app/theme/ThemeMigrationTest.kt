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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
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
        assertEquals(ThemeDocument.CURRENT_SCHEMA, migrated.schemaVersion)
        val old = json(v5) as JsonObject
        assertEquals(JsonObject(iconTogether(withVisible(old)) + ("schemaVersion" to JsonPrimitive(ThemeDocument.CURRENT_SCHEMA))), json(migrated.toJson()))
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
        return JsonObject(iconTogether(withVisible(o)) + mapOf("schemaVersion" to JsonPrimitive(ThemeDocument.CURRENT_SCHEMA), "overrides" to JsonObject(emptyMap())))
    }

    /**
     * [doc] with its layers in schema 7's order, nothing between the icon's fill and lines,
     * and schema 8's mappings: lockOffset as randomShift, no random turn or zoom, no crop.
     */
    private fun iconTogether(doc: JsonObject): JsonObject {
        val theme = doc["theme"] as JsonObject
        val layers = JsonArray(iconTogether((theme["layers"] as JsonArray).toList()).map(::v8Mapping))
        // And schema 9's rewrite, tested on its own by version8RendersTheSameAsSchema9.
        return v8ToV9(JsonObject(doc + ("theme" to JsonObject(theme + ("layers" to layers)))), { 0L }, { "" })
    }

    /**
     * A schema 9 document as schema 8 wrote it, for themes schema 8 could hold: patterns
     * back to noise, no adjust, finish or stroke direction, version 8.
     */
    private fun v8Of(doc: JsonObject): JsonObject {
        val theme = doc["theme"] as JsonObject
        val layers = JsonArray((theme["layers"] as JsonArray).map { l ->
            val o = l as JsonObject
            when ((o["type"] as JsonPrimitive).content) {
                "paint" -> {
                    val src = o["source"] as JsonObject
                    val back = when ((src["type"] as JsonPrimitive).content) {
                        "texture" -> JsonObject(src - "adjust")
                        "pattern" -> {
                            val p = src["pattern"] as JsonObject
                            val (kind, octaves) = when ((p["type"] as JsonPrimitive).content) {
                                "noise" -> (if ((p["ridges"] as JsonPrimitive).content == "true") "RIDGED" else "FBM") to
                                    ((p["detail"] as JsonPrimitive).content.toDouble().toInt() + 1)
                                else -> "GRAIN" to 4
                            }
                            JsonObject(mapOf("type" to JsonPrimitive("noise"), "kind" to JsonPrimitive(kind), "octaves" to JsonPrimitive(octaves),
                                "seed" to src["seed"]!!, "stops" to src["stops"]!!))
                        }
                        else -> src
                    }
                    JsonObject(o - "finish" + ("source" to back))
                }
                "effect" -> {
                    val e = o["effect"] as JsonObject
                    if ((e["type"] as JsonPrimitive).content != "brushStrokes") o
                    else JsonObject(o + ("effect" to JsonObject(e - listOf("direction", "originX", "originY", "style"))))
                }
                else -> o
            }
        })
        return JsonObject(doc + mapOf("schemaVersion" to JsonPrimitive(8), "theme" to JsonObject(theme + ("layers" to layers))))
    }

    /**
     * The schema 9 migration test, items 7 and 8: every golden theme schema 8 could hold,
     * written as schema 8 (noise paints, explicit clips), migrates to patterns and, where
     * it is the same, clip to the layer below, and renders every golden byte for byte.
     */
    @Test
    fun version8RendersTheSameAsSchema9() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        val renderer = IconRenderer()
        renderer.textures.put(File(goldens, GoldenSet.TEXTURE_FILE).readBytes())
        renderer.textures.put(dev.iconoclast.render.Marble.PNG)
        var below = 0
        var patterns = 0
        val themes = HashMap<Theme, Theme>()
        for ((dir, theme, name) in GoldenSet.CASES) {
            if (dir.startsWith("fb_") || dir.startsWith("bg_")) continue
            // Below full size schema 11 keeps an old file's icon where it was, so it no
            // longer draws the golden, which is laid out the schema 11 way:
            // MigrationV10ToV11Test.belowFullSizeKeepsTheIcon covers those.
            if (theme.background.enabled && theme.background.size < 1f) continue
            val migrated = themes.getOrPut(theme) {
                val v8 = v8Of(json(ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = theme).toJson()) as JsonObject)
                assertTrue("\"pattern\"" !in v8.toString() && "\"finish\"" !in v8.toString(), dir)
                migrate(v8.toString(), now = { error("no clock") }, newId = { error("no id") }).theme.also { m ->
                    below += m.layers.count { it is PaintLayer && it.clip == dev.iconoclast.render.ClipTarget.BELOW }
                    patterns += m.layers.count { it is PaintLayer && it.source is Paint.Noise }
                }
            }
            val png = renderer.renderPng(name, bundle.icon(GoldenSet.SAMPLES.indexOf(name)), migrated, OUTPUT_SIZE)
            assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
        }
        assertTrue(below > 0, "some clips became BELOW")
        assertTrue(patterns > 0, "some noise became patterns")
    }

    private fun v8Mapping(layer: JsonElement): JsonElement {
        val l = layer as JsonObject
        val m = l["mapping"] as? JsonObject ?: return l
        val lock = (m["lockOffset"] as JsonPrimitive).content.toBoolean()
        return JsonObject(l + ("mapping" to JsonObject(m - "lockOffset" + mapOf(
            "randomShift" to JsonPrimitive(if (lock) 0.0 else 1.0), "randomRotation" to JsonPrimitive(0.0),
            "randomScale" to JsonPrimitive(0.0), "seed" to JsonPrimitive(0), "crop" to JsonNull,
        ))))
    }

    /** Schema 8's mapping as schema 7 wrote it: randomShift 0 or 1 back to lockOffset. */
    private fun v7Mapping(layer: JsonElement): JsonElement {
        val l = layer as JsonObject
        val m = l["mapping"] as? JsonObject ?: return l
        val shift = (m["randomShift"] as JsonPrimitive).content.toDouble()
        return JsonObject(l + ("mapping" to JsonObject(m - listOf("randomShift", "randomRotation", "randomScale", "seed", "crop") +
            ("lockOffset" to JsonPrimitive(shift == 0.0)))))
    }

    /**
     * The schema 8 migration test: every look golden that has textures and noise, written as
     * schema 7 with lockOffset, locked and unlocked, migrates to the same theme and renders
     * its goldens byte for byte.
     */
    @Test
    fun version7MappingsBecomeRandomShiftAndRenderTheSame() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        val renderer = IconRenderer()
        renderer.textures.put(File(goldens, GoldenSet.TEXTURE_FILE).readBytes())
        for ((dir, theme) in GoldenSet.LOOKS + GoldenSet.BLENDS) {
            val current = json(ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = theme).toJson()) as JsonObject
            val t = current["theme"] as JsonObject
            val v8 = v8Of(current)
            val t8 = v8["theme"] as JsonObject
            val v7 = JsonObject(v8 + mapOf("schemaVersion" to JsonPrimitive(7),
                "theme" to JsonObject(t8 + ("layers" to JsonArray((t8["layers"] as JsonArray).map(::v7Mapping))))))
            assertTrue("lockOffset" in v7.toString())
            val migrated = migrate(v7.toString(), now = { error("no clock") }, newId = { error("no id") })
            assertEquals(v10ToV11(v9ToV10(v8ToV9(v8, { 0L }, { "" }), { 0L }, { "" }), { 0L }, { "" }), json(migrated.toJson()), dir)
            val samples = if (dir in GoldenSet.LOOKS) GoldenSet.LOOK_SAMPLES else GoldenSet.BLEND_SAMPLES
            for (name in samples) {
                val png = renderer.renderPng(name, bundle.icon(GoldenSet.SAMPLES.indexOf(name)), migrated.theme, OUTPUT_SIZE)
                assertContentEquals(File(goldens, "$dir/$name.png").readBytes(), png, "$dir/$name")
            }
        }
    }

    /**
     * The schema 7 migration test, ruling 1: the Icon is one row, so the shipped Jade at
     * schema 6, whose veins sit between the icon fill and the icon lines, gets the veins
     * directly above the lines and nothing else changes. It renders the Jade starter
     * goldens byte for byte: the veins are clipped to the fill, which never meets the
     * cut-out lines.
     */
    @Test
    fun version6MovesLayersFromInsideTheIconToAboveIt() {
        val goldens = File(requireNotNull(System.getProperty("golden.dir")))
        val bundle = IconBundle(ByteBuffer.wrap(File(goldens, "samples.bin").readBytes()))
        val v6 = JsonObject(withVisible(json(fixture("v5-jade.json")) as JsonObject) + ("schemaVersion" to JsonPrimitive(6))).toString()
        fun order(doc: JsonObject) = ((doc["theme"] as JsonObject)["layers"] as JsonArray).map { ((it as JsonObject)["id"] as JsonPrimitive).content }
        assertEquals(listOf("background", "effects", "strokes", "icon-fill", "veins", "icon-lines"), order(json(v6) as JsonObject))
        val migrated = migrate(v6, now = { error("no clock") }, newId = { error("no id") })
        assertEquals(ThemeDocument.CURRENT_SCHEMA, migrated.schemaVersion)
        val out = json(migrated.toJson()) as JsonObject
        assertEquals(listOf("background", "effects", "strokes", "icon-fill", "icon-lines", "veins"), order(out))
        assertEquals(JsonObject(iconTogether(json(v6) as JsonObject) + ("schemaVersion" to JsonPrimitive(ThemeDocument.CURRENT_SCHEMA))), out, "only the order, the mappings and the version change")
        assertEquals(GoldenSet.STARTERS.getValue("preset_jade"), migrated.theme)
        val renderer = IconRenderer()
        for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
            val png = renderer.renderPng(name, bundle.icon(i), migrated.theme, OUTPUT_SIZE)
            assertContentEquals(File(goldens, "preset_jade/$name.png").readBytes(), png, "preset_jade/$name")
        }
    }

    /** The step on its own: several layers keep their order, either icon layer may be on top, and a theme missing one is left alone. */
    @Test
    fun iconTogetherMovesOnlyWhatIsBetween() {
        fun ids(vararg id: String) = id.map { JsonObject(mapOf("id" to JsonPrimitive(it))) }
        fun names(l: List<kotlinx.serialization.json.JsonElement>) = l.map { ((it as JsonObject)["id"] as JsonPrimitive).content }
        assertEquals(listOf("background", "icon-lines", "icon-fill", "a", "b", "top"),
            names(iconTogether(ids("background", "icon-lines", "a", "b", "icon-fill", "top"))))
        assertEquals(listOf("icon-fill", "icon-lines", "a"), names(iconTogether(ids("icon-fill", "icon-lines", "a"))))
        assertEquals(listOf("icon-fill", "a", "b"), names(iconTogether(ids("icon-fill", "a", "b"))))
        assertEquals(listOf("a", "b"), names(iconTogether(ids("a", "b"))))
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
