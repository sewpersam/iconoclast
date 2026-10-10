package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.Primitive

/** The resolved style of one element. Units are the element's user space. */
data class ComputedStyle(
    val fill: Boolean = true,
    val fillEvenOdd: Boolean = false,
    val fillOpacity: Float = 1f,
    val stroke: Boolean = false,
    val strokeWidth: Float = 1f,
    val cap: Int = Primitive.CAP_BUTT,
    val join: Int = Primitive.JOIN_MITER,
    val miterLimit: Float = 4f,
    val strokeOpacity: Float = 1f,
    val dashArray: FloatArray? = null,
    val dashOffset: Float = 0f,
    val visible: Boolean = true,
    /** Not inherited in CSS, but multiplied down the tree, so carried as the product. */
    val opacity: Float = 1f,
    /** display:none on this element or an ancestor. */
    val displayNone: Boolean = false,
    val defaultFill: Boolean = true,
)

/** Properties this converter understands. Others are ignored and counted by the caller. */
val KNOWN_PROPERTIES = setOf(
    "fill", "fill-rule", "fill-opacity", "stroke", "stroke-width", "stroke-linecap",
    "stroke-linejoin", "stroke-miterlimit", "stroke-opacity", "stroke-dasharray",
    "stroke-dashoffset", "visibility", "opacity", "display",
)

/**
 * Cascades style for one element: inherited parent values, then presentation attributes,
 * then stylesheet rules by specificity and source order, then the inline `style` attribute.
 */
class StyleResolver(private val sheet: StyleSheet, private val unknown: (String) -> Unit) {

    fun resolve(node: SvgNode, parent: ComputedStyle): ComputedStyle {
        val declared = LinkedHashMap<String, String>()
        for ((k, v) in node.attrs) if (k in KNOWN_PROPERTIES) declared[k] = v.trim()
        for (rule in sheet.matching(node)) declared.putAll(rule.declarations)
        node.attrs["style"]?.let { declared.putAll(parseDeclarations(it)) }

        // opacity and display are not inherited; reset them before applying.
        var s = parent.copy(opacity = 1f)
        for ((name, value) in declared) {
            if (name !in KNOWN_PROPERTIES) {
                unknown(name)
                continue
            }
            if (value == "inherit") continue // already holds the parent value
            s = apply(s, name, value)
        }
        return s.copy(opacity = parent.opacity * s.opacity, displayNone = parent.displayNone || s.displayNone)
    }

    private fun apply(s: ComputedStyle, name: String, value: String): ComputedStyle = when (name) {
        "fill" -> s.copy(fill = isPaint(value), defaultFill = false)
        "fill-rule" -> s.copy(fillEvenOdd = value == "evenodd")
        "fill-opacity" -> number(value)?.let { s.copy(fillOpacity = it) } ?: s
        "stroke" -> s.copy(stroke = isPaint(value))
        "stroke-width" -> length(value)?.let { s.copy(strokeWidth = it) } ?: s
        "stroke-linecap" -> s.copy(
            cap = when (value) {
                "round" -> Primitive.CAP_ROUND
                "square" -> Primitive.CAP_SQUARE
                else -> Primitive.CAP_BUTT
            },
        )
        "stroke-linejoin" -> s.copy(
            join = when (value) {
                "round" -> Primitive.JOIN_ROUND
                "bevel" -> Primitive.JOIN_BEVEL
                else -> Primitive.JOIN_MITER
            },
        )
        "stroke-miterlimit" -> number(value)?.let { s.copy(miterLimit = it) } ?: s
        "stroke-opacity" -> number(value)?.let { s.copy(strokeOpacity = it) } ?: s
        "stroke-dasharray" -> s.copy(dashArray = dashArray(value))
        "stroke-dashoffset" -> length(value)?.let { s.copy(dashOffset = it) } ?: s
        "visibility" -> s.copy(visible = value == "visible")
        "opacity" -> number(value)?.let { s.copy(opacity = it) } ?: s
        "display" -> s.copy(displayNone = value == "none")
        else -> s
    }

    private fun isPaint(value: String): Boolean = value != "none" && value != "transparent"

    private fun number(value: String): Float? = value.toFloatOrNull()

    private fun length(value: String): Float? = value.removeSuffix("px").trim().toFloatOrNull()
        ?: null.also { unknown("unit:$value") }

    private fun dashArray(value: String): FloatArray? {
        if (value == "none") return null
        val parts = value.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { length(it) ?: return null }
        if (parts.any { it < 0f } || parts.sum() <= 0f) return null
        return (if (parts.size % 2 == 1) parts + parts else parts).toFloatArray()
    }
}
