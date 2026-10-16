package dev.iconoclast.pack

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackNameTest {
    private val base = requireNotNull(System.getProperty("pack.base"))
    private val dir: File = Files.createTempDirectory("packname").toFile().apply { deleteOnExit() }

    @Test
    fun namesAreFixedLengthAndRoundTrip() {
        val names = listOf(0, 1, 42, PackName.MAX).map { PackName.of(base, it) }
        assertEquals("$base.g000000", PackName.placeholder(base))
        assertEquals("$base.g000042", names[2])
        assertEquals(1, names.map { it.length }.toSet().size)
        assertEquals(listOf(0, 1, 42, PackName.MAX), names.map { PackName.generationOf(base, it) })
        assertFailsWith<IllegalArgumentException> { PackName.of(base, PackName.MAX + 1) }
    }

    @Test
    fun theLegacyPackIsOursAndOtherPackagesAreNot() {
        assertEquals(0, PackName.generationOf(base, base))
        for (other in listOf("$base.g12", "$base.g0000001", "$base.gabcdef", "$base.x000001", "${base}x", "dev.other.pack", base.substringBeforeLast('.'))) {
            assertNull(PackName.generationOf(base, other), other)
        }
    }

    @Test
    fun theNextGenerationIsAboveTheLastAndEveryInstalledPack() {
        assertEquals(1, PackName.next(0, emptyList()))
        assertEquals(1, PackName.next(0, listOf(0)), "the legacy pack counts as 0")
        assertEquals(6, PackName.next(5, listOf(3)))
        assertEquals(10, PackName.next(2, listOf(0, 9)), "data cleared: a pack still installed is never reused")
        assertFailsWith<IllegalStateException> { PackName.next(PackName.MAX, emptyList()) }
    }

    @Test
    fun patchReplacesExactlyOneOccurrenceInEitherEncoding() {
        val utf16 = "xx".toByteArray(Charsets.UTF_16LE) + "a.b.g000000".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        assertContentEquals(
            "xx".toByteArray(Charsets.UTF_16LE) + "a.b.g000123".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0),
            PackName.patch(utf16, "a.b.g000000", "a.b.g000123"),
        )
        val utf8 = "<a.b.g000000>".toByteArray()
        assertContentEquals("<a.b.g000007>".toByteArray(), PackName.patch(utf8, "a.b.g000000", "a.b.g000007"))
        assertFailsWith<IllegalArgumentException> { PackName.patch("none".toByteArray(), "a.b.g000000", "a.b.g000001") }
        assertFailsWith<IllegalArgumentException> { PackName.patch(utf8 + utf8, "a.b.g000000", "a.b.g000001") }
        assertFailsWith<IllegalArgumentException> { PackName.patch(utf8, "a.b.g000000", "a.b.g1") }
    }

    /** The real template: renamed manifest and arsc still parse, and only the package name changed. */
    @Test
    fun theRenamedTemplateIsAValidPackUnderTheNewName() {
        val template = templateBytes()
        val placeholder = PackName.placeholder(base)
        val name = PackName.of(base, 314)
        val renamed = listOf("AndroidManifest.xml", "resources.arsc").associateWith {
            PackName.patch(ZipRewriter.entryBytes(template, it), placeholder, name)
        }
        val out = rewriteToFile(template, renamed, dir, File(dir, "renamed.apk"))
        assertZipaligned(out)

        val badging = aapt2("dump", "badging", out.path)
        assertTrue("package: name='$name'" in badging, badging.lines().first())
        assertTrue("application-label:'Iconoclast Pack'" in badging, "label kept")
        assertTrue("org.adw.launcher.THEMES" in aapt2("dump", "xmltree", "--file", "AndroidManifest.xml", out.path))
        val resources = aapt2("dump", "resources", out.path)
        assertTrue("Package name=$name" in resources, resources.lines().take(3).joinToString("\n"))
        assertTrue("drawable/slot_0000" in resources)

        // Nothing else in either file moved: same size, and only the name's bytes differ.
        val original = aapt2("dump", "badging", File(dir, "template.apk").apply { writeBytes(template) }.path)
        assertEquals(original.replace(placeholder, name), badging)
        ZipFile(out).use { z ->
            for ((entry, bytes) in renamed) assertContentEquals(bytes, z.getInputStream(z.getEntry(entry)).readBytes())
        }
    }

    @Test
    fun entryBytesInflatesTheDeflatedManifest() {
        val template = templateBytes()
        ZipFile(File(requireNotNull(System.getProperty("template.apk")))).use { z ->
            for (name in listOf("AndroidManifest.xml", "resources.arsc", "assets/NOTICE.txt")) {
                assertContentEquals(z.getInputStream(z.getEntry(name)).readBytes(), ZipRewriter.entryBytes(template, name), name)
            }
        }
    }

    private fun aapt2(vararg args: String): String {
        val p = ProcessBuilder(listOf(System.getProperty("aapt2")) + args).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        assertEquals(0, p.waitFor(), text.take(500))
        return text
    }
}
