package dev.icondesigner.iconsource

/** Drawable names in bundle order: name i is icon i. The converter sorts them. */
class DrawableNames(private val names: Array<String>) {
    val size: Int get() = names.size

    fun name(index: Int): String = names[index]

    /** Index of [name], or -1. */
    fun indexOf(name: String): Int = names.binarySearch(name).let { if (it >= 0) it else -1 }

    /**
     * Indices of names containing [query], case-insensitive, spaces read as underscores.
     * Names starting with the query come first, then the rest, each group in name order.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Int> {
        val q = query.trim().lowercase().replace(' ', '_')
        if (q.isEmpty()) return emptyList()
        val prefix = ArrayList<Int>()
        val inner = ArrayList<Int>()
        for (i in names.indices) {
            val at = names[i].indexOf(q)
            if (at == 0) prefix += i else if (at > 0) inner += i
            if (prefix.size >= limit) break
        }
        return (prefix + inner).take(limit)
    }

    companion object {
        /** Parses the converter's names.txt: UTF-8, one name per line. */
        fun parse(bytes: ByteArray): DrawableNames =
            DrawableNames(bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.toList().toTypedArray())
    }
}
