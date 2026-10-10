package dev.icondesigner.buildlogic.arcticons

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/** The compiled appfilter plus everything that was dropped, for the build log. */
class AppfilterResult(
    /** Component key to drawable index. Keys are `pkg/activity`, or `:NAME` shortcuts. */
    val entries: Map<String, Int>,
    val items: Int,
    val calendarSkipped: Int,
    val shortcuts: Int,
    val malformed: List<String>,
    val unknownDrawable: List<String>,
    val duplicates: Int,
    val conflicting: List<String>,
)

private val COMPONENT = Regex("ComponentInfo\\{([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*)/([^/{}\\s]+)\\}")
private val SHORTCUT = Regex(":[A-Z_]+")

/**
 * Compiles appfilter.xml into a component to drawable-index map. Calendar entries are
 * skipped. Malformed components and unknown drawables are collected, never thrown.
 * The first mapping of a component wins.
 */
fun compileAppfilter(input: InputStream, drawableIndex: Map<String, Int>): AppfilterResult {
    val entries = LinkedHashMap<String, Int>()
    val malformed = ArrayList<String>()
    val unknown = ArrayList<String>()
    val conflicting = ArrayList<String>()
    var items = 0
    var calendars = 0
    var shortcuts = 0
    var duplicates = 0

    val factory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
    val reader = factory.createXMLStreamReader(input, "UTF-8")
    try {
        while (reader.hasNext()) {
            if (reader.next() != XMLStreamConstants.START_ELEMENT) continue
            when (reader.localName) {
                "calendar" -> calendars++
                "item" -> {
                    items++
                    val component = reader.getAttributeValue(null, "component")
                    val drawable = reader.getAttributeValue(null, "drawable")
                    val key = when {
                        component == null || drawable == null -> null
                        SHORTCUT.matches(component) -> component.also { shortcuts++ }
                        else -> COMPONENT.matchEntire(component)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
                    }
                    if (key == null) {
                        malformed += "component=$component drawable=$drawable"
                        continue
                    }
                    val index = drawableIndex[drawable]
                    if (index == null) {
                        unknown += "$key -> $drawable"
                        continue
                    }
                    val existing = entries.putIfAbsent(key, index)
                    if (existing != null) {
                        duplicates++
                        if (existing != index) conflicting += "$key -> $drawable"
                    }
                }
            }
        }
    } catch (e: XMLStreamException) {
        // A broken document keeps what was read before the error.
        malformed += "xml error at line ${e.location?.lineNumber}: ${e.message}"
    } finally {
        reader.close()
    }
    return AppfilterResult(entries, items, calendars, shortcuts, malformed, unknown, duplicates, conflicting)
}
