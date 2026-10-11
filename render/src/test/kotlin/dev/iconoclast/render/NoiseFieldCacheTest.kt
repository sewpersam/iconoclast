package dev.iconoclast.render

import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Preview sizes cache each noise paint's whole-canvas field, so a drag that changes only
 * the geometry skips the noise. The pixels must equal the uncached path's, which evaluates
 * the field only where the layer can show: previews must match Apply.
 */
class NoiseFieldCacheTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/golden/$name")) { name }.readBytes()

    @Test
    fun cachedNoiseFieldsGiveTheSamePixels() {
        val bundle = IconBundle(ByteBuffer.wrap(resource("samples.bin")))
        val themes = GoldenSet.CASES.map { it.second }.distinct()
            .filter { t -> t.layers.any { it is PaintLayer && it.source is Paint.Noise } }
        assertTrue(themes.size >= 3, "noise themes: ${themes.size}")
        val cached = IconRenderer()
        val plain = IconRenderer(noiseCacheBelow = 0)
        for (r in listOf(cached, plain)) assertEquals(GoldenSet.TEXTURE, r.textures.put(resource(GoldenSet.TEXTURE_FILE)))
        var compared = 0
        val differ = ArrayList<String>()
        for ((t, theme) in themes.withIndex()) for (weight in listOf(theme.shape.strokeWeight, theme.shape.strokeWeight + 0.25f)) {
            // The second weight is a drag step: new masks, the cached noise fields again.
            val step = theme.copy(shape = theme.shape.copy(strokeWeight = weight))
            for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
                val a = cached.render(name, bundle.icon(i), step, PREVIEW)
                val b = plain.render(name, bundle.icon(i), step, PREVIEW)
                compared++
                if (!a.pixels.contentEquals(b.pixels)) differ += "theme $t weight $weight $name"
            }
        }
        println("noise field cache: ${themes.size} themes, $compared icons compared, ${differ.size} differ")
        assertEquals(emptyList(), differ.take(10))
    }

    private companion object {
        const val PREVIEW = 128
    }
}
