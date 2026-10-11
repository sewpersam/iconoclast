package dev.iconoclast.pack

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

    private fun rewrite(template: ByteArray, replacements: Map<String, ByteArray>, name: String) =
        rewriteToFile(template, replacements, dir, File(dir, name))

    @Test
    fun rewrittenTemplatePassesZipalign() {
        val out = rewrite(templateBytes(), replacements, "aligned.apk")
        val report = assertZipaligned(out)
        assertTrue("Verification succesful" in report || "Verification successful" in report, report.takeLast(200))
    }

    @Test
    fun keepsOrderAndContentAndStoresReplacements() {
        val template = write(templateBytes(), "template.apk")
        val out = rewrite(template.readBytes(), replacements, "out.apk")
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
        val a = rewrite(templateBytes(), replacements, "a.apk").readBytes()
        val b = rewrite(templateBytes(), replacements.entries.reversed().associate { it.toPair() }, "b.apk").readBytes()
        assertContentEquals(a, b)
    }

    @Test
    fun rejectsAnEntryTheTemplateDoesNotHave() {
        val e = assertFailsWith<IllegalArgumentException> {
            rewrite(templateBytes(), mapOf("res/drawable-nodpi-v4/slot_1024.png" to byteArrayOf(1)), "missing.apk")
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
        assertTrue(entryFlags(src.toByteArray()).any { (local, _) -> local and 0x8 != 0 }, "input must use data descriptors")
        val out = rewrite(src.toByteArray(), mapOf("c.txt" to "new".toByteArray()), "small.zip")
        assertZipaligned(out)
        for ((local, central) in entryFlags(out.readBytes())) {
            assertEquals(0, local and 0x8, "local header data descriptor bit")
            assertEquals(0, central and 0x8, "central directory data descriptor bit")
        }
        ZipFile(out).use { z ->
            assertEquals(listOf("a.txt", "b.bin", "c.txt"), z.entries().toList().map { it.name })
            assertEquals("a".repeat(500), z.getInputStream(z.getEntry("a.txt")).readBytes().decodeToString())
            assertEquals("stored!", z.getInputStream(z.getEntry("b.bin")).readBytes().decodeToString())
            assertEquals("new", z.getInputStream(z.getEntry("c.txt")).readBytes().decodeToString())
        }
    }

    /** (local header flags, central directory flags) of every entry, read from the raw bytes. */
    private fun entryFlags(zip: ByteArray): List<Pair<Int, Int>> {
        val b = java.nio.ByteBuffer.wrap(zip).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val end = (zip.size - 22 downTo 0).first { b.getInt(it) == 0x06054b50 }
        var p = b.getInt(end + 16)
        return List(b.getShort(end + 10).toInt() and 0xFFFF) {
            check(b.getInt(p) == 0x02014b50)
            val central = b.getShort(p + 8).toInt() and 0xFFFF
            val local = b.getShort(b.getInt(p + 42) + 6).toInt() and 0xFFFF
            p += 46 + (b.getShort(p + 28).toInt() and 0xFFFF) + (b.getShort(p + 30).toInt() and 0xFFFF) + (b.getShort(p + 32).toInt() and 0xFFFF)
            local to central
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
