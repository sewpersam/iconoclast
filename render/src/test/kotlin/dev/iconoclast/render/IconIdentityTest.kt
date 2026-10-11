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

    /**
     * Readiness item 2: the seed name reaches the pixels only through the one per-icon
     * offset. Texture, noise layers, streak breakup and grain all read it: pinning slot A's
     * offset on slot B gives slot A's bytes exactly, so nothing derives an offset of its own.
     */
    @Test
    fun theSeedActsOnlyThroughTheOneOffset() {
        for (theme in listOf(jade, grainy)) for (size in listOf(128, OUTPUT_SIZE)) {
            val r = IconRenderer()
            val a = r.render("signal", icon("signal"), "fdroid", theme, size)
            val pinned = r.render("signal", icon("signal"), "camera", theme.copy(textureOffset = TextureOffset.of("fdroid")), size)
            assertContentEquals(a.pixels, pinned.pixels, "$size")
        }
    }

    @Test
    fun grainFollowsTheOffset() {
        val grain = Theme(layers = Theme.baseLayers() + EffectLayer("grain", Effect.Grain(amount = 1f), blend = BlendMode.OVERLAY))
        val r = IconRenderer()
        val own = r.render("signal", icon("signal"), grain, 128).pixels
        val moved = r.render("signal", icon("signal"), grain.copy(textureOffset = TextureOffset(0.1f, 0.2f)), 128).pixels
        assertFalse(own.contentEquals(moved))
    }
}
