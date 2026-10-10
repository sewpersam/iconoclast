package dev.icondesigner.pack

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipRewriterTest {
    private val dir: File = Files.createTempDirectory("rewriter").toFile().apply { deleteOnExit() }

    /** Odd sizes, so a writer that does not pad would misalign the entries after them. */
    private val replacements = mapOf(
        "res/drawable-nodpi-v4/slot_0000.png" to ByteArray(1001) { (it * 7).toByte() },
        "res/drawable-nodpi-v4/slot_0005.png" to byteArrayOf(1, 2, 3),
        "res/drawable-nodpi-v4/pack_icon.png" to ByteArray(77) { 9 },
        "assets/appfilter.xml" to AppfilterWriter.write(listOf("a.b/a.b.C" to "slot_0000")).toByteArray(),
        "assets/drawable.xml" to DrawableXmlWriter.write(listOf("slot_0000")).toByteArray(),
    )

    private fun write(bytes: ByteArray, name: String) = File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun rewrittenTemplatePassesZipalign() {
        val out = write(ZipRewriter.rewrite(templateBytes(), replacements), "aligned.apk")
        val report = assertZipaligned(out)
        assertTrue("Verification succesful" in report || "Verification successful" in report, report.takeLast(200))
    }

    @Test
    fun keepsOrderAndContentAndStoresReplacements() {
        val template = write(templateBytes(), "template.apk")
        val out = write(ZipRewriter.rewrite(template.readBytes(), replacements), "out.apk")
        ZipFile(template).use { t ->
            ZipFile(out).use { o ->
                assertEquals(t.entries().toList().map { it.name }, o.entries().toList().map { it.name })
                for (e in o.entries()) {
                    val bytes = o.getInputStream(e).readBytes()
                    val replaced = replacements[e.name]
                    if (replaced != null) {
                        assertContentEquals(replaced, bytes, e.name)
                        assertEquals(ZipEntry.STORED, e.method, e.name)
                    } else {
                        assertContentEquals(t.getInputStream(t.getEntry(e.name)).readBytes(), bytes, e.name)
                        assertEquals(t.getEntry(e.name).method, e.method, e.name)
                    }
                }
                assertEquals(ZipEntry.STORED, o.getEntry("resources.arsc").method)
                assertEquals(ZipEntry.STORED, o.getEntry("res/drawable-nodpi-v4/slot_1023.png").method)
            }
        }
    }

    @Test
    fun sameInputsGiveTheSameBytes() {
        val a = ZipRewriter.rewrite(templateBytes(), replacements)
        val b = ZipRewriter.rewrite(templateBytes(), replacements.entries.reversed().associate { it.toPair() })
        assertContentEquals(a, b)
    }

    @Test
    fun rejectsAnEntryTheTemplateDoesNotHave() {
        val e = assertFailsWith<IllegalArgumentException> {
            ZipRewriter.rewrite(templateBytes(), mapOf("res/drawable-nodpi-v4/slot_1024.png" to byteArrayOf(1)))
        }
        assertTrue("slot_1024" in e.message!!)
    }

    @Test
    fun dropsMetaInfAndHandlesDataDescriptors() {
        // ZipOutputStream writes deflated entries with data descriptors after the data.
        val src = ByteArrayOutputStream()
        ZipOutputStream(src).use { z ->
            z.putNextEntry(ZipEntry("META-INF/MANIFEST.MF")); z.write("x".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("a.txt")); z.write(ByteArray(500) { 'a'.code.toByte() }); z.closeEntry()
            val stored = "stored!".toByteArray()
            z.putNextEntry(ZipEntry("b.bin").apply {
                method = ZipEntry.STORED; size = stored.size.toLong(); crc = CRC32().apply { update(stored) }.value
            })
            z.write(stored); z.closeEntry()
            z.putNextEntry(ZipEntry("c.txt")); z.write("old".toByteArray()); z.closeEntry()
        }
        val out = write(ZipRewriter.rewrite(src.toByteArray(), mapOf("c.txt" to "new".toByteArray())), "small.zip")
        assertZipaligned(out)
        ZipFile(out).use { z ->
            assertEquals(listOf("a.txt", "b.bin", "c.txt"), z.entries().toList().map { it.name })
            assertEquals("a".repeat(500), z.getInputStream(z.getEntry("a.txt")).readBytes().decodeToString())
            assertEquals("stored!", z.getInputStream(z.getEntry("b.bin")).readBytes().decodeToString())
            assertEquals("new", z.getInputStream(z.getEntry("c.txt")).readBytes().decodeToString())
        }
    }

    @Test
    fun alignmentExtraFieldPadsToTheBoundary() {
        for (offset in 0L..11L) {
            val extra = ZipAlignment.extraField(offset, 4)
            assertEquals(0L, (offset + extra.size) % 4, "offset $offset")
            if (extra.isNotEmpty()) assertEquals(0xD935, (extra[0].toInt() and 0xFF) or ((extra[1].toInt() and 0xFF) shl 8))
        }
    }
}
