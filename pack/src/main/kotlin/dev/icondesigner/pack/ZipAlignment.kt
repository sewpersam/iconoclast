package dev.icondesigner.pack

/**
 * Pads a local file header's extra field so the entry data starts on an aligned offset,
 * which is what `zipalign -p 4` does. Uses the extra field apksig writes and recognises:
 * ID 0xD935, then the alignment, then zero padding.
 */
object ZipAlignment {
    const val EXTRA_ID = 0xD935
    private const val FIELD_HEADER = 6

    /** Extra field bytes for an entry whose data would start at [dataOffset] with no extra field. */
    fun extraField(dataOffset: Long, alignment: Int): ByteArray {
        require(alignment in 1..0xFFFF) { "alignment $alignment" }
        if (dataOffset % alignment == 0L) return ByteArray(0)
        val pad = ((alignment - (dataOffset + FIELD_HEADER) % alignment) % alignment).toInt()
        val out = ByteArray(FIELD_HEADER + pad)
        putShort(out, 0, EXTRA_ID)
        putShort(out, 2, 2 + pad)
        putShort(out, 4, alignment)
        return out
    }

    private fun putShort(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte()
        b[at + 1] = (v ushr 8).toByte()
    }
}
