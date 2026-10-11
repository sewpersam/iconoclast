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
        val hash = TextureFiles(dir).add(cracks)
        assertEquals(GoldenSet.TEXTURE, hash)
        assertTrue(File(dir, "$hash.png").exists())
        val files = TextureFiles(dir)
        assertEquals(emptySet(), files.load(setOf(hash)))
        assertContentEquals(File(dir, "$hash.png").readBytes(), files.store.bytes(hash))
        assertEquals(listOf(hash), files.recent())
    }

    @Test
    fun missingOrDamagedFilesAreReported() {
        val files = TextureFiles(dir)
        val absent = "0".repeat(64)
        assertEquals(setOf(absent), files.load(setOf(absent)))
        File(dir, "${GoldenSet.TEXTURE}.png").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(setOf(GoldenSet.TEXTURE), files.load(setOf(GoldenSet.TEXTURE)))
    }

    /** The store remembers a texture absent; writing its file clears that. */
    @Test
    fun writingATextureClearsRememberedAbsence() {
        val files = TextureFiles(dir)
        assertFalse(GoldenSet.TEXTURE in files.store)
        files.add(cracks)
        assertFalse(files.store.isResident(GoldenSet.TEXTURE))
        assertTrue(GoldenSet.TEXTURE in files.store)
    }

    @Test
    fun presenceHidesOnlyTheMissingLayers() {
        val theme = GoldenSet.LOOKS.values.first()
        val files = TextureFiles(dir)
        val store = files.store
        val absent = TexturePresence.absent(store, theme)
        assertEquals(theme.textureHashes(), absent)
        val shown = TexturePresence.without(theme, absent)
        assertTrue(shown.textureHashes().isEmpty())
        assertEquals(theme.layers.count { ((it as? PaintLayer)?.source !is Paint.Texture) }, shown.layers.size)
        assertEquals(theme, TexturePresence.without(theme, emptySet()))
        assertFalse(TexturePresence.ensure(files, Theme()).isNotEmpty())
    }

    @Test
    fun importSizesKeepTheLongestSideAtMost2048() {
        assertEquals(2048 to 1536, TextureImport.fitted(4000, 3000, 2048))
        assertEquals(1536 to 2048, TextureImport.fitted(3000, 4000, 2048))
        assertEquals(256 to 256, TextureImport.fitted(256, 256, 2048))
        assertEquals(2048 to 1, TextureImport.fitted(10000, 2, 2048))
    }

    /** Item 1: many textures on disk, a small cap: memory stays bounded and every one still renders. */
    @Test
    fun manyTexturesStayWithinTheCapAndReload() {
        val one = 64 * 64 * 4L
        val files = TextureFiles(dir, maxBytes = 3 * one + 3 * 20_000)
        val hashes = (0 until 12).map { n ->
            val px = ByteArray(64 * 64 * 4) { i -> if (i % 4 == 3) -1 else ((i * 31 + n * 97) % 251).toByte() }
            files.add(TextureStore.encode(64, 64, px))
        }
        assertEquals(emptySet(), files.load(hashes.toSet()))
        assertTrue(files.store.residentBytes <= 3 * one + 3 * 20_000)
        assertFalse(files.store.isResident(hashes.first()))
        assertTrue(hashes.first() in files.store, "present means on disk")
        assertTrue(files.store[hashes.first()] != null)
    }
}
