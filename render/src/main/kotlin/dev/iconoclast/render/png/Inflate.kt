package dev.iconoclast.render.png

import java.util.zip.Adler32

/**
 * A zlib/DEFLATE decoder, RFC 1950 and RFC 1951, after Mark Adler's puff.c: stored, fixed
 * and dynamic Huffman blocks, canonical codes decoded a bit at a time. Plain Kotlin integer
 * code, so a texture decodes the same everywhere; java.util.zip.Inflater would work too but
 * would put a platform zlib on the texture path. Malformed input throws
 * [IllegalArgumentException].
 */
object Inflate {

    /** Inflates a zlib stream and checks its Adler-32. [limit] caps the output size. */
    fun zlib(data: ByteArray, limit: Int = Int.MAX_VALUE): ByteArray {
        require(data.size >= 6) { "zlib stream too short" }
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        require(cmf and 0x0F == 8 && cmf ushr 4 <= 7) { "not a deflate stream" }
        require(((cmf shl 8) or flg) % 31 == 0) { "bad zlib header check" }
        require(flg and 0x20 == 0) { "preset dictionary not supported" }
        val (out, end) = raw(data, 2, data.size, limit)
        require(end + 4 <= data.size) { "missing Adler-32" }
        var want = 0
        for (k in 0 until 4) want = (want shl 8) or (data[end + k].toInt() and 0xFF)
        val got = Adler32().apply { update(out) }.value.toInt()
        require(got == want) { "Adler-32 mismatch" }
        return out
    }

    /** Inflates raw DEFLATE from [from]. Returns the output and the byte offset after the last block. */
    fun raw(data: ByteArray, from: Int, to: Int, limit: Int = Int.MAX_VALUE): Pair<ByteArray, Int> {
        val bits = Bits(data, from, to)
        val out = Output(limit)
        do {
            val last = bits.bits(1)
            when (bits.bits(2)) {
                0 -> stored(bits, out)
                1 -> codes(bits, out, FIXED_LIT, FIXED_DIST)
                2 -> dynamic(bits, out)
                else -> throw IllegalArgumentException("invalid block type")
            }
        } while (last == 0)
        return out.toByteArray() to bits.pos
    }

    private fun stored(bits: Bits, out: Output) {
        bits.align()
        require(bits.pos + 4 <= bits.end) { "truncated stored block" }
        val d = bits.d
        val p = bits.pos
        val len = (d[p].toInt() and 0xFF) or ((d[p + 1].toInt() and 0xFF) shl 8)
        val nlen = (d[p + 2].toInt() and 0xFF) or ((d[p + 3].toInt() and 0xFF) shl 8)
        require(len == nlen.inv() and 0xFFFF) { "stored length check" }
        require(p + 4 + len <= bits.end) { "truncated stored block" }
        for (k in 0 until len) out.add(d[p + 4 + k].toInt())
        bits.pos = p + 4 + len
    }

    private fun dynamic(bits: Bits, out: Output) {
        val nlen = bits.bits(5) + 257
        val ndist = bits.bits(5) + 1
        val ncode = bits.bits(4) + 4
        require(nlen <= 286 && ndist <= 30) { "too many codes" }
        val lengths = IntArray(19)
        for (k in 0 until ncode) lengths[ORDER[k]] = bits.bits(3)
        val lencode = Huffman(lengths)
        val all = IntArray(nlen + ndist)
        var i = 0
        while (i < nlen + ndist) {
            val sym = lencode.decode(bits)
            if (sym < 16) { all[i++] = sym; continue }
            var len = 0
            val repeat = when (sym) {
                16 -> { require(i > 0) { "repeat with no length" }; len = all[i - 1]; 3 + bits.bits(2) }
                17 -> 3 + bits.bits(3)
                else -> 11 + bits.bits(7)
            }
            require(i + repeat <= nlen + ndist) { "too many lengths" }
            repeat(repeat) { all[i++] = len }
        }
        require(all[256] != 0) { "no end-of-block code" }
        codes(bits, out, Huffman(all.copyOfRange(0, nlen)), Huffman(all.copyOfRange(nlen, nlen + ndist)))
    }

    private fun codes(bits: Bits, out: Output, lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = lit.decode(bits)
            if (sym < 256) { out.add(sym); continue }
            if (sym == 256) return
            val li = sym - 257
            require(li < 29) { "bad length symbol" }
            val len = LEN_BASE[li] + bits.bits(LEN_EXTRA[li])
            val ds = dist.decode(bits)
            require(ds < 30) { "bad distance symbol" }
            val d = DIST_BASE[ds] + bits.bits(DIST_EXTRA[ds])
            out.copy(d, len)
        }
    }

    private class Bits(val d: ByteArray, var pos: Int, val end: Int) {
        private var buf = 0
        private var cnt = 0

        fun bits(n: Int): Int {
            var v = buf
            while (cnt < n) {
                require(pos < end) { "truncated deflate stream" }
                v = v or ((d[pos++].toInt() and 0xFF) shl cnt)
                cnt += 8
            }
            buf = v ushr n
            cnt -= n
            return v and ((1 shl n) - 1)
        }

        /** Drops the rest of the current byte. */
        fun align() {
            buf = 0
            cnt = 0
        }
    }

    /** Canonical Huffman decoding from code lengths, RFC 1951 3.2.2. */
    private class Huffman(lengths: IntArray) {
        private val count = IntArray(16)
        private val symbol = IntArray(lengths.size)

        init {
            for (l in lengths) count[l]++
            var left = 1
            for (len in 1..15) {
                left = (left shl 1) - count[len]
                require(left >= 0) { "over-subscribed code" }
            }
            val offs = IntArray(16)
            for (len in 1 until 15) offs[len + 1] = offs[len] + count[len]
            for (s in lengths.indices) if (lengths[s] != 0) symbol[offs[lengths[s]]++] = s
        }

        fun decode(bits: Bits): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..15) {
                code = code or bits.bits(1)
                val c = count[len]
                if (code - c < first) return symbol[index + (code - first)]
                index += c
                first = (first + c) shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("invalid Huffman code")
        }
    }

    private class Output(private val limit: Int) {
        private var a = ByteArray(1 shl 16)
        private var n = 0

        fun add(b: Int) {
            require(n < limit) { "output exceeds $limit bytes" }
            if (n == a.size) grow()
            a[n++] = b.toByte()
        }

        fun copy(dist: Int, len: Int) {
            require(dist in 1..n) { "distance too far back" }
            for (k in 0 until len) add(a[n - dist].toInt())
        }

        private fun grow() {
            a = a.copyOf(minOf(limit.toLong(), a.size * 2L).toInt())
        }

        fun toByteArray(): ByteArray = a.copyOf(n)
    }

    private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
    private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val FIXED_LIT = Huffman(IntArray(288) { when { it < 144 -> 8; it < 256 -> 9; it < 280 -> 7; else -> 8 } })
    private val FIXED_DIST = Huffman(IntArray(30) { 5 })
}
