package dev.iconoclast.templatepack

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The template must be the same bytes on every builder. Nothing in it is deflated by the
 * build JDK: aapt2's entries are copied raw and classes.dex is stored.
 */
class TemplateReproducibleTest {
    private fun file(key: String) = File(requireNotNull(System.getProperty(key)) { "missing $key" })

    @Test
    fun twoBuildsAreByteIdentical() {
        val a = file("template.apk").readBytes()
        val b = file("template.again.apk").readBytes()
        assertTrue(a.isNotEmpty())
        assertContentEquals(a, b)
    }

    @Test
    fun aapt2EntriesAreCopiedRawAndTheDexIsStored() {
        ZipFile(file("template.apk")).use { t ->
            ZipFile(file("template.linked.apk")).use { linked ->
                val names = linked.entries().toList().map { it.name }
                assertEquals(names + "classes.dex", t.entries().toList().map { it.name })
                for (e in linked.entries()) {
                    val o = t.getEntry(e.name)
                    assertEquals(e.method, o.method, e.name)
                    assertEquals(e.crc, o.crc, e.name)
                    assertEquals(e.compressedSize, o.compressedSize, e.name)
                }
                assertTrue(linked.entries().toList().any { it.method == ZipEntry.DEFLATED })
            }
            assertEquals(ZipEntry.STORED, t.getEntry("classes.dex").method)
        }
    }
}
