package dev.iconoclast.app

import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Card previews must show what the pack shows. At 64 px the M4 Jade's invert fill fell back
 * to outline; the M6 Jade's thicker stroke no longer does, so the 64 px case keeps the M4 one.
 */
class PreviewSizeTest {
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val jade = migrate(File(requireNotNull(System.getProperty("presets.dir")), Presets.FILES[1]).readText()).theme
    private val m4Jade = GoldenSet.PRESETS.getValue("jade")

    @Test
    fun jadeKeepsItsFillAtPreviewSize() {
        assertTrue(ThemePreviews.SIZE >= 128, "previews render at 128 px minimum")
        for (name in listOf("settings", "clock")) {
            val i = GoldenSet.SAMPLES.indexOf(name)
            val at64 = IconRenderer().glyphMasks(GoldenSet.SAMPLES[i], bundle.icon(i), m4Jade, 64).fellBack
            val atPreview = listOf(m4Jade, jade).any { IconRenderer().glyphMasks(GoldenSet.SAMPLES[i], bundle.icon(i), it, ThemePreviews.SIZE).fellBack }
            println("$name: fellBack at 64 px = $at64, at ${ThemePreviews.SIZE} px = $atPreview")
            assertTrue(at64, "$name at 64 px was expected to fall back")
            assertFalse(atPreview, "$name falls back at ${ThemePreviews.SIZE} px")
        }
    }
}
