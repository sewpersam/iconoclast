package dev.iconoclast.app.theme

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThemeZipTest {
    private val golden = File(requireNotNull(System.getProperty("golden.dir")))
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))
    private val cracks = File(golden, GoldenSet.TEXTURE_FILE).readBytes()

    private fun doc(name: String) =
        ThemeDocument(id = "t-$name", name = name, created = 1, modified = 2, theme = GoldenSet.LOOKS.getValue(name))

    private fun sourceStore() = TextureStore().also { assertEquals(GoldenSet.TEXTURE, it.put(cracks)) }

    /** Export, import into an empty store, render: the same bytes as before the trip. */
    @Test
    fun roundTripRendersTheSameBytes() {
        for (name in GoldenSet.LOOKS.keys) {
            val original = doc(name)
            val from = sourceStore()
            val zip = ThemeZipExport.write(original, from)

            val to = TextureStore()
            val archive = ThemeZipImport.read(zip, to) { false }
            assertEquals(original.theme.textureHashes(), archive.textures, name)
            val imported = migrate(archive.json)
            assertEquals(original, imported, name)
            for (hash in archive.textures) assertContentEquals(from.bytes(hash), to.bytes(hash))

            val a = IconRenderer(textures = from)
            val b = IconRenderer(textures = to)
            for (s in GoldenSet.LOOK_SAMPLES) {
                val i = GoldenSet.SAMPLES.indexOf(s)
                assertContentEquals(a.renderPng(s, bundle.icon(i), original.theme, 128), b.renderPng(s, bundle.icon(i), imported.theme, 128), "$name $s")
            }
        }
    }

    @Test
    fun exportIsDeterministicAndHoldsOnlyWhatTheThemeUses() {
        val d = doc(GoldenSet.LOOKS.keys.first())
        val zip = ThemeZipExport.write(d, sourceStore())
        assertContentEquals(zip, ThemeZipExport.write(d, sourceStore()))
        val names = entries(zip).keys
        assertEquals(setOf("theme.json", "textures/${GoldenSet.TEXTURE}.png"), names)
    }

    @Test
    fun archiveWithoutThemeIsRefused() {
        val zip = zip("textures/${GoldenSet.TEXTURE}.png" to cracks)
        val e = assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip, TextureStore()) { false } }
        assertTrue("theme.json" in e.message!!)
    }

    @Test
    fun missingTextureIsRefusedUnlessOnThePhone() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson()
        val zip = zip("theme.json" to json.toByteArray())
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip, TextureStore()) { false } }
        assertEquals(emptySet(), ThemeZipImport.read(zip, TextureStore()) { true }.textures)
    }

    @Test
    fun textureNotMatchingItsHashIsRefused() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson()
        val other = TextureStore.encode(1, 1, byteArrayOf(0, 0, 0, -1))
        val zip = zip("theme.json" to json.toByteArray(), "textures/${GoldenSet.TEXTURE}.png" to other)
        val e = assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip, TextureStore()) { false } }
        assertTrue("damaged" in e.message!!)
        val garbage = zip("theme.json" to json.toByteArray(), "textures/${GoldenSet.TEXTURE}.png" to byteArrayOf(1, 2, 3))
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(garbage, TextureStore()) { false } }
    }

    @Test
    fun bareJsonImportsWhenItsTexturesAreOnThePhone() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson().toByteArray()
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(json, TextureStore()) { false } }
        assertEquals(json.decodeToString(), ThemeZipImport.read(json, TextureStore()) { true }.json)
        val plain = ThemeDocument(id = "p", name = "Plain", created = 1, modified = 2, theme = dev.iconoclast.render.Theme()).toJson()
        assertEquals(plain, ThemeZipImport.read(plain.toByteArray(), TextureStore()) { false }.json)
    }

    @Test
    fun damagedArchiveOrThemeIsRefused() {
        val zip = ThemeZipExport.write(doc(GoldenSet.LOOKS.keys.first()), sourceStore())
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip.copyOf(zip.size / 2), TextureStore()) { false } }
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip("theme.json" to "{}".toByteArray()), TextureStore()) { false } }
    }

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, b) in files) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
        return m
    }
}
