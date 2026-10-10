package dev.iconoclast.render.format

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * Component string to drawable index, sorted by UTF-8 bytes for binary search.
 * Layout is documented in docs/bundle-format.md.
 */
object ComponentTableFormat {
    const val MAGIC = 0x49434e43 // "ICNC"
    const val VERSION = 1
    const val HEADER_SIZE = 12

    fun encode(entries: Map<String, Int>): ByteArray {
        val sorted = entries.entries
            .map { it.key.toByteArray(Charsets.UTF_8) to it.value }
            .sortedWith { a, b -> compareUnsigned(a.first, b.first) }
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeShort(VERSION)
        data.writeShort(0)
        data.writeInt(sorted.size)
        val poolStart = HEADER_SIZE + sorted.size * 6
        var offset = poolStart
        for ((key, _) in sorted) {
            data.writeInt(offset)
            offset += 2 + key.size
        }
        for ((_, drawable) in sorted) {
            require(drawable in 0..0xFFFF) { "drawable index $drawable does not fit in u16" }
            data.writeShort(drawable)
        }
        for ((key, _) in sorted) {
            require(key.size <= 0xFFFF) { "component too long" }
            data.writeShort(key.size)
            data.write(key)
        }
        data.flush()
        return out.toByteArray()
    }

    internal fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}

/** Reads a component table in place. Lookups allocate only the query bytes. */
class ComponentTable(private val buf: ByteBuffer) {
    val size: Int

    init {
        require(buf.getInt(0) == ComponentTableFormat.MAGIC) { "not a component table" }
        val version = buf.getShort(4).toInt() and 0xFFFF
        require(version == ComponentTableFormat.VERSION) { "unsupported table version $version" }
        size = buf.getInt(8)
    }

    /** Drawable index for [component], or -1. */
    fun lookup(component: String): Int {
        val key = component.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareAt(mid, key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return buf.getShort(ComponentTableFormat.HEADER_SIZE + size * 4 + mid * 2).toInt() and 0xFFFF
            }
        }
        return -1
    }

    private fun compareAt(i: Int, key: ByteArray): Int {
        val pos = buf.getInt(ComponentTableFormat.HEADER_SIZE + i * 4)
        val len = buf.getShort(pos).toInt() and 0xFFFF
        val n = minOf(len, key.size)
        for (j in 0 until n) {
            val d = (buf.get(pos + 2 + j).toInt() and 0xFF) - (key[j].toInt() and 0xFF)
            if (d != 0) return d
        }
        return len - key.size
    }
}
