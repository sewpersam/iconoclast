package dev.iconoclast.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.format.IconBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * Renders the JVM golden set on ART and compares the PNG bytes, not the decoded pixels.
 * Byte identity proves the renderer and the PNG encoder are platform independent.
 */
@RunWith(AndroidJUnit4::class)
class ArtByteIdentityTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun asset(name: String) = assets.open(name).use { it.readBytes() }

    @Test
    fun goldensMatchByteForByte() {
        val bundle = IconBundle(ByteBuffer.wrap(asset("samples.bin")))
        val renderer = IconRenderer()
        val compared = ArrayList<String>()
        val differ = ArrayList<String>()
        for ((mode, theme) in GoldenSet.THEMES) for ((i, name) in GoldenSet.SAMPLES.withIndex()) {
            val path = "${mode.name.lowercase()}/$name.png"
            val png = renderer.renderPng(i, bundle.icon(i), theme, OUTPUT_SIZE)
            compared += path
            if (!png.contentEquals(asset(path))) differ += path
        }
        android.util.Log.i("ArtByteIdentity", "compared ${compared.size} PNGs, ${differ.size} differ: $differ")
        assertEquals("PNGs differing from the JVM goldens", emptyList<String>(), differ)
        assertEquals(96, compared.size)
    }
}
