package dev.iconoclast.render.png

import java.util.zip.Adler32

/**
 * A small deterministic zlib/DEFLATE compressor: LZ77 over hash chains ([Lz77]), then
 * Huffman blocks. java.util.zip.Deflater would give bytes that depend on the zlib build
 * behind it, which differs between a desktop JVM and Android, so the same theme would not
 * give the same PNG bytes everywhere. This one is plain Kotlin integer code.
 *
 * Two efforts, each pinned to its bytes:
 * - [Effort.FAST], every icon the renderer encodes: chains of [FAST_CHAIN], no lazy
 *   matching, and per block the cheaper of the fixed and the dynamic Huffman code, both
 *   costed exactly from the block's symbol counts. A textured 432 px icon is mostly noise
 *   that long chains search in vain; this is what keeps it inside the Apply budget.
 * - [Effort.CANONICAL], texture files: chains of 512, lazy matching, dynamic blocks only.
 *   This is the M5 encoder unchanged. A texture's name is the SHA-256 of these bytes, so
 *   they may never change: every stored texture, export and theme names its texture by them.
 */
object Deflate {

    enum class Effort(internal val lz: Lz77.Effort, internal val chooseFixed: Boolean, internal val flags: Int) {
        FAST(Lz77.Effort(FAST_CHAIN, 32, lazy = false), chooseFixed = true, flags = 0x01),
        CANONICAL(Lz77.Effort(512, 64, lazy = true), chooseFixed = false, flags = 0xDA),
    }

    fun zlib(data: ByteArray, effort: Effort = Effort.FAST): ByteArray {
        val out = BitWriter(data.size / 2 + 64)
        out.byte(0x78)
        out.byte(effort.flags)
        val (syms, extra) = Lz77.parse(data, effort.lz)
        var start = 0
        do {
            val end = minOf(syms.size, start + BLOCK_SYMBOLS)
            writeBlock(out, syms, extra, start, end, final = end == syms.size, effort.chooseFixed)
            start = end
        } while (start < syms.size)
        out.flushByte()
        val adler = Adler32().apply { update(data) }.value.toInt()
        for (s in intArrayOf(24, 16, 8, 0)) out.byte(adler ushr s and 0xFF)
        return out.toByteArray()
    }

    private fun writeBlock(out: BitWriter, syms: IntArray, dists: IntArray, from: Int, to: Int, final: Boolean, chooseFixed: Boolean) {
        val litFreq = IntArray(286)
        val distFreq = IntArray(30)
        for (k in from until to) {
            val s = syms[k]
            if (s < 256) litFreq[s]++ else {
                litFreq[LENGTH_CODE[s - 256]]++
                distFreq[distCode(dists[k])]++
            }
        }
        litFreq[256]++
        val litLen = Huffman.lengths(litFreq, 15)
        val distLen = Huffman.lengths(distFreq, 15)
        val litCodes = Huffman.codes(litLen)
        val distCodes = Huffman.codes(distLen)

        var hlit = 286
        while (hlit > 257 && litLen[hlit - 1] == 0) hlit--
        var hdist = 30
        while (hdist > 1 && distLen[hdist - 1] == 0) hdist--
        val all = litLen.copyOf(hlit) + distLen.copyOf(hdist)
        val (clSyms, clExtra) = runLengths(all)
        val clFreq = IntArray(19)
        clSyms.forEach { clFreq[it]++ }
        val clLen = Huffman.lengths(clFreq, 7)
        val clCodes = Huffman.codes(clLen)
        var hclen = 19
        while (hclen > 4 && clLen[CL_ORDER[hclen - 1]] == 0) hclen--

        if (chooseFixed) {
            // Exact sizes in bits of the two encodings; the extra bits are the same in both.
            var dynamic = 5L + 5 + 4 + 3L * hclen
            for (k in clSyms.indices) dynamic += clLen[clSyms[k]] + CL_EXTRA_BITS[clSyms[k]]
            var fixed = 0L
            for (sym in 0 until 286) {
                dynamic += litFreq[sym].toLong() * litLen[sym]
                fixed += litFreq[sym].toLong() * FIXED_LIT_LEN[sym]
            }
            for (sym in 0 until 30) {
                dynamic += distFreq[sym].toLong() * distLen[sym]
                fixed += distFreq[sym] * 5L
            }
            if (fixed <= dynamic) return writeFixed(out, syms, dists, from, to, final)
        }

        out.bits(if (final) 1 else 0, 1)
        out.bits(2, 2)
        out.bits(hlit - 257, 5)
        out.bits(hdist - 1, 5)
        out.bits(hclen - 4, 4)
        for (k in 0 until hclen) out.bits(clLen[CL_ORDER[k]], 3)
        for (k in clSyms.indices) {
            val s = clSyms[k]
            out.code(clCodes[s], clLen[s])
            when (s) {
                16 -> out.bits(clExtra[k], 2)
                17 -> out.bits(clExtra[k], 3)
                18 -> out.bits(clExtra[k], 7)
            }
        }
        writeSymbols(out, syms, dists, from, to, litCodes, litLen, distCodes, distLen)
    }

    /** The block's symbols and its end-of-block, codes reversed once for LSB-first output. */
    private fun writeSymbols(
        out: BitWriter, syms: IntArray, dists: IntArray, from: Int, to: Int,
        litCodes: IntArray, litLen: IntArray, distCodes: IntArray, distLen: IntArray,
    ) {
        val lit = IntArray(litCodes.size) { reverse(litCodes[it], litLen[it]) }
        val dist = IntArray(distCodes.size) { reverse(distCodes[it], distLen[it]) }
        for (k in from until to) {
            val s = syms[k]
            if (s < 256) {
                out.bits(lit[s], litLen[s])
                continue
            }
            val len = s - 256
            val lc = LENGTH_CODE[len]
            out.bits(lit[lc], litLen[lc])
            val li = lc - 257
            if (LEN_EXTRA[li] > 0) out.bits(len - LEN_BASE[li], LEN_EXTRA[li])
            val d = dists[k]
            val dc = distCode(d)
            out.bits(dist[dc], distLen[dc])
            if (DIST_EXTRA[dc] > 0) out.bits(d - DIST_BASE[dc], DIST_EXTRA[dc])
        }
        out.bits(lit[256], litLen[256])
    }

    /** A Huffman code, most significant bit first, reversed for the LSB-first bit stream. */
    private fun reverse(code: Int, length: Int): Int {
        var r = 0
        for (i in 0 until length) r = r or ((code ushr i and 1) shl (length - 1 - i))
        return r
    }

    /** Code-length symbols 0 to 18 with their extra-bit values. */
    private fun runLengths(lengths: IntArray): Pair<IntArray, IntArray> {
        val syms = IntList()
        val extra = IntList()
        var i = 0
        while (i < lengths.size) {
            val v = lengths[i]
            var run = 1
            while (i + run < lengths.size && lengths[i + run] == v) run++
            if (v == 0 && run >= 3) {
                val r = minOf(run, 138)
                if (r <= 10) { syms.add(17); extra.add(r - 3) } else { syms.add(18); extra.add(r - 11) }
                i += r
            } else if (v != 0 && run >= 4) {
                syms.add(v); extra.add(0)
                val r = minOf(run - 1, 6)
                syms.add(16); extra.add(r - 3)
                i += 1 + r
            } else {
                syms.add(v); extra.add(0)
                i++
            }
        }
        return syms.toArray() to extra.toArray()
    }

    private fun distCode(d: Int): Int = if (d <= 256) DIST_CODE_LOW[d] else DIST_CODE_HIGH[(d - 1) ushr 7]

    /** Chain links [Effort.FAST] follows per position. */
    const val FAST_CHAIN = 32
    private const val BLOCK_SYMBOLS = 1 shl 14

    /** Extra bits after code-length symbols 16, 17 and 18. */
    private val CL_EXTRA_BITS = IntArray(19).also { it[16] = 2; it[17] = 3; it[18] = 7 }

    /** RFC 1951 section 3.2.6: the fixed literal/length code lengths. */
    private val FIXED_LIT_LEN = IntArray(288) { when { it < 144 -> 8; it < 256 -> 9; it < 280 -> 7; else -> 8 } }
    private val FIXED_LIT_CODES = Huffman.codes(FIXED_LIT_LEN)
    private val FIXED_DIST_LEN = IntArray(30) { 5 }
    private val FIXED_DIST_CODES = Huffman.codes(FIXED_DIST_LEN)

    /** A block with the fixed Huffman code, BTYPE 01. */
    private fun writeFixed(out: BitWriter, syms: IntArray, dists: IntArray, from: Int, to: Int, final: Boolean) {
        out.bits(if (final) 1 else 0, 1)
        out.bits(1, 2)
        writeSymbols(out, syms, dists, from, to, FIXED_LIT_CODES, FIXED_LIT_LEN, FIXED_DIST_CODES, FIXED_DIST_LEN)
    }

    private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private fun slowCode(base: IntArray, v: Int): Int {
        var i = base.size - 1
        while (base[i] > v) i--
        return i
    }

    /** Length symbol 257..285 for each match length 0..258; entries below 3 unused. */
    private val LENGTH_CODE = IntArray(Lz77.MAX_MATCH + 1) { if (it < Lz77.MIN_MATCH) 0 else 257 + slowCode(LEN_BASE, it) }

    /** Distance codes: direct for 1..256, by 128-wide buckets above, which never straddle a code. */
    private val DIST_CODE_LOW = IntArray(257) { if (it == 0) 0 else slowCode(DIST_BASE, it) }
    private val DIST_CODE_HIGH = IntArray(256) { slowCode(DIST_BASE, it * 128 + 1) }
}

/** Length-limited canonical Huffman codes, deterministic for equal inputs. */
internal object Huffman {

    /**
     * Code lengths for [freq], none longer than [limit]. At least two symbols get codes,
     * so every code is complete, as zlib's inflater requires of code-length codes.
     */
    fun lengths(freq: IntArray, limit: Int): IntArray {
        val f = freq.copyOf()
        var used = f.count { it > 0 }
        for (s in f.indices) {
            if (used >= 2) break
            if (f[s] == 0) { f[s] = 1; used++ }
        }
        // Leaves ascending by frequency, ties by symbol.
        val leaves = f.indices.filter { f[it] > 0 }.sortedWith(compareBy({ f[it] }, { it }))
        val n = leaves.size
        // Two-queue construction. Nodes 0 until n are leaves, n and up internal.
        val weight = LongArray(2 * n)
        val parent = IntArray(2 * n) { -1 }
        for (k in 0 until n) weight[k] = f[leaves[k]].toLong()
        var li = 0
        var qi = n
        var next = n
        fun take(): Int = if (li < n && (qi >= next || weight[li] <= weight[qi])) li++ else qi++
        while (next < 2 * n - 1) {
            val a = take()
            val b = take()
            weight[next] = weight[a] + weight[b]
            parent[a] = next
            parent[b] = next
            next++
        }
        val depth = IntArray(2 * n)
        for (k in 2 * n - 3 downTo 0) depth[k] = depth[parent[k]] + 1
        val count = IntArray(maxOf(limit, depth.max()) + 1)
        for (k in 0 until n) count[depth[k]]++
        // Clamp over-long leaves, then restore the Kraft sum as miniz does: drop a leaf at
        // the limit and split one shorter leaf into two, until the code is complete.
        for (b in limit + 1 until count.size) { count[limit] += count[b]; count[b] = 0 }
        var total = 0L
        for (b in 1..limit) total += count[b].toLong() shl (limit - b)
        while (total != 1L shl limit) {
            count[limit]--
            for (b in limit - 1 downTo 1) {
                if (count[b] > 0) {
                    count[b]--
                    count[b + 1] += 2
                    break
                }
            }
            total--
        }
        // Longest codes to the least frequent symbols.
        val out = IntArray(freq.size)
        var k = 0
        for (b in limit downTo 1) repeat(count[b]) { out[leaves[k++]] = b }
        return out
    }

    /** Canonical codes from lengths, RFC 1951 section 3.2.2. */
    fun codes(lengths: IntArray): IntArray {
        val max = lengths.max()
        val count = IntArray(max + 1)
        lengths.forEach { if (it > 0) count[it]++ }
        val next = IntArray(max + 2)
        var code = 0
        for (b in 1..max) {
            code = (code + count[b - 1]) shl 1
            next[b] = code
        }
        return IntArray(lengths.size) { if (lengths[it] > 0) next[lengths[it]]++ else 0 }
    }
}

/** LSB-first bit packing, as DEFLATE wants, into a growable array. */
internal class BitWriter(capacity: Int = 1024) {
    private var buf = ByteArray(maxOf(16, capacity))
    private var size = 0
    private var acc = 0L
    private var n = 0

    fun bits(value: Int, count: Int) {
        acc = acc or ((value.toLong() and ((1L shl count) - 1)) shl n)
        n += count
        while (n >= 8) {
            if (size == buf.size) buf = buf.copyOf(size * 2)
            buf[size++] = acc.toByte()
            acc = acc ushr 8
            n -= 8
        }
    }

    /** A Huffman code, most significant bit first. */
    fun code(code: Int, length: Int) {
        var r = 0
        for (i in 0 until length) r = r or ((code ushr i and 1) shl (length - 1 - i))
        bits(r, length)
    }

    fun flushByte() {
        if (n > 0) bits(0, 8 - n)
    }

    fun byte(b: Int) = bits(b, 8)

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

internal class IntList {
    private var a = IntArray(1024)
    var size = 0; private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(size * 2)
        a[size++] = v
    }

    fun toArray(): IntArray = a.copyOf(size)
}
