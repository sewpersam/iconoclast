package dev.iconoclast.render.format

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * The icon bundle: every icon's primitives in one file, big-endian.
 * Layout is documented in docs/bundle-format.md.
 */
object BundleFormat {
    const val MAGIC = 0x49434e42 // "ICNB"
    const val VERSION = 1
    const val HEADER_SIZE = 12

    /** Fixed-point steps per canvas unit for coordinates (i16, range +-128 units). */
    const val COORD_SCALE = 256f
    /** Steps per canvas unit for stroke width (u16). */
    const val WIDTH_SCALE = 1024f
    /** Steps per unit for miter limit (u16). */
    const val MITER_SCALE = 64f

    fun encode(icons: List<Icon>): ByteArray {
        val records = icons.map(::encodeIcon)
        val tableSize = 4 * (icons.size + 1)
        val out = ByteArrayOutputStream(HEADER_SIZE + tableSize + records.sumOf { it.size })
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeShort(VERSION)
        data.writeShort(0)
        data.writeInt(icons.size)
        var offset = HEADER_SIZE + tableSize
        for (record in records) {
            data.writeInt(offset)
            offset += record.size
        }
        data.writeInt(offset)
        records.forEach(data::write)
        data.flush()
        return out.toByteArray()
    }

    private fun encodeIcon(icon: Icon): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeShort(u16(icon.primitives.size, "primitive count"))
        for (p in icon.primitives) {
            val points = p.verbs.sumOf { Verb.pointCount(it) }
            require(points * 2 == p.coords.size) { "coords do not match verbs" }
            data.writeByte(p.flags)
            data.writeShort(u16((p.strokeWidth * WIDTH_SCALE).roundToInt(), "stroke width"))
            data.writeShort(u16((p.miterLimit * MITER_SCALE).roundToInt(), "miter limit"))
            data.writeShort(u16(p.verbs.size, "verb count"))
            data.write(p.verbs)
            for (c in p.coords) {
                val q = (c * COORD_SCALE).roundToInt()
                require(q in Short.MIN_VALUE..Short.MAX_VALUE) { "coordinate $c out of range" }
                data.writeShort(q)
            }
        }
        data.flush()
        return out.toByteArray()
    }

    private fun u16(v: Int, what: String): Int {
        require(v in 0..0xFFFF) { "$what $v does not fit in u16" }
        return v
    }
}

/**
 * Reads a bundle in place. Decodes one icon per [icon] call, nothing up front.
 * Uses absolute reads only, so one instance is safe to share across threads.
 */
class IconBundle(private val buf: ByteBuffer) {
    val size: Int

    init {
        require(buf.getInt(0) == BundleFormat.MAGIC) { "not an icon bundle" }
        val version = buf.getShort(4).toInt() and 0xFFFF
        require(version == BundleFormat.VERSION) { "unsupported bundle version $version" }
        size = buf.getInt(8)
    }

    fun icon(index: Int): Icon {
        if (index !in 0 until size) throw IndexOutOfBoundsException("icon $index of $size")
        var pos = buf.getInt(BundleFormat.HEADER_SIZE + 4 * index)
        val count = u16(pos)
        pos += 2
        val primitives = ArrayList<Primitive>(count)
        repeat(count) {
            val flags = buf.get(pos).toInt() and 0xFF
            val width = u16(pos + 1) / BundleFormat.WIDTH_SCALE
            val miter = u16(pos + 3) / BundleFormat.MITER_SCALE
            val verbCount = u16(pos + 5)
            pos += 7
            val verbs = ByteArray(verbCount)
            var points = 0
            for (i in 0 until verbCount) {
                val v = buf.get(pos + i)
                verbs[i] = v
                points += Verb.pointCount(v)
            }
            pos += verbCount
            val coords = FloatArray(points * 2)
            for (i in coords.indices) {
                coords[i] = buf.getShort(pos) / BundleFormat.COORD_SCALE
                pos += 2
            }
            primitives += Primitive(flags, width, miter, verbs, coords)
        }
        return Icon(primitives)
    }

    private fun u16(pos: Int): Int = buf.getShort(pos).toInt() and 0xFFFF
}
