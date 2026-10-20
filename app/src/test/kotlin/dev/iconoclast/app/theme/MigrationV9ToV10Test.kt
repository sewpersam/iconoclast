package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Marble
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.Icon
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Schema 9 to 10: the background Size control, and the background's paint laid out on its shape. */
class MigrationV9ToV10Test {

    private fun v9(theme: Theme): String {
        val doc = ThemeDocument.JSON.parseToJsonElement(ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = theme).toJson()) as JsonObject
        return JsonObject(doc + ("schemaVersion" to JsonPrimitive(9))).toString()
    }

    private fun up(json: String) = migrate(json, now = { error("no clock") }, newId = { error("no id") }).theme

    /** Every golden theme schema 9 could hold changes only its version: none has a framed paint below full size. */
    @Test
    fun everySchema9GoldenMigratesUnchanged() {
        val themes = GoldenSet.CASES.filter { !it.first.startsWith("bg_") }.map { it.second }.distinct() + GoldenSet.CASES_324.map { it.second }.distinct()
        for (t in themes) assertEquals(t, up(v9(t)))
        assertTrue(themes.any { it.background.size < 1f }, "one golden is below full size")
    }

    private val ramp = listOf(ColorStop(0f, 0xFF102030.toInt()), ColorStop(0.5f, 0xFFB86BFF.toInt()), ColorStop(1f, 0xFFFFE29A.toInt()))

    private fun at(bg: Paint, mapping: Mapping, size: Float) = Theme(
        background = BackgroundParams(shape = BackgroundShape.Square, size = size),
        layers = Theme.baseLayers(bg).map { l ->
            when (l.id) {
                Layer.BACKGROUND_ID -> (l as PaintLayer).copy(mapping = mapping)
                Layer.ICON_FILL_ID, Layer.ICON_LINES_ID -> (l as PaintLayer).copy(visible = false)
                else -> l
            }
        },
    )

    /**
     * A schema 9 theme at 70% looks as it did: inside the shape, the migrated theme draws
     * what the old canvas-wide paint drew there, which is the unmigrated paint on a full
     * tile. Gradients and centred images, up to float rounding.
     */
    @Test
    fun belowFullSizeKeepsItsLook() {
        val r = IconRenderer().apply { textures.put(Marble.PNG) }
        val none = Icon(emptyList())
        val size = 140
        val cases = listOf(
            Paint.LinearGradient(0.1f, 0f, 0.8f, 1f, ramp) to Mapping(),
            Paint.RadialGradient(0.4f, 0.35f, 0.6f, ramp) to Mapping(),
            Paint.AngularGradient(0.6f, 0.4f, 200f, ramp) to Mapping(),
            Paint.Texture(Marble.HASH) to Mapping(MappingMode.FILL, scale = 2f, randomShift = 0f, rotation = 20f),
            Paint.Texture(Marble.HASH) to Mapping(MappingMode.FILL, scale = 0.5f, randomShift = 0f),
            Paint.Texture(Marble.HASH) to Mapping(MappingMode.FIT, scale = 1.2f, randomShift = 0f),
            Paint.Texture(Marble.HASH) to Mapping(MappingMode.TILE, scale = 0.5f, randomShift = 0f),
        )
        for ((paint, mapping) in cases) {
            val migrated = up(v9(at(paint, mapping, 0.7f)))
            val now = r.render("slot", none, migrated, size).pixels
            val old = r.render("slot", none, at(paint, mapping, 1f), size).pixels
            var worst = 0
            // The 70% square spans 21 to 119; its edge pixels are partly covered.
            for (y in 22 until 118) for (x in 22 until 118) for (c in 0..3) {
                val i = (y * size + x) * 4 + c
                worst = maxOf(worst, abs((now[i].toInt() and 0xFF) - (old[i].toInt() and 0xFF)))
            }
            assertTrue(worst <= 1, "$paint $mapping differs by $worst")
        }
    }

    @Test
    fun aScaleBeyondFourIsCapped() {
        val t = up(v9(at(Paint.Texture(Marble.HASH), Mapping(MappingMode.TILE, scale = 3.5f), 0.7f)))
        assertEquals(4f, (t.layers.first { it.id == Layer.BACKGROUND_ID } as PaintLayer).mapping.scale)
    }

    @Test
    fun patternsAndSolidsAreUntouched() {
        val solid = at(Paint.Solid(0xFF336699.toInt()), Mapping(), 0.8f)
        assertEquals(solid, up(v9(solid)))
        val pattern = at(Paint.Noise(dev.iconoclast.render.Pattern.Noise(), 3, ramp), Mapping(scale = 2f), 0.8f)
        assertEquals(pattern, up(v9(pattern)))
    }
}
