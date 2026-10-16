package dev.iconoclast.pack

/**
 * Every Apply installs the pack under a fresh package name, `<base>.g<NNNNNN>`. Neo 0.9.3
 * keeps a pack's first-parsed appfilter for the life of its process and keys its icon
 * cache by the pack's package name, not its version, so an update in place under one
 * name is never noticed. A new name is.
 *
 * The template is built as generation 0, [placeholder]. Every generation has the same
 * length, so [patch] swaps the name inside the binary manifest and resources.arsc in
 * place, without touching a string pool's layout or the pure-Kotlin byte reproducibility.
 * `<base>` alone is the single name every pack used before this scheme.
 */
object PackName {
    const val DIGITS = 6
    const val MAX = 999_999
    private const val MARK = ".g"

    /** The pack of [generation], 0 being the template's placeholder. */
    fun of(base: String, generation: Int): String {
        require(generation in 0..MAX) { "generation $generation out of range" }
        return base + MARK + generation.toString().padStart(DIGITS, '0')
    }

    fun placeholder(base: String) = of(base, 0)

    /** The generation [name] carries, 0 for the legacy `<base>` pack, null when it is no pack of ours. */
    fun generationOf(base: String, name: String): Int? {
        if (name == base) return 0
        if (!name.startsWith(base + MARK)) return null
        val digits = name.substring(base.length + MARK.length)
        if (digits.length != DIGITS || !digits.all { it in '0'..'9' }) return null
        return digits.toInt()
    }

    fun isPack(base: String, name: String) = generationOf(base, name) != null

    /**
     * The generation the next Apply installs: above both the last one installed and every
     * pack still on the phone, so it never takes a name that is installed or that a
     * launcher may still hold in its cache.
     */
    fun next(lastInstalled: Int, installed: Collection<Int>): Int {
        val n = maxOf(lastInstalled, installed.maxOrNull() ?: 0) + 1
        check(n <= MAX) { "pack generations exhausted" }
        return n
    }

    /**
     * [bytes] with the one occurrence of [from] replaced by [to], which must be as long.
     * Android's string pools hold it as UTF-16 (the manifest, the arsc package header) or
     * UTF-8; either is matched. Exactly one occurrence must exist, so a template that
     * changed shape fails here instead of shipping a half-renamed pack.
     */
    fun patch(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length) { "$to is not as long as $from" }
        val out = bytes.copyOf()
        var count = 0
        for (cs in listOf(Charsets.UTF_16LE, Charsets.UTF_8)) {
            val f = from.toByteArray(cs)
            val t = to.toByteArray(cs)
            var i = indexOf(out, f, 0)
            while (i >= 0) {
                t.copyInto(out, i)
                count++
                i = indexOf(out, f, i + f.size)
            }
        }
        require(count == 1) { "expected one \"$from\", found $count" }
        return out
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, start: Int): Int {
        outer@ for (i in start..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
