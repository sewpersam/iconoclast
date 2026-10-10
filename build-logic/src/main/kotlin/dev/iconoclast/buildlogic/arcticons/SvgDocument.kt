package dev.iconoclast.buildlogic.arcticons

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** An SVG element after stripping: SVG-namespace elements only, unprefixed attributes only. */
class SvgNode(val tag: String, val attrs: Map<String, String>, val children: List<SvgNode>)

/** The render tree of one SVG plus the text of all its `<style>` blocks. */
class SvgDocument(val root: SvgNode, val css: String)

private const val SVG_NS = "http://www.w3.org/2000/svg"

/** Elements dropped with their whole subtree: metadata and other non-rendering content. */
private val STRIPPED = setOf("metadata", "title", "desc")

private val factory: ThreadLocal<DocumentBuilderFactory> = ThreadLocal.withInitial {
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
    }
}

/**
 * Parses SVG text. Strips `<metadata>`, `<title>`, `<desc>` and every element outside the
 * SVG namespace, such as inkscape and sodipodi cruft. Collects `<style>` text in document order.
 */
fun parseSvg(text: String): SvgDocument {
    val builder = factory.get().newDocumentBuilder()
    builder.setErrorHandler(null)
    val doc = builder.parse(InputSource(StringReader(text)))
    val css = StringBuilder()
    val root = convert(doc.documentElement, css)
        ?: throw IllegalArgumentException("root element is not svg")
    require(root.tag == "svg") { "root element is ${root.tag}, not svg" }
    return SvgDocument(root, css.toString())
}

private fun convert(el: Element, css: StringBuilder): SvgNode? {
    val ns = el.namespaceURI
    if (ns != null && ns != SVG_NS) return null
    val tag = el.localName ?: el.tagName
    if (tag in STRIPPED) return null
    if (tag == "style") {
        css.append(el.textContent).append('\n')
        return null
    }
    val attrs = LinkedHashMap<String, String>()
    val list = el.attributes
    for (i in 0 until list.length) {
        val a = list.item(i)
        if (a.namespaceURI == null) attrs[a.localName ?: a.nodeName] = a.nodeValue
    }
    val children = ArrayList<SvgNode>()
    val nodes = el.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n is Element) convert(n, css)?.let(children::add)
    }
    return SvgNode(tag, attrs, children)
}
