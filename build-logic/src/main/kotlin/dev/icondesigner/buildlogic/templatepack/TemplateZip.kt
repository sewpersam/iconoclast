package dev.icondesigner.buildlogic.templatepack

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Writes the template zip without deflating anything on the JVM, whose zlib differs between
 * builders (zlib-ng on Fedora, stock zlib on Debian). Entries aapt2 produced keep its
 * compressed bytes unchanged. Entries the task adds are stored. Every entry gets the same
 * fixed DOS time, and no data descriptors, so the bytes depend only on the inputs.
 */
internal object TemplateZip {
    class RawEntry(val name: String, val method: Int, val crc: Int, val compressedSize: Int, val size: Int, val data: ByteArray)

    const val STORED = 0
    const val DEFLATED = 8

    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val END_SIG = 0x06054b50
    private const val UTF8_FLAG = 0x0800

    /** 1981-01-01 00:00 in DOS date and time fields. */
    private const val DOS_DATE = (1 shl 9) or (1 shl 5) or 1
    private const val DOS_TIME = 0

    /** The entries of [linked], raw, in file order, then [added], stored. */
    fun assemble(linked: ByteArray, added: List<Pair<String, ByteArray>>): ByteArray {
        val entries = readEntries(linked) + added.map { (name, data) ->
            RawEntry(name, STORED, CRC32().apply { update(data) }.value.toInt(), data.size, data.size, data)
        }
        val names = entries.map { it.name }
        require(names.size == names.toSet().size) { "duplicate entry in $names" }

        val out = Output()
        val central = Output()
        for (e in entries) {
            val name = e.name.toByteArray(Charsets.UTF_8)
            val version = if (e.method == STORED) 10 else 20
            val offset = out.size()
            out.int(LOCAL_SIG); out.short(version); out.short(UTF8_FLAG); out.short(e.method)
            out.short(DOS_TIME); out.short(DOS_DATE); out.int(e.crc); out.int(e.compressedSize); out.int(e.size)
            out.short(name.size); out.short(0); out.bytes(name); out.bytes(e.data)

            central.int(CENTRAL_SIG); central.short(20); central.short(version); central.short(UTF8_FLAG)
            central.short(e.method); central.short(DOS_TIME); central.short(DOS_DATE)
            central.int(e.crc); central.int(e.compressedSize); central.int(e.size)
            central.short(name.size); central.short(0); central.short(0); central.short(0)
            central.short(0); central.int(0); central.int(offset)
            central.bytes(name)
        }
        val centralOffset = out.size()
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.int(END_SIG); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
        out.int(centralBytes.size); out.int(centralOffset); out.short(0)
        return out.toByteArray()
    }

    /** Every entry of [zip] with its compressed bytes, in local header order. */
    fun readEntries(zip: ByteArray): List<RawEntry> {
        val src = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        val end = (zip.size - 22 downTo maxOf(0, zip.size - 22 - 0xFFFF))
            .first { src.getInt(it) == END_SIG && it + 22 + u16(src, it + 20) == zip.size }
        val count = u16(src, end + 10)
        var p = src.getInt(end + 16)
        val found = ArrayList<Pair<Int, RawEntry>>(count)
        repeat(count) {
            require(src.getInt(p) == CENTRAL_SIG) { "bad central directory record at $p" }
            val nameLen = u16(src, p + 28)
            val name = String(zip, p + 46, nameLen, Charsets.UTF_8)
            val method = u16(src, p + 10)
            require(method == STORED || method == DEFLATED) { "$name: method $method" }
            val compressedSize = src.getInt(p + 20)
            val local = src.getInt(p + 42)
            require(src.getInt(local) == LOCAL_SIG) { "bad local header for $name" }
            val start = local + 30 + u16(src, local + 26) + u16(src, local + 28)
            val data = zip.copyOfRange(start, start + compressedSize)
            found += local to RawEntry(name, method, src.getInt(p + 16), compressedSize, src.getInt(p + 24), data)
            p += 46 + nameLen + u16(src, p + 30) + u16(src, p + 32)
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun u16(b: ByteBuffer, at: Int) = b.getShort(at).toInt() and 0xFFFF

    private class Output : ByteArrayOutputStream() {
        fun short(v: Int) { write(v); write(v ushr 8) }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        fun bytes(b: ByteArray) = write(b, 0, b.size)
    }
}
