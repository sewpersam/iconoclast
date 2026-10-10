package dev.icondesigner.render.png

import java.io.ByteArrayOutputStream
import java.util.zip.Adler32

/**
 * A small deterministic zlib/DEFLATE compressor: LZ77 over hash chains with lazy matching, then
 * dynamic Huffman blocks. java.util.zip.Deflater would give bytes that depend on the zlib
 * build behind it, which differs between a desktop JVM and Android, so the same theme
 * would not give the same PNG bytes everywhere. This one is plain Kotlin integer code.
 */
object Deflate {

    fun zlib(data: ByteArray): ByteArray {
        val out = BitWriter()
        out.byte(0x78)
        out.byte(0xDA)
        val (syms, extra) = lz77(data)
        var start = 0
        do {
            val end = minOf(syms.size, start + BLOCK_SYMBOLS)
            writeBlock(out, syms, extra, start, end, final = end == syms.size)
            start = end
        } while (start < syms.size)
        out.flushByte()
        val adler = Adler32().apply { update(data) }.value.toInt()
        for (s in intArrayOf(24, 16, 8, 0)) out.byte(adler ushr s and 0xFF)
        return out.toByteArray()
    }

    /**
     * Symbols: a literal byte as 0..255, or a match as 256 + length, with its distance at
     * the same index in the second array. The end-of-block symbol is added per block.
     */
    private fun lz77(data: ByteArray): Pair<IntArray, IntArray> {
        val n = data.size
        val syms = IntList()
        val dists = IntList()
        val head = IntArray(1 shl HASH_BITS) { -1 }
        val prev = IntArray(WINDOW)
        // Multiplicative hash of the next three bytes.
        fun hash(i: Int) = (((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8) or
            (data[i + 2].toInt() and 0xFF)) * -0x61c88647 ushr (32 - HASH_BITS)
        fun insert(i: Int) {
            if (i + 2 >= n) return
            val h = hash(i)
            prev[i and (WINDOW - 1)] = head[h]
            head[h] = i
        }
        /** Longest earlier match at [i], packed as length shl 16 or distance. */
        fun longest(i: Int): Int {
            if (i + 2 >= n) return 0
            var bestLen = 0
            var bestDist = 0
            var cand = head[hash(i)]
            var chain = MAX_CHAIN
            val maxLen = minOf(MAX_MATCH, n - i)
            while (cand >= 0 && i - cand <= WINDOW - 1 && chain-- > 0) {
                if (data[cand + bestLen] == data[i + bestLen]) {
                    var l = 0
                    while (l < maxLen && data[cand + l] == data[i + l]) l++
                    if (l > bestLen) {
                        bestLen = l
                        bestDist = i - cand
                        if (l == maxLen || l >= NICE_LENGTH) break
                    }
                }
                val p = prev[cand and (WINDOW - 1)]
                if (p >= cand) break
                cand = p
            }
            return (bestLen shl 16) or bestDist
        }
        var i = 0
        while (i < n) {
            val m = longest(i)
            val len = m ushr 16
            insert(i)
            // Lazy matching: a longer match one byte on wins over this one.
            if (len >= MIN_MATCH && (len >= LAZY_LIMIT || (longest(i + 1) ushr 16) <= len)) {
                syms.add(256 + len)
                dists.add(m and 0xFFFF)
                for (k in 1 until len) insert(i + k)
                i += len
            } else {
                syms.add(data[i].toInt() and 0xFF)
                dists.add(0)
                i++
            }
        }
        return syms.toArray() to dists.toArray()
    }

    private fun writeBlock(out: BitWriter, syms: IntArray, dists: IntArray, from: Int, to: Int, final: Boolean) {
        val litFreq = IntArray(286)
        val distFreq = IntArray(30)
        for (k in from until to) {
            val s = syms[k]
            if (s < 256) litFreq[s]++ else {
                litFreq[lengthCode(s - 256)]++
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
        for (k in from until to) {
            val s = syms[k]
            if (s < 256) {
                out.code(litCodes[s], litLen[s])
                continue
            }
            val len = s - 256
            val lc = lengthCode(len)
            out.code(litCodes[lc], litLen[lc])
            val li = lc - 257
            if (LEN_EXTRA[li] > 0) out.bits(len - LEN_BASE[li], LEN_EXTRA[li])
            val d = dists[k]
            val dc = distCode(d)
            out.code(distCodes[dc], distLen[dc])
            if (DIST_EXTRA[dc] > 0) out.bits(d - DIST_BASE[dc], DIST_EXTRA[dc])
        }
        out.code(litCodes[256], litLen[256])
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

    private fun lengthCode(len: Int): Int {
        var i = LEN_BASE.size - 1
        while (LEN_BASE[i] > len) i--
        return 257 + i
    }

    private fun distCode(d: Int): Int {
        var i = DIST_BASE.size - 1
        while (DIST_BASE[i] > d) i--
        return i
    }

    private const val WINDOW = 32768
    private const val HASH_BITS = 15
    private const val MAX_CHAIN = 512
    /** A match this long ends the chain walk, as zlib's nice_length does. */
    private const val NICE_LENGTH = 64
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val LAZY_LIMIT = 258
    private const val BLOCK_SYMBOLS = 1 shl 14

    private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CL_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
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

/** LSB-first bit packing, as DEFLATE wants. */
internal class BitWriter {
    private val out = ByteArrayOutputStream()
    private var acc = 0L
    private var n = 0

    fun bits(value: Int, count: Int) {
        acc = acc or ((value.toLong() and ((1L shl count) - 1)) shl n)
        n += count
        while (n >= 8) {
            out.write((acc and 0xFF).toInt())
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

    fun toByteArray(): ByteArray = out.toByteArray()
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
