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
