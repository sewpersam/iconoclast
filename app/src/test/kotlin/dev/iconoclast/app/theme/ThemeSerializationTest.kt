package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.Border
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSerializationTest {
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))

    private fun doc(theme: Theme) = ThemeDocument(id = "t", name = "Test", created = 1, modified = 2, theme = theme)

    private val themes: List<Theme> = GoldenSet.THEMES.values + listOf(
        Theme(),
        Theme(background = Theme().background.copy(shape = BackgroundShape.Square, border = Border(0.05f, 0x80FF0000.toInt()))),
        Theme(background = Theme().background.copy(shape = BackgroundShape.RoundedSquare(0.5f), enabled = false)),
    )

    @Test
    fun roundTripIsIdentical() {
        for (theme in themes) {
            val original = doc(theme)
            val parsed = migrate(original.toJson())
            assertEquals(original, parsed)
            assertEquals(original.toJson(), parsed.toJson())
        }
    }

    @Test
    fun roundTripRendersTheSameBytes() {
        for (theme in themes) {
            val parsed = migrate(doc(theme).toJson()).theme
            val icon = bundle.icon(3)
            assertContentEquals(IconRenderer().renderPng("test", icon, theme, 128), IconRenderer().renderPng("test", icon, parsed, 128))
        }
    }

    @Test
    fun negativeZeroWritesAndReadsAsZero() {
        val negative = doc(Theme(shape = ShapeParams(offsetX = -0f, offsetY = -0f)))
        val positive = doc(Theme(shape = ShapeParams(offsetX = 0f, offsetY = 0f)))
        assertEquals(positive.toJson(), negative.toJson())
        assertFalse("-0" in negative.toJson())
        val parsed = migrate(positive.toJson().replace("\"offsetX\": 0.0", "\"offsetX\": -0.0"))
        assertEquals(positive, parsed)
        assertEquals(positive.theme.hashCode(), parsed.theme.hashCode())
    }

    @Test
    fun everyFieldIsWritten() {
        val json = doc(Theme()).toJson()
        for (key in listOf("schemaVersion", "strokeWeight", "dots", "keepLoneLines", "\"border\": null", "\"type\": \"squircle\"", "\"color\": \"#FF202124\""))
            assertTrue(key in json, "missing $key in $json")
    }

    @Test
    fun badValuesAreRejected() {
        val json = doc(Theme()).toJson()
        for (bad in listOf(
            json.replace("\"strokeWeight\": 1.0", "\"strokeWeight\": 9.0"),
            json.replace("#FF202124", "red"),
            json.replace("\"squircle\"", "\"hexagon\""),
            json.replace("\"keepLoneLines\": true", "\"keepLoneLines\": true, \"extra\": 1"),
            json.replace("\"strokeWeight\": 1.0,", ""),
        )) {
            assertFailsWith<ThemeFormatException>(bad) { migrate(bad) }
        }
    }
}
