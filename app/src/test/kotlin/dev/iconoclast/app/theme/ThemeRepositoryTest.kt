package dev.iconoclast.app.theme

import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeRepositoryTest {
    private val dir: File = Files.createTempDirectory("themes").toFile()
    private var clock = 1000L
    private var ids = 0
    private val presets = File(requireNotNull(System.getProperty("presets.dir"))).let { d -> Presets.FILES.map { File(d, it).readText() } }

    private fun repo(presets: List<String> = this.presets) = ThemeRepository(dir, { presets }, now = { clock++ }, newId = { "id${ids++}" })

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun firstLoadSeedsPresetsOnce() = runBlocking {
        val first = repo().apply { load() }
        assertEquals(listOf("Plain Arcticons", "Jade", "Neon"), first.themes.value.map { it.name })
        assertEquals("preset-plain-arcticons", first.activeId.value)

        first.delete("preset-jade")
        val again = repo().apply { load() }
        assertEquals(listOf("preset-plain-arcticons", "preset-neon"), again.themes.value.map { it.id }, "a deleted preset stays deleted")
    }

    @Test
    fun crudPersists() = runBlocking {
        val repo = repo()
        val made = repo.create("  Thick ", Theme(shape = ShapeParams(strokeWeight = 2f)))
        assertEquals("Thick", made.name)
        val copy = repo.duplicate(made.id)!!
        assertEquals("Thick copy", copy.name)
        repo.rename(copy.id, "Thicker")
        repo.save(repo.get(copy.id)!!.copy(theme = Theme(shape = ShapeParams(strokeWeight = 3f))))
        repo.setActive(copy.id)

        val reread = repo().apply { load() }
        assertEquals(repo.themes.value, reread.themes.value)
        assertEquals(copy.id, reread.activeId.value)
        assertEquals(3f, reread.get(copy.id)!!.theme.shape.strokeWeight)
        assertEquals("Thicker", reread.get(copy.id)!!.name)
        assertTrue(File(dir, "${copy.id}.json").exists())
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") }, "temp files left: ${dir.list()!!.toList()}")
    }

    @Test
    fun deletingTheActiveThemeActivatesTheOldest() = runBlocking {
        val repo = repo()
        val made = repo.create("Mine", Theme())
        repo.setActive(made.id)
        repo.delete(made.id)
        assertEquals("preset-plain-arcticons", repo.activeId.value)
        assertTrue(!File(dir, "${made.id}.json").exists())
        for (t in repo.themes.value.toList()) repo.delete(t.id)
        assertNull(repo.activeId.value)
        assertNull(repo().apply { load() }.activeId.value)
    }

    @Test
    fun importTakesANewIdAndRejectsBadFiles() = runBlocking {
        val repo = repo().apply { load() }
        val jade = repo.get("preset-jade")!!
        val imported = repo.import(repo.get(jade.id)!!.toJson())
        assertTrue(imported.id != jade.id)
        assertEquals(jade.theme, imported.theme)
        assertEquals(4, repo.themes.value.size)
        val padded = repo.import(repo.get(jade.id)!!.toJson().replace("\"Jade\"", "\"  Jade two  \""))
        assertEquals("Jade two", padded.name)
        val blank = repo.import(repo.get(jade.id)!!.toJson().replace("\"Jade\"", "\"   \""))
        assertEquals("Untitled", blank.name)
        repo.delete(padded.id)
        repo.delete(blank.id)
        assertFailsWith<ThemeFormatException> { repo.import("{\"schemaVersion\": 1}") }
        assertEquals(4, repo.themes.value.size)
    }

    @Test
    fun aDamagedFileIsSkipped() = runBlocking {
        repo().load()
        File(dir, "broken.json").writeText("{ not json")
        val repo = repo().apply { load() }
        assertEquals(3, repo.themes.value.size)
        assertEquals(1, repo.unreadable.value.size)
        assertTrue(repo.unreadable.value.single().startsWith("broken.json: "), repo.unreadable.value.toString())
    }

    /** A preset added in an update reaches an install seeded before it. */
    @Test
    fun aNewPresetArrivesOnAnExistingInstall() = runBlocking {
        repo(presets.take(2)).load()
        assertEquals(listOf("preset-plain-arcticons", "preset-jade"), repo(presets.take(2)).apply { load() }.themes.value.map { it.id })
        val updated = repo().apply { load() }
        assertEquals(listOf("preset-plain-arcticons", "preset-jade", "preset-neon"), updated.themes.value.map { it.id })
        assertEquals(listOf("preset-plain-arcticons", "preset-jade", "preset-neon"), File(dir, "seeded").readLines())
    }

    /** Seeding never overwrites a document that holds the preset's id, edited or not. */
    @Test
    fun seedingNeverOverwritesAnExistingDocument() = runBlocking {
        val first = repo(presets.take(2)).apply { load() }
        first.rename("preset-jade", "My jade")
        val mine = migrate(presets[2]).copy(name = "My neon", created = 1, modified = 1)
        File(dir, "preset-neon.json").writeText(mine.toJson())
        val again = repo().apply { load() }
        assertEquals("My jade", again.get("preset-jade")!!.name)
        assertEquals("My neon", again.get("preset-neon")!!.name)
        assertEquals(listOf("preset-plain-arcticons", "preset-jade", "preset-neon"), File(dir, "seeded").readLines())
    }

    /**
     * A deleted preset is never resurrected, including on an install seeded before M6,
     * whose marker was a bare "1" for the two M4 presets: only the new preset arrives.
     */
    @Test
    fun deletedPresetsStayDeletedAcrossTheLegacyMarker() = runBlocking {
        dir.mkdirs()
        File(dir, "seeded").writeText("1\n")
        val repo = repo().apply { load() }
        assertEquals(listOf("preset-neon"), repo.themes.value.map { it.id })
        repo.delete("preset-neon")
        assertEquals(emptyList(), repo().apply { load() }.themes.value.map { it.id })
    }
}
