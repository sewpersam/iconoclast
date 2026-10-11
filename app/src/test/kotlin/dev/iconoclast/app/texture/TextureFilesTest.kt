package dev.iconoclast.app.texture

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.texture.TextureStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextureFilesTest {
    private val cracks = File(System.getProperty("golden.dir"), GoldenSet.TEXTURE_FILE).readBytes()
    private val dir = Files.createTempDirectory("textures").toFile()

    @Test
    fun addedTextureLoadsIntoAFreshStore() {
        val hash = TextureFiles(dir, TextureStore()).add(cracks)
        assertEquals(GoldenSet.TEXTURE, hash)
        assertTrue(File(dir, "$hash.png").exists())
        val store = TextureStore()
        val files = TextureFiles(dir, store)
        assertEquals(emptySet(), files.load(setOf(hash)))
        assertContentEquals(File(dir, "$hash.png").readBytes(), store.bytes(hash))
        assertEquals(listOf(hash), files.recent())
    }

    @Test
    fun missingOrDamagedFilesAreReported() {
        val files = TextureFiles(dir, TextureStore())
        val absent = "0".repeat(64)
        assertEquals(setOf(absent), files.load(setOf(absent)))
        File(dir, "${GoldenSet.TEXTURE}.png").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(setOf(GoldenSet.TEXTURE), files.load(setOf(GoldenSet.TEXTURE)))
    }

    @Test
    fun presenceHidesOnlyTheMissingLayers() {
        val theme = GoldenSet.LOOKS.values.first()
        val store = TextureStore()
        val absent = TexturePresence.absent(store, theme)
        assertEquals(theme.textureHashes(), absent)
        val shown = TexturePresence.without(theme, absent)
        assertTrue(shown.textureHashes().isEmpty())
        assertEquals(theme.layers.count { ((it as? PaintLayer)?.source !is Paint.Texture) }, shown.layers.size)
        assertEquals(theme, TexturePresence.without(theme, emptySet()))
        assertFalse(TexturePresence.ensure(TextureFiles(dir, store), Theme()).isNotEmpty())
    }

    @Test
    fun importSizesKeepTheLongestSideAtMost2048() {
        assertEquals(1, TextureImport.sampleSize(2048, 1000, 2048))
        assertEquals(1, TextureImport.sampleSize(4000, 3000, 2048))
        assertEquals(2, TextureImport.sampleSize(4096, 3000, 2048))
        assertEquals(4, TextureImport.sampleSize(12000, 9000, 2048))
        assertEquals(2048 to 1536, TextureImport.fitted(4000, 3000, 2048))
        assertEquals(1536 to 2048, TextureImport.fitted(3000, 4000, 2048))
        assertEquals(256 to 256, TextureImport.fitted(256, 256, 2048))
        assertEquals(2048 to 1, TextureImport.fitted(10000, 2, 2048))
    }
}
