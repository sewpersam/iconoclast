package dev.iconoclast.render.png

/**
 * The encoder's match finder: LZ77 over hash chains, plain integer code. The three bytes
 * at each position are hashed into [HASH_BITS] bits; each hash heads a chain of earlier
 * positions with that hash, newest first, within the 32 KB window. A position takes the
 * longest match found in the first [Effort.maxChain] links, cut short by a match of
 * [Effort.niceLength]. With [Effort.lazy], a match is dropped for a literal when the next
 * position matches longer, as zlib's higher levels do.
 */
internal object Lz77 {

    /** How hard to look. Two settings exist; each fixes the bytes it gives. */
    class Effort internal constructor(val maxChain: Int, val niceLength: Int, val lazy: Boolean)

    /**
     * Symbols: a literal byte as 0..255, or a match as 256 + length, with its distance at
     * the same index in the second array. The end-of-block symbol is added per block.
     */
    fun parse(data: ByteArray, effort: Effort): Pair<IntArray, IntArray> {
        val n = data.size
        val syms = IntList()
        val dists = IntList()
        val head = IntArray(1 shl HASH_BITS) { -1 }
        val prev = IntArray(WINDOW)
        val maxChain = effort.maxChain
        val nice = effort.niceLength
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
            var chain = maxChain
            val maxLen = minOf(MAX_MATCH, n - i)
            while (cand >= 0 && i - cand <= WINDOW - 1 && chain-- > 0) {
                if (data[cand + bestLen] == data[i + bestLen]) {
                    var l = 0
                    while (l < maxLen && data[cand + l] == data[i + l]) l++
                    if (l > bestLen) {
                        bestLen = l
                        bestDist = i - cand
                        if (l == maxLen || l >= nice) break
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
            val take = len >= MIN_MATCH && (!effort.lazy || len >= MAX_MATCH || (longest(i + 1) ushr 16) <= len)
            if (take) {
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

    const val WINDOW = 32768
    private const val HASH_BITS = 15
    const val MIN_MATCH = 3
    const val MAX_MATCH = 258
}
