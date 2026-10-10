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

/** Card previews must show what the pack shows. At 64 px Jade's invert fill falls back to outline. */
class PreviewSizeTest {
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val jade = migrate(File(requireNotNull(System.getProperty("presets.dir")), Presets.FILES[1]).readText()).theme

    @Test
    fun jadeKeepsItsFillAtPreviewSize() {
        assertTrue(ThemePreviews.SIZE >= 128, "previews render at 128 px minimum")
        for (name in listOf("settings", "clock")) {
            val i = GoldenSet.SAMPLES.indexOf(name)
            val at64 = IconRenderer().glyphMasks(i, bundle.icon(i), jade, 64).fellBack
            val atPreview = IconRenderer().glyphMasks(i, bundle.icon(i), jade, ThemePreviews.SIZE).fellBack
            println("$name: fellBack at 64 px = $at64, at ${ThemePreviews.SIZE} px = $atPreview")
            assertTrue(at64, "$name at 64 px was expected to fall back")
            assertFalse(atPreview, "$name falls back at ${ThemePreviews.SIZE} px")
        }
    }
}
