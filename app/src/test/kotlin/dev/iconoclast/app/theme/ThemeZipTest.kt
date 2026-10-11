package dev.iconoclast.app.theme

import dev.iconoclast.pack.backup
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
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

            val archive = ThemeZipImport.read(zip) { false }
            assertEquals(original.theme.textureHashes(), archive.textures.keys, name)
            val to = TextureStore()
            for ((hash, png) in archive.textures) to.put(hash, png)
            val imported = migrate(archive.json)
            assertEquals(original, imported, name)
            for (hash in archive.textures.keys) assertContentEquals(from.bytes(hash), to.bytes(hash))

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

    /** Built-in brushes are never exported and never required of an archive. */
    @Test
    fun builtInBrushesStayOutOfArchives() {
        val brush = dev.iconoclast.render.texture.BuiltInBrushes.BRISTLE.sha256
        val theme = GoldenSet.LOOKS.getValue("layers_stone").withEffect(
            dev.iconoclast.render.EffectLayer("s", dev.iconoclast.render.Effect.Streaks(breakup = dev.iconoclast.render.Breakup(texture = brush))))
        val d = ThemeDocument(id = "b", name = "Brush", created = 1, modified = 2, theme = theme)
        assertTrue(brush in theme.textureHashes())
        val zip = ThemeZipExport.write(d, sourceStore())
        assertEquals(setOf("theme.json", "textures/${GoldenSet.TEXTURE}.png"), entries(zip).keys)
        val archive = ThemeZipImport.read(zip) { false }
        assertEquals(setOf(GoldenSet.TEXTURE), archive.textures.keys)
        assertEquals(d, migrate(archive.json))
        // A bare JSON whose only texture is a brush needs nothing on the phone.
        val bare = ThemeDocument(id = "c", name = "Bare", created = 1, modified = 2,
            theme = Theme().withEffect(dev.iconoclast.render.EffectLayer("s", dev.iconoclast.render.Effect.Streaks(
                breakup = dev.iconoclast.render.Breakup(texture = brush))))).toJson()
        assertEquals(bare, ThemeZipImport.read(bare.toByteArray()) { false }.json)
    }

    @Test
    fun archiveWithoutThemeIsRefused() {
        val zip = zip("textures/${GoldenSet.TEXTURE}.png" to cracks)
        val e = assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip) { false } }
        assertTrue("theme.json" in e.message!!)
    }

    @Test
    fun missingTextureIsRefusedUnlessOnThePhone() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson()
        val zip = zip("theme.json" to json.toByteArray())
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip) { false } }
        assertEquals(emptySet(), ThemeZipImport.read(zip) { true }.textures.keys)
    }

    @Test
    fun textureNotMatchingItsHashIsRefused() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson()
        val other = TextureStore.encode(1, 1, byteArrayOf(0, 0, 0, -1))
        val zip = zip("theme.json" to json.toByteArray(), "textures/${GoldenSet.TEXTURE}.png" to other)
        val e = assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip) { false } }
        assertTrue("damaged" in e.message!!)
        val garbage = zip("theme.json" to json.toByteArray(), "textures/${GoldenSet.TEXTURE}.png" to byteArrayOf(1, 2, 3))
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(garbage) { false } }
    }

    @Test
    fun bareJsonImportsWhenItsTexturesAreOnThePhone() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson().toByteArray()
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(json) { false } }
        assertEquals(json.decodeToString(), ThemeZipImport.read(json) { true }.json)
        val plain = ThemeDocument(id = "p", name = "Plain", created = 1, modified = 2, theme = Theme()).toJson()
        assertEquals(plain, ThemeZipImport.read(plain.toByteArray()) { false }.json)
    }

    /**
     * The picked file is streamed, never held whole: an archive larger than the old 64 MB
     * whole-file cap imports when the entries it reads stay in bounds, and every stream
     * opened is closed. A bare JSON is capped at MAX_JSON.
     */
    @Test
    fun streamsTheFileAndClosesEveryStream() {
        val file = File.createTempFile("big-theme", ".zip").apply { deleteOnExit() }
        val junk = ByteArray(1024 * 1024).also { kotlin.random.Random(1).nextBytes(it) }
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            z.setLevel(0)
            z.putNextEntry(ZipEntry("theme.json")); z.write(doc(GoldenSet.LOOKS.keys.first()).toJson().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("textures/${GoldenSet.TEXTURE}.png")); z.write(cracks); z.closeEntry()
            repeat(70) { z.putNextEntry(ZipEntry("junk/$it.bin")); z.write(junk); z.closeEntry() }
        }
        assertTrue(file.length() > 70L * 1024 * 1024, "bigger than the old cap")
        var opened = 0
        var closed = 0
        val open = {
            opened++
            object : java.io.FilterInputStream(file.inputStream()) {
                private var done = false
                override fun close() { if (!done) { done = true; closed++ }; super.close() }
            }
        }
        assertEquals(setOf(GoldenSet.TEXTURE), ThemeZipImport.read(open) { false }.textures.keys)
        assertTrue(opened > 0)
        assertEquals(opened, closed)

        val bare = ByteArray(ThemeZipImport.MAX_JSON + 1) { ' '.code.toByte() }
        assertTrue("too large" in assertFailsWith<ThemeFormatException> { ThemeZipImport.read(bare) { false } }.message!!)
    }

    @Test
    fun damagedArchiveOrThemeIsRefused() {
        val zip = ThemeZipExport.write(doc(GoldenSet.LOOKS.keys.first()), sourceStore())
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip.copyOf(zip.size / 2)) { false } }
        assertFailsWith<ThemeFormatException> { ThemeZipImport.read(zip("theme.json" to "{}".toByteArray())) { false } }
    }

    /** Validation stores nothing: a failing archive leaves no texture behind. */
    @Test
    fun validationTouchesNoStore() {
        val d = doc(GoldenSet.LOOKS.keys.first())
        val archive = ThemeZipImport.read(ThemeZipExport.write(d, sourceStore())) { false }
        assertEquals(setOf(GoldenSet.TEXTURE), archive.textures.keys)
        assertContentEquals(sourceStore().bytes(GoldenSet.TEXTURE), archive.textures[GoldenSet.TEXTURE])
    }

    /** Item 2: entries the theme does not reference are never read, however many. */
    @Test
    fun unreferencedEntriesAreSkippedUnread() {
        val json = doc(GoldenSet.LOOKS.keys.first()).toJson().toByteArray()
        val junk = ByteArray(2 * 1024 * 1024)
        val entries = listOf("theme.json" to json, "textures/${GoldenSet.TEXTURE}.png" to cracks) +
            (0 until 40).map { "textures/%064x.png".format(it) to junk } + (0 until 40).map { "junk/$it.bin" to junk }
        val archive = ThemeZipImport.read(zip(*entries.toTypedArray())) { false }
        assertEquals(setOf(GoldenSet.TEXTURE), archive.textures.keys)
    }

    /** Item 2: compressible entries the theme does reference are capped in total, refused before decoding. */
    @Test
    fun referencedEntriesAreCappedInTotal() {
        val hashes = (1..4).map { "%064x".format(it) }
        val theme = Theme().let { t -> t.copy(layers = t.layers + hashes.map { PaintLayer(it, Paint.Texture(it)) }) }
        val json = ThemeDocument(id = "b", name = "Bomb", created = 1, modified = 2, theme = theme).toJson().toByteArray()
        val big = ByteArray(20 * 1024 * 1024)
        val bomb = zip("theme.json" to json, *hashes.map { "textures/$it.png" to big }.toTypedArray())
        assertTrue(bomb.size < 1024 * 1024, "the archive itself is small")
        val e = assertFailsWith<ThemeFormatException> { ThemeZipImport.read(bomb) { false } }
        assertTrue("too large" in e.message!!, e.message)
        val one = zip("theme.json" to json, "textures/${hashes[0]}.png" to ByteArray(ThemeZipImport.MAX_TEXTURE + 1))
        assertTrue("too large" in assertFailsWith<ThemeFormatException> { ThemeZipImport.read(one) { false } }.message!!)
    }

    private val key by lazy {
        dev.iconoclast.pack.PackKeyStore(java.nio.file.Files.createTempDirectory("zipkey").toFile(), "Zip Pack").let { it.loadOrCreate(); it.backup()!! }
    }

    /** Export v2: the key entries only when a key is passed, after the textures, read back whole. */
    @Test
    fun keyEntriesOnlyWhenAsked() {
        val d = doc(GoldenSet.LOOKS.keys.first())
        val plain = entries(ThemeZipExport.write(d, sourceStore()))
        assertTrue(plain.keys.none { it.startsWith("key/") })
        assertEquals(null, ThemeZipImport.read(ThemeZipExport.write(d, sourceStore())) { false }.key)
        val withKey = ThemeZipExport.write(d, sourceStore(), key)
        assertEquals(plain.keys.toList() + listOf("key/pack.p12", "key/password"), entries(withKey).keys.toList())
        assertContentEquals(withKey, ThemeZipExport.write(d, sourceStore(), key), "deterministic")
        val skipped = ThemeZipImport.read(withKey) { false }
        assertEquals(null, skipped.key, "unread without readKey")
        assertTrue(skipped.hasKey)
        val read = ThemeZipImport.read(withKey, readKey = true) { false }.key!!
        assertContentEquals(key.p12, read.p12)
        assertContentEquals(key.password, read.password)
    }

    @Test
    fun aHalfOrDamagedOrOversizedKeyIsRefused() {
        val json = doc(GoldenSet.LOOKS.keys.first()).copy(theme = Theme()).toJson().toByteArray()
        val bad = listOf(
            zip("theme.json" to json, "key/pack.p12" to key.p12),
            zip("theme.json" to json, "key/password" to key.password),
            zip("theme.json" to json, "key/pack.p12" to key.p12, "key/password" to "wrong".toByteArray()),
            zip("theme.json" to json, "key/pack.p12" to ByteArray(ThemeZipImport.MAX_KEY_STORE + 1), "key/password" to key.password),
        )
        for (z in bad) {
            assertFailsWith<ThemeFormatException> { ThemeZipImport.read(z, readKey = true) { false } }
            // Not restoring the key: the damaged entries are ignored and the theme imports.
            val theme = ThemeZipImport.read(z) { false }
            assertEquals(null, theme.key)
            assertTrue(theme.hasKey)
        }
    }

    /** A different key on the phone: an error before anything is stored, and the phone keeps its key. */
    @Test
    fun aDifferentLocalKeyStopsTheImport() = kotlinx.coroutines.runBlocking {
        fun tmp() = java.nio.file.Files.createTempDirectory("imp").toFile()
        val keys = dev.iconoclast.pack.PackKeyStore(tmp(), "Mine").apply { loadOrCreate() }
        val mine = keys.backup()!!
        val textures = dev.iconoclast.app.texture.TextureFiles(tmp())
        val themes = ThemeRepository(tmp())
        val archive = ThemeZipImport.read(ThemeZipExport.write(doc(GoldenSet.LOOKS.entries.first { GoldenSet.TEXTURE in it.value.textureHashes() }.key), sourceStore(), key), readKey = true) { false }
        assertFailsWith<ThemeFormatException> { ArchiveImport.store(archive, restoreKey = true, keys, textures, themes) }
        assertEquals(emptyList(), themes.themes.value)
        assertEquals(emptyList(), textures.recent())
        assertContentEquals(mine.p12, keys.backup()!!.p12)
        // Without the restore the theme comes in and the key is left alone.
        val result = ArchiveImport.store(archive, restoreKey = false, keys, textures, themes)
        assertEquals(null, result.key)
        assertEquals(1, themes.themes.value.size)
        assertContentEquals(mine.p12, keys.backup()!!.p12)
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
