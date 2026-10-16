package dev.iconoclast.app

import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BuiltPackTest {
    private val doc = ThemeDocument(id = "a", name = "Jade", created = 1, modified = 2, theme = Theme())
    private val plan = CoveragePlan(listOf(3, 7), listOf("p/p.A" to 0, "q/q.B" to 1), emptyList())
    private val template = byteArrayOf(1, 2, 3)
    private val cert = byteArrayOf(9)

    private fun key(
        d: ThemeDocument = doc, p: CoveragePlan = plan, r: OutputResolution = OutputResolution.FULL,
        t: ByteArray = template, c: ByteArray = cert, b: String = "1@100", n: String = "x.pack.g000001",
    ) = BuiltPack.key(d, p, r, t, c, b, n)

    @Test
    fun nameIdAndDatesDoNotChangeTheRecipe() {
        assertEquals(key(), key(doc.copy(id = "b", name = "Other", created = 5, modified = 6)))
    }

    @Test
    fun everythingTheBytesDependOnChangesTheRecipe() {
        val base = key()
        assertNotEquals(base, key(doc.copy(theme = Theme(shape = ShapeParams(strokeWeight = 2f)))))
        assertNotEquals(base, key(doc.copy(overrides = mapOf("camera" to IconOverride(strokeWeight = 2f)))))
        assertNotEquals(base, key(p = CoveragePlan(listOf(3, 8), plan.items, emptyList())))
        assertNotEquals(base, key(p = CoveragePlan(plan.drawables, listOf("p/p.A" to 1, "q/q.B" to 0), emptyList())))
        assertNotEquals(base, key(r = OutputResolution.COMPACT))
        assertNotEquals(base, key(t = byteArrayOf(1, 2, 4)))
        assertNotEquals(base, key(c = byteArrayOf(8)))
        assertNotEquals(base, key(b = "1@200"), "an update of the designer draws anew")
        assertNotEquals(base, key(n = "x.pack.g000002"), "each pack name is its own build")
    }

    @Test
    fun aKeptPackIsFoundOnlyByItsOwnRecipe() {
        val dir = Files.createTempDirectory("built").toFile()
        val built = BuiltPack(dir)
        assertNull(built.find(key()))
        val signed = dir.resolve("pack.apk").apply { writeBytes(byteArrayOf(42)) }
        val kept = built.keep(key(), signed)
        assertEquals(kept, built.find(key()))
        assertEquals(listOf<Byte>(42), kept.readBytes().toList())
        assertNull(built.find(key(r = OutputResolution.COMPACT)))
        // A newer pack replaces it.
        built.keep(key(r = OutputResolution.COMPACT), dir.resolve("pack.apk").apply { writeBytes(byteArrayOf(43)) })
        assertNull(built.find(key()))
        dir.deleteRecursively()
    }
}
