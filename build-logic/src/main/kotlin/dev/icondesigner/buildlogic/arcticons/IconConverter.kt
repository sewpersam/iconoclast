package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.format.Primitive
import java.util.TreeMap

/** Side length of the canvas every icon is normalised to, in bundle units. */
const val CANVAS = 48.0

/** Counters for the conversion report. Keys are short event names. */
class ConversionStats {
    val counts = TreeMap<String, Int>()
    fun add(key: String, n: Int = 1) {
        counts.merge(key, n, Int::plus)
    }
    fun addAll(other: ConversionStats) = other.counts.forEach { (k, v) -> add(k, v) }
}

private val CONTAINERS = setOf("svg", "g", "a")
private val NON_RENDERING = setOf("defs", "clipPath", "mask", "marker", "pattern", "symbol", "linearGradient", "radialGradient")

/**
 * Converts one Arcticons SVG into an [Icon] on the 48-unit canvas: styles cascaded,
 * transforms baked, every shape turned into absolute moves, lines and cubics.
 */
fun convertSvg(text: String, stats: ConversionStats): Icon {
    val doc = parseSvg(text)
    val sheet = parseStyleSheet(doc.css)
    sheet.unsupportedSelectors.forEach { stats.add("css.unsupportedSelector") }
    val resolver = StyleResolver(sheet) { stats.add("style.ignored.$it") }
    val primitives = ArrayList<Primitive>()
    val rootStyle = resolver.resolve(doc.root, ComputedStyle())
    val rootTransform = viewBoxTransform(doc.root, stats)
    for (child in doc.root.children) visit(child, rootStyle, rootTransform, resolver, primitives, stats)
    return Icon(primitives)
}

/** Maps the root viewBox onto the 48-unit canvas, centred, aspect preserved. */
private fun viewBoxTransform(root: SvgNode, stats: ConversionStats): Affine {
    val vb = root.attrs["viewBox"]?.let { NumberScanner(it).all() }?.takeIf { it.size == 4 && it[2] > 0 && it[3] > 0 }
    val box = vb ?: run {
        stats.add("svg.noViewBox")
        val w = root.attrs["width"]?.removeSuffix("px")?.toDoubleOrNull() ?: CANVAS
        val h = root.attrs["height"]?.removeSuffix("px")?.toDoubleOrNull() ?: CANVAS
        listOf(0.0, 0.0, w, h)
    }
    val (minX, minY, w, h) = box
    if (minX == 0.0 && minY == 0.0 && w == CANVAS && h == CANVAS) return Affine.IDENTITY
    stats.add("svg.viewBoxRescaled")
    val s = minOf(CANVAS / w, CANVAS / h)
    return Affine.translate((CANVAS - w * s) / 2, (CANVAS - h * s) / 2)
        .times(Affine.scale(s, s))
        .times(Affine.translate(-minX, -minY))
}

private fun visit(
    node: SvgNode, parentStyle: ComputedStyle, parentTransform: Affine,
    resolver: StyleResolver, out: MutableList<Primitive>, stats: ConversionStats,
) {
    if (node.tag in NON_RENDERING) return
    val isShape = node.tag in SHAPE_TAGS
    if (!isShape && node.tag !in CONTAINERS) {
        stats.add("element.unsupported.${node.tag}")
        return
    }
    val style = resolver.resolve(node, parentStyle)
    if (style.displayNone) return
    val local = node.attrs["transform"]?.let {
        stats.add("transform.baked")
        Affine.parse(it) ?: run { stats.add("transform.unparseable"); null }
    } ?: Affine.IDENTITY
    val transform = parentTransform.times(local)
    if (!isShape) {
        for (child in node.children) visit(child, style, transform, resolver, out, stats)
        return
    }
    stats.add("element.${node.tag}")
    shapePrimitives(node, style, transform, stats).forEach(out::add)
}

private fun shapePrimitives(node: SvgNode, style: ComputedStyle, transform: Affine, stats: ConversionStats): List<Primitive> {
    val path = PathBuilder(transform)
    val ok = buildShape(node, path)
    if (!ok) stats.add("geometry.error.${node.tag}")
    if (!path.hasSegments()) {
        stats.add("geometry.empty")
        return emptyList()
    }
    if (!style.visible || style.opacity <= 0f) {
        stats.add("style.invisible")
        return emptyList()
    }
    // Lines have no interior, so fill never paints them.
    val fill = style.fill && style.fillOpacity > 0f && node.tag != "line"
    val strokeWidth = (style.strokeWidth * transform.strokeScale).toFloat()
    val stroke = style.stroke && style.strokeOpacity > 0f && strokeWidth > 0f
    if (!fill && !stroke) {
        stats.add("style.unpainted")
        return emptyList()
    }
    if (fill && style.defaultFill) stats.add("style.defaultBlackFill")
    if (style.opacity < 1f || (fill && style.fillOpacity < 1f) || (stroke && style.strokeOpacity < 1f)) {
        stats.add("style.partialOpacityIgnored")
    }
    if (stroke && !transform.isConformal) stats.add("transform.nonUniformStroke")
    if (fill) stats.add("primitive.filled")
    if (stroke) stats.add("primitive.stroked")
    if (style.fillEvenOdd) stats.add("primitive.evenOdd")

    val verbs = path.verbs()
    val coords = path.coords()
    val dashes = style.dashArray
    if (!stroke || dashes == null) {
        return listOf(Primitive(flags(style, stroke, fill), strokeWidth, style.miterLimit, verbs, coords))
    }
    stats.add("primitive.dashBaked")
    val scale = transform.strokeScale.toFloat()
    val scaled = FloatArray(dashes.size) { dashes[it] * scale }
    val (dv, dc) = applyDashes(verbs, coords, scaled, style.dashOffset * scale)
    val result = ArrayList<Primitive>(2)
    if (fill) result += Primitive(flags(style, false, true), strokeWidth, style.miterLimit, verbs, coords)
    if (dv.isNotEmpty()) result += Primitive(flags(style, true, false), strokeWidth, style.miterLimit, dv, dc)
    return result
}

// The fill rule is kept even on unfilled primitives: fill modes downstream fill stroked shapes.
private fun flags(style: ComputedStyle, stroke: Boolean, fill: Boolean): Int =
    Primitive.flags(stroke, fill, style.fillEvenOdd, style.cap, style.join)
