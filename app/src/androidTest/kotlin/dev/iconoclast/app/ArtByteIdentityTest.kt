package dev.iconoclast.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.texture.BuiltInBrushes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.MessageDigest

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
        assertEquals(GoldenSet.TEXTURE, renderer.textures.put(asset(GoldenSet.TEXTURE_FILE)))
        val compared = ArrayList<String>()
        val differ = ArrayList<String>()
        for ((dir, theme, name) in GoldenSet.CASES) {
            val i = GoldenSet.SAMPLES.indexOf(name)
            val path = "$dir/$name.png"
            val png = renderer.renderPng(name, bundle.icon(i), theme, OUTPUT_SIZE)
            compared += path
            if (!png.contentEquals(asset(path))) differ += path
        }
        for ((dir, theme, name) in GoldenSet.CASES_324) {
            val path = "$dir/$name.png"
            val png = renderer.renderPng(name, bundle.icon(GoldenSet.SAMPLES.indexOf(name)), name, theme, OutputResolution.COMPACT.px, OutputResolution.COMPACT)
            compared += path
            if (!png.contentEquals(asset(path))) differ += path
        }
        android.util.Log.i("ArtByteIdentity", "compared ${compared.size} PNGs, ${differ.size} differ: $differ")
        assertEquals("PNGs differing from the JVM goldens", emptyList<String>(), differ)
        assertEquals(266 + 48, compared.size)
    }

    /**
     * The built-in brushes are generated on the phone, never shipped: their canonical PNG
     * must hash on ART to the id that themes and the JVM goldens name.
     */
    @Test
    fun builtInBrushesHashToTheirIds() {
        val sha = MessageDigest.getInstance("SHA-256")
        val got = BuiltInBrushes.ALL.map { b ->
            b.name to sha.digest(requireNotNull(BuiltInBrushes.png(b.sha256)) { b.name }).joinToString("") { "%02x".format(it) }
        }
        android.util.Log.i("ArtByteIdentity", "built-in brushes: $got")
        assertEquals(3, got.size)
        assertEquals(BuiltInBrushes.ALL.map { it.name to it.sha256 }, got)
    }
}
