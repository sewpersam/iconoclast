package dev.iconoclast.app.theme

import dev.iconoclast.app.texture.TextureFiles
import dev.iconoclast.pack.PackKeyStore
import dev.iconoclast.pack.RestoreResult
import dev.iconoclast.pack.backup
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.texture.TextureStore
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The M7 gate, spec 5: export and import round-trip with byte-identical render output. Every
 * golden theme is exported with its textures and the signing key, read back, stored by
 * the import path into an empty install (new texture directory, new theme directory, no
 * key), and rendered at its pack size, 432 or 324, from the imported document: every
 * golden sample must match its committed golden byte for byte.
 */
class ExportRoundTripGateTest {
    private val golden = File(requireNotNull(System.getProperty("golden.dir")))
    private val bundle = IconBundle(ByteBuffer.wrap(File(golden, "samples.bin").readBytes()))
    private fun tmp(name: String): File = Files.createTempDirectory(name).toFile().apply { deleteOnExit() }

    private val from = TextureStore().also { assertEquals(GoldenSet.TEXTURE, it.put(File(golden, GoldenSet.TEXTURE_FILE).readBytes())) }
    private val key = PackKeyStore(tmp("key-old"), "Old Pack").apply { loadOrCreate() }.backup()!!

    /** A fresh install: textures, themes and key store all empty. */
    private inner class Install {
        val textures = TextureFiles(tmp("textures"))
        val themes = ThemeRepository(tmp("themes"))
        val keys = PackKeyStore(tmp("key-new"), "New Pack")

        fun import(zip: ByteArray): ImportResult = runBlocking {
            val archive = ThemeZipImport.read(zip, readKey = true) { false }
            ArchiveImport.store(archive, restoreKey = true, keys, textures, themes)
        }
    }

    @Test
    fun everyGoldenThemeRoundTripsToItsGoldenBytes() {
        val install = Install()
        val renderer = IconRenderer(textures = install.textures.store)
        val cases = (GoldenSet.CASES + GoldenSet.CASES_324).groupBy({ it.first to it.second }, { it.third })
        val mismatches = mutableListOf<String>()
        var first = true
        for ((dirTheme, samples) in cases) {
            val (dir, theme) = dirTheme
            val doc = ThemeDocument(id = "gate-$dir", name = dir, created = 1, modified = 2, theme = theme)
            val result = install.import(ThemeZipExport.write(doc, from, key))
            assertEquals(if (first) RestoreResult.RESTORED else RestoreResult.ALREADY_PRESENT, result.key, dir)
            first = false
            val imported = result.doc
            assertEquals(theme, imported.theme, dir)
            assertEquals(emptySet(), install.textures.load(imported.theme.textureHashes()), "$dir textures stored")
            val res = if (dir.startsWith("324/")) OutputResolution.COMPACT else OutputResolution.FULL
            for (name in samples) {
                val png = renderer.renderPng(name, bundle.icon(GoldenSet.SAMPLES.indexOf(name)), name, imported.theme, res.px, res)
                if (!png.contentEquals(File(golden, "$dir/$name.png").readBytes())) mismatches += "$dir/$name"
            }
        }
        assertEquals(key.open().certificate, install.keys.loadOrCreate().certificate, "the key came across")
        assertTrue(cases.size >= 10, "${cases.size} golden themes")
        if (mismatches.isNotEmpty()) fail("${mismatches.size} renders differ after the round trip: ${mismatches.take(10)}")
    }

    /** Overrides travel too: every slot's effective look renders the same bytes before and after. */
    @Test
    fun overridesRoundTripToTheSameBytes() {
        val theme: Theme = GoldenSet.JADE_LIKE
        val doc = ThemeDocument(id = "o", name = "Overrides", created = 1, modified = 2, theme = theme)
            .withOverride("fdroid", IconOverride(strokeWeight = 2.5f, gapClosing = 1f))
            .withOverride("camera", IconOverride(fillMode = FillMode.SILHOUETTE, hideStreaks = true))
            .withOverride("signal", IconOverride(textureOffset = TextureOffset(0.125f, 0.875f)))
            .withOverride("spotify", IconOverride(swapDrawable = "weather"))
            .withOverride("not_in_this_pin", IconOverride(strokeWeight = 3f))
        val install = Install()
        val imported = install.import(ThemeZipExport.write(doc, from)).doc
        assertEquals(doc.overrides, imported.overrides)
        assertEquals(null, install.keys.backup(), "no key in the archive, none restored")
        val before = IconRenderer(textures = from)
        val after = IconRenderer(textures = install.textures.store)
        for (name in GoldenSet.LOOK_SAMPLES) {
            fun render(r: IconRenderer, d: ThemeDocument): ByteArray {
                val g = d.effectiveGeometry(name)
                return r.renderPng(g, bundle.icon(GoldenSet.SAMPLES.indexOf(g)), name, d.effectiveTheme(name), OUTPUT_SIZE)
            }
            assertContentEquals(render(before, doc), render(after, imported), name)
        }
    }
}
