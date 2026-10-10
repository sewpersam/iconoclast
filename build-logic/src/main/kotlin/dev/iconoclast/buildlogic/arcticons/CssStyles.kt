package dev.iconoclast.buildlogic.arcticons

/** A compound selector without combinators: optional type, then classes and ids. */
class SimpleSelector(val type: String?, val classes: List<String>, val ids: List<String>) {
    /** CSS specificity packed as ids, classes, types. */
    val specificity: Int = ids.size * 10_000 + classes.size * 100 + (if (type != null) 1 else 0)

    fun matches(node: SvgNode, nodeClasses: Set<String>): Boolean {
        if (type != null && type != node.tag) return false
        if (!nodeClasses.containsAll(classes)) return false
        val id = node.attrs["id"]
        return ids.all { it == id }
    }
}

class CssRule(val selector: SimpleSelector, val order: Int, val declarations: Map<String, String>)

class StyleSheet(val rules: List<CssRule>, val unsupportedSelectors: List<String>) {
    /** Rules matching [node], lowest priority first: by specificity, then source order. */
    fun matching(node: SvgNode): List<CssRule> {
        if (rules.isEmpty()) return emptyList()
        val classes = node.attrs["class"]?.split(WS)?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        return rules.filter { it.selector.matches(node, classes) }
            .sortedWith(compareBy({ it.selector.specificity }, { it.order }))
    }
}

private val WS = Regex("\\s+")
private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
private val SELECTOR = Regex("(\\*|[A-Za-z][A-Za-z0-9_-]*)?((?:[.#][A-Za-z0-9_-]+)*)")
private val PART = Regex("[.#][A-Za-z0-9_-]+")

fun parseStyleSheet(css: String): StyleSheet {
    val rules = ArrayList<CssRule>()
    val unsupported = ArrayList<String>()
    val text = COMMENT.replace(css, "")
    var order = 0
    for (block in text.split('}')) {
        val brace = block.indexOf('{')
        if (brace < 0) continue
        val declarations = parseDeclarations(block.substring(brace + 1))
        for (raw in block.substring(0, brace).split(',')) {
            val sel = raw.trim()
            if (sel.isEmpty()) continue
            val m = SELECTOR.matchEntire(sel)
            if (m == null) {
                unsupported += sel
                continue
            }
            val type = m.groupValues[1].takeIf { it.isNotEmpty() && it != "*" }
            val parts = PART.findAll(m.groupValues[2]).map { it.value }.toList()
            val selector = SimpleSelector(
                type,
                parts.filter { it[0] == '.' }.map { it.substring(1) },
                parts.filter { it[0] == '#' }.map { it.substring(1) },
            )
            rules += CssRule(selector, order++, declarations)
        }
    }
    return StyleSheet(rules, unsupported)
}

/** Parses `a:b; c: d` into lowercase property names and trimmed values. Drops `!important`. */
fun parseDeclarations(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (decl in text.split(';')) {
        val colon = decl.indexOf(':')
        if (colon < 0) continue
        val name = decl.substring(0, colon).trim().lowercase()
        val value = decl.substring(colon + 1).replace("!important", "").trim()
        if (name.isNotEmpty() && value.isNotEmpty()) out[name] = value
    }
    return out
}
