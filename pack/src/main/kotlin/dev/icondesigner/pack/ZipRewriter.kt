package dev.icondesigner.pack

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * The on-phone step of spec 1.2 and 3.5: a plain zip rewrite of the template.
 *
 * Entries keep their template order. `META-INF/` is dropped. Each replaced entry gets
 * the new bytes, stored uncompressed. Untouched entries are copied byte for byte,
 * compressed or not. Every STORED entry's data is aligned to 4 bytes. The output depends
 * only on the template and the replacements, so it is deterministic.
 */
object ZipRewriter {
    const val ALIGNMENT = 4

    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val END_SIG = 0x06054b50
    private const val STORED = 0
    private const val UTF8_FLAG = 0x0800

    private class Entry(
        val name: ByteArray,
        val versionMadeBy: Int,
        val versionNeeded: Int,
        val flags: Int,
        val method: Int,
        val time: Int,
        val date: Int,
        val crc: Int,
        val compressedSize: Int,
        val size: Int,
        val internalAttrs: Int,
        val externalAttrs: Int,
        val localOffset: Int,
    ) {
        val nameString get() = String(name, Charsets.UTF_8)
    }

    /**
     * Rewrites [template]. Every key of [replacements] must name an entry of the template,
     * so a slot that does not exist fails loudly instead of being silently dropped.
     */
    fun rewrite(template: ByteArray, replacements: Map<String, ByteArray>): ByteArray {
        val src = ByteBuffer.wrap(template).order(ByteOrder.LITTLE_ENDIAN)
        val entries = readCentralDirectory(src).sortedBy { it.localOffset }
        val missing = replacements.keys - entries.map { it.nameString }.toSet()
        require(missing.isEmpty()) { "not in the template: ${missing.sorted().take(5)}" }

        val out = Output()
        val central = Output()
        var count = 0
        for (e in entries) {
            val name = e.nameString
            if (name.startsWith("META-INF/")) continue
            val replacement = replacements[name]
            val method: Int
            val crc: Int
            val compressedSize: Int
            val size: Int
            val data: ByteBuffer
            if (replacement != null) {
                method = STORED
                crc = CRC32().apply { update(replacement) }.value.toInt()
                compressedSize = replacement.size
                size = replacement.size
                data = ByteBuffer.wrap(replacement)
            } else {
                method = e.method
                crc = e.crc
                compressedSize = e.compressedSize
                size = e.size
                data = localData(src, e)
            }
            val flags = e.flags and UTF8_FLAG
            val offset = out.size
            val extra = if (method == STORED) {
                ZipAlignment.extraField(offset + 30L + e.name.size, ALIGNMENT)
            } else {
                ByteArray(0)
            }

            out.int(LOCAL_SIG); out.short(e.versionNeeded); out.short(flags); out.short(method)
            out.short(e.time); out.short(e.date); out.int(crc); out.int(compressedSize); out.int(size)
            out.short(e.name.size); out.short(extra.size); out.bytes(e.name); out.bytes(extra)
            out.buffer(data)

            central.int(CENTRAL_SIG); central.short(e.versionMadeBy); central.short(e.versionNeeded)
            central.short(flags); central.short(method); central.short(e.time); central.short(e.date)
            central.int(crc); central.int(compressedSize); central.int(size)
            central.short(e.name.size); central.short(0); central.short(0); central.short(0)
            central.short(e.internalAttrs); central.int(e.externalAttrs); central.int(offset.toInt())
            central.bytes(e.name)
            count++
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.int(END_SIG); out.short(0); out.short(0); out.short(count); out.short(count)
        out.int(centralBytes.size); out.int(centralOffset.toInt()); out.short(0)
        return out.toByteArray()
    }

    /** Entry names of [zip] in central directory order. */
    fun entryNames(zip: ByteArray): List<String> =
        readCentralDirectory(ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)).map { it.nameString }

    private fun readCentralDirectory(src: ByteBuffer): List<Entry> {
        val end = findEndRecord(src)
        val count = src.getShort(end + 10).toInt() and 0xFFFF
        var p = src.getInt(end + 16)
        val entries = ArrayList<Entry>(count)
        repeat(count) {
            require(src.getInt(p) == CENTRAL_SIG) { "bad central directory record at $p" }
            val nameLen = u16(src, p + 28)
            val name = ByteArray(nameLen).also { slice(src, p + 46, nameLen).get(it) }
            entries += Entry(
                name = name,
                versionMadeBy = u16(src, p + 4),
                versionNeeded = u16(src, p + 6),
                flags = u16(src, p + 8),
                method = u16(src, p + 10),
                time = u16(src, p + 12),
                date = u16(src, p + 14),
                crc = src.getInt(p + 16),
                compressedSize = src.getInt(p + 20),
                size = src.getInt(p + 24),
                internalAttrs = u16(src, p + 36),
                externalAttrs = src.getInt(p + 38),
                localOffset = src.getInt(p + 42),
            )
            p += 46 + nameLen + u16(src, p + 30) + u16(src, p + 32)
        }
        return entries
    }

    private fun findEndRecord(src: ByteBuffer): Int {
        val last = src.limit() - 22
        for (p in last downTo maxOf(0, last - 0xFFFF)) {
            if (src.getInt(p) == END_SIG && p + 22 + u16(src, p + 20) == src.limit()) return p
        }
        throw IllegalArgumentException("no end of central directory record")
    }

    private fun localData(src: ByteBuffer, e: Entry): ByteBuffer {
        val p = e.localOffset
        require(src.getInt(p) == LOCAL_SIG) { "bad local header for ${e.nameString}" }
        val start = p + 30 + u16(src, p + 26) + u16(src, p + 28)
        return slice(src, start, e.compressedSize)
    }

    private fun slice(src: ByteBuffer, start: Int, length: Int): ByteBuffer {
        val b = src.duplicate()
        b.position(start)
        b.limit(start + length)
        return b.slice()
    }

    private fun u16(b: ByteBuffer, at: Int) = b.getShort(at).toInt() and 0xFFFF

    private class Output : ByteArrayOutputStream() {
        val size: Long get() = count.toLong()
        fun short(v: Int) { write(v); write(v ushr 8) }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        fun bytes(b: ByteArray) = write(b, 0, b.size)
        fun buffer(b: ByteBuffer) {
            if (b.hasArray()) write(b.array(), b.arrayOffset() + b.position(), b.remaining())
            else bytes(ByteArray(b.remaining()).also { b.duplicate().get(it) })
        }
    }
}
