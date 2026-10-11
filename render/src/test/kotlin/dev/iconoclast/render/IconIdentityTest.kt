package dev.iconoclast.render

import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * M7 readiness item 1: a slot's geometry and its seed are separate names. A swap draws
 * another drawable's geometry under the slot's own seed, through the shared cache,
 * without changing what either drawable renders afterwards.
 */
class IconIdentityTest {
    private val bundle = IconBundle(ByteBuffer.wrap(requireNotNull(javaClass.getResourceAsStream("/golden/samples.bin")).readBytes()))
    private fun icon(name: String) = bundle.icon(GoldenSet.SAMPLES.indexOf(name))

    /** Textured, streaked, noise cached at preview size: every per-icon path is live. */
    private val jade = GoldenSet.STARTERS.getValue("preset_jade")
    private val grainy = GoldenSet.JADE_LIKE

    @Test
    fun aSwapCannotPoisonTheSharedCache() {
        for (theme in listOf(jade, grainy)) for (size in listOf(128, OUTPUT_SIZE)) {
            val r = IconRenderer()
            // fdroid's slot swapped to camera's drawable.
            r.render("camera", icon("camera"), "fdroid", theme, size)
            val x = r.render("fdroid", icon("fdroid"), "fdroid", theme, size)
            val y = r.render("camera", icon("camera"), "camera", theme, size)
            assertContentEquals(IconRenderer().render("fdroid", icon("fdroid"), theme, size).pixels, x.pixels, "fdroid at $size")
            assertContentEquals(IconRenderer().render("camera", icon("camera"), theme, size).pixels, y.pixels, "camera at $size")
        }
    }

    @Test
    fun aSwappedSlotKeepsItsOwnSeed() {
        val r = IconRenderer()
        val own = r.render("camera", icon("camera"), jade, 128)
        val swapped = r.render("camera", icon("camera"), "fdroid", jade, 128)
        assertFalse(own.pixels.contentEquals(swapped.pixels), "the seed name moves the texture")
        assertContentEquals(IconRenderer().render("camera", icon("camera"), "fdroid", jade, 128).pixels, swapped.pixels, "warm equals cold")
    }
}
