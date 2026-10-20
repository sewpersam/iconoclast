package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.MAX_ICON_SCALE
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Schema 10 to 11: the icon is laid out on the background shape's square. */
class MigrationV10ToV11Test {

    companion object {
        /** [t] as `v10ToV11` rewrites it: the icon's numbers moved into the shape's square. */
        fun followed(t: Theme): Theme {
            val f = t.background.size
            if (!t.background.enabled || f == 1f) return t
            val s = t.shape
            return t.copy(shape = s.copy(
                iconScale = (s.iconScale / f).coerceIn(0.5f, MAX_ICON_SCALE),
                offsetX = (s.offsetX / f).coerceIn(-0.1f, 0.1f),
                offsetY = (s.offsetY / f).coerceIn(-0.1f, 0.1f),
            ))
        }
    }

    private fun v10(theme: Theme): String {
        val doc = ThemeDocument.JSON.parseToJsonElement(ThemeDocument(id = "a", name = "A", created = 5, modified = 6, theme = theme).toJson()) as JsonObject
        return JsonObject(doc + ("schemaVersion" to JsonPrimitive(10))).toString()
    }

    private fun up(json: String) = migrate(json, now = { error("no clock") }, newId = { error("no id") }).theme

    private val bundle by lazy {
        IconBundle(ByteBuffer.wrap(java.io.File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    }

    /** Every golden theme at full size, or with the background off, changes only its version. */
    @Test
    fun everyFullSizeGoldenMigratesUnchanged() {
        val themes = (GoldenSet.CASES.map { it.second } + GoldenSet.CASES_324.map { it.second }).distinct()
        val full = themes.filter { !it.background.enabled || it.background.size == 1f }
        assertTrue(full.size > 20)
        for (t in full) assertEquals(t, up(v10(t)))
        val below = themes - full.toSet()
        assertTrue(below.isNotEmpty())
        for (t in below) assertEquals(followed(t), up(v10(t)))
    }

    /**
     * A schema 10 theme below full size keeps its icon: the migrated icon in the shape's
     * square draws the lines and fill the old icon drew on the canvas, up to float rounding.
     * Schema 10's renderer laid the icon on the canvas, which today's draws with the shape
     * at full size.
     */
    @Test
    fun belowFullSizeKeepsTheIcon() {
        val r = IconRenderer()
        val names = listOf("fdroid", "camera", "google_chrome", "signal", "spotify", "weather")
        val shapes = listOf(ShapeParams(), ShapeParams(iconScale = 0.6f, offsetX = 0.03f, offsetY = -0.02f), ShapeParams(iconScale = 0.75f, strokeWeight = 1.5f))
        for (n in names) for (shape in shapes) for (f in listOf(0.85f, 0.7f, 0.5f)) for (mode in listOf(FillMode.OUTLINE, FillMode.INVERT)) {
            val icon = bundle.icon(GoldenSet.SAMPLES.indexOf(n))
            val old = Theme(shape = shape, fill = FillParams(mode = mode), background = BackgroundParams(shape = BackgroundShape.Circle, size = f))
            val migrated = up(v10(old))
            val before = r.iconMask(n, icon, old.copy(background = old.background.copy(size = 1f)), 160)!!.toFloatArray()
            val after = r.iconMask(n, icon, migrated, 160)!!.toFloatArray()
            var worst = 0f
            for (i in before.indices) worst = maxOf(worst, abs(before[i] - after[i]))
            assertTrue(worst < 0.05f, "$n $shape $f $mode differs by $worst")
        }
    }

    @Test
    fun outOfRangeNumbersAreCapped() {
        val t = Theme(shape = ShapeParams(iconScale = 1.5f, offsetX = 0.08f, offsetY = -0.09f), background = BackgroundParams(size = 0.5f))
        val m = up(v10(t)).shape
        assertEquals(MAX_ICON_SCALE, m.iconScale)
        assertEquals(0.1f, m.offsetX)
        assertEquals(-0.1f, m.offsetY)
    }

    @Test
    fun backgroundOffIsUntouched() {
        val t = Theme(shape = ShapeParams(iconScale = 0.7f, offsetX = 0.05f), background = BackgroundParams(enabled = false, size = 0.6f))
        assertEquals(t, up(v10(t)))
    }
}
