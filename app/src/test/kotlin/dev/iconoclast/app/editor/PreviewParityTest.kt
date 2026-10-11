package dev.iconoclast.app.editor

import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Previews re-rasterise at 128 px rather than downscale the 432 px pack output. The one
 * decision that can differ between the two is invert fill's fallback to outline, which
 * would make the preview show another look than the pack. This checks it for the golden
 * samples under every golden theme and both presets.
 */
class PreviewParityTest {
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val presets = Presets.FILES.map { migrate(File(requireNotNull(System.getProperty("presets.dir")), it).readText()) }

    @Test
    fun fallbackAtPreviewSizeMatchesThePack() {
        val themes = GoldenSet.THEMES.map { (m, t) -> "golden ${m.name.lowercase()}" to t } + presets.map { it.name to it.theme }
        val mismatches = mutableListOf<String>()
        var checked = 0
        for ((name, theme) in themes) for ((i, icon) in GoldenSet.SAMPLES.withIndex()) {
            val preview = IconRenderer().glyphMasks(GoldenSet.SAMPLES[i], bundle.icon(i), theme, EditorViewModel.SIZE).fellBack
            val pack = IconRenderer().glyphMasks(GoldenSet.SAMPLES[i], bundle.icon(i), theme, OUTPUT_SIZE).fellBack
            checked++
            if (preview != pack) mismatches += "$name/$icon: preview $preview, pack $pack"
        }
        println("fallback parity: $checked checked, ${mismatches.size} differ $mismatches")
        assertTrue(mismatches.isEmpty(), "$mismatches")
    }
}
