package dev.icondesigner.pack

/**
 * Writes `assets/drawable.xml`, the list launcher icon pickers read to show a pack's
 * icons, spec 3.4. Lists the used slots in slot order.
 */
object DrawableXmlWriter {
    fun write(drawables: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <version>1</version>\n")
        for (d in drawables) append("    <item drawable=\"").append(escapeXml(d)).append("\" />\n")
        append("</resources>\n")
    }
}
