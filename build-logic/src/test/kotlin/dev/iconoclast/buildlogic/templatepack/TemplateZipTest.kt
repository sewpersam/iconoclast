package dev.iconoclast.buildlogic.templatepack

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TemplateZipTest {

    /** A stand-in for aapt2's output: one deflated and one stored entry, with data descriptors. */
    private fun linked(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("AndroidManifest.xml"))
            z.write("manifest ".repeat(50).toByteArray())
            z.closeEntry()
            val png = BuildTemplatePackTask.TRANSPARENT_PNG
            z.putNextEntry(ZipEntry("res/a.png").apply {
                method = ZipEntry.STORED; size = png.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(png) }.value
            })
            z.write(png)
            z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun copiesCompressedBytesRawAndStoresAddedEntries() {
        val input = linked()
        val dex = ByteArray(300) { (it % 7).toByte() }
        val out = TemplateZip.assemble(input, listOf("classes.dex" to dex))
        val before = TemplateZip.readEntries(input)
        val after = TemplateZip.readEntries(out)
        assertEquals(listOf("AndroidManifest.xml", "res/a.png", "classes.dex"), after.map { it.name })
        for ((a, b) in before.zip(after)) {
            assertEquals(a.method, b.method, a.name)
            assertContentEquals(a.data, b.data, a.name)
        }
        assertEquals(TemplateZip.DEFLATED, after[0].method)
        assertEquals(TemplateZip.STORED, after[2].method)
        assertContentEquals(dex, after[2].data)

        val file = File.createTempFile("template", ".zip").apply { deleteOnExit(); writeBytes(out) }
        ZipFile(file).use { z ->
            assertEquals("manifest ".repeat(50), z.getInputStream(z.getEntry("AndroidManifest.xml")).readBytes().decodeToString())
            assertContentEquals(dex, z.getInputStream(z.getEntry("classes.dex")).readBytes())
        }
        assertContentEquals(out, TemplateZip.assemble(input, listOf("classes.dex" to dex)))
    }

    @Test
    fun placeholderIsATransparentPixel() {
        val img = ImageIO.read(BuildTemplatePackTask.TRANSPARENT_PNG.inputStream())
        assertEquals(1, img.width)
        assertEquals(1, img.height)
        assertEquals(0, img.getRGB(0, 0) ushr 24)
    }
}
