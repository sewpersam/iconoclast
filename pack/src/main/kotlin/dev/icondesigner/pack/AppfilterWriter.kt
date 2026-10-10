package dev.icondesigner.pack

/**
 * Writes the pack's `assets/appfilter.xml`: plain-text XML, which every launcher under
 * test reads, spec 1.2. Package and activity names are escaped, spec 6.1. Only `<item>`
 * entries are written; calendar icons fall back to their static drawable, spec 2.5.
 */
object AppfilterWriter {
    /** [items] maps a component, `package/class` exactly as the system reports it, to a drawable name. */
    fun write(items: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        for ((component, drawable) in items) {
            append("    <item component=\"ComponentInfo{").append(escapeXml(component))
            append("}\" drawable=\"").append(escapeXml(drawable)).append("\" />\n")
        }
        append("</resources>\n")
    }
}

/** Escapes text for an XML attribute value in double quotes or for element content. */
fun escapeXml(s: String): String {
    val out = StringBuilder(s.length)
    for (c in s) {
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> out.append(c)
        }
    }
    return out.toString()
}
