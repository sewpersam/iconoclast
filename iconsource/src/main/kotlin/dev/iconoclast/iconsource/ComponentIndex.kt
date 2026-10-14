package dev.iconoclast.iconsource

import dev.iconoclast.render.format.ComponentTable

/** Resolves launcher component strings to drawable indices through the compiled appfilter. */
class ComponentIndex(private val table: ComponentTable) {
    val size: Int get() = table.size

    /**
     * Drawable index for [component], or -1. Accepts `ComponentInfo{pkg/cls}`, `pkg/cls`,
     * the short form `pkg/.cls`, and appfilter shortcuts such as `:BROWSER`.
     */
    fun resolve(component: String): Int {
        val key = normalize(component) ?: return -1
        return table.lookup(key)
    }

    /**
     * The drawable a launcher shows for [component] in a pack built on this phone: its own
     * appfilter entry, else the drawable Arcticons gives its package under another
     * activity name. Store builds rename launcher activities (Play, Aurora, Obtainium),
     * so the exact entry is often missing while the app is in Arcticons. -1 when neither.
     */
    fun resolveApp(component: String): Int {
        val exact = resolve(component)
        if (exact >= 0) return exact
        val key = normalize(component) ?: return -1
        if (key.startsWith(":")) return -1
        return resolvePackage(key.substringBefore('/'))
    }

    /**
     * The drawable Arcticons uses most for [pkg]'s activities, ties to the lower index, or
     * -1 when the package has no entry.
     */
    fun resolvePackage(pkg: String): Int {
        val prefix = "$pkg/"
        val counts = HashMap<Int, Int>()
        var i = table.lowerBound(prefix)
        while (i < table.size) {
            if (!table.component(i).startsWith(prefix)) break
            val d = table.drawable(i)
            counts[d] = (counts[d] ?: 0) + 1
            i++
        }
        return counts.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })?.key ?: -1
    }

    /** Every `package/class` entry, sorted, whose drawable is in [drawables]. Shortcuts such as `:BROWSER` are left out. */
    fun componentsOf(drawables: Set<Int>): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until table.size) {
            val d = table.drawable(i)
            if (d !in drawables) continue
            val c = table.component(i)
            if (!c.startsWith(":")) out += c to d
        }
        return out
    }

    companion object {
        fun normalize(component: String): String? {
            var c = component.trim()
            if (c.startsWith(":")) return c
            if (c.startsWith("ComponentInfo{") && c.endsWith("}")) c = c.substring(14, c.length - 1)
            val slash = c.indexOf('/')
            if (slash <= 0 || slash == c.length - 1) return null
            val pkg = c.substring(0, slash)
            val cls = c.substring(slash + 1)
            return if (cls.startsWith(".")) "$pkg/$pkg$cls" else "$pkg/$cls"
        }
    }
}
