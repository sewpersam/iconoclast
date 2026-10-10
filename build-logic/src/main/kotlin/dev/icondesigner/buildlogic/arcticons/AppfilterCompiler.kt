package dev.icondesigner.buildlogic.arcticons

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/** The compiled appfilter plus everything that was dropped, for the build log. */
class AppfilterResult(
    /**
     * Component key to drawable index. Keys are fully qualified `pkg/activity`, or `:NAME`
     * shortcuts. The short form `pkg/.Activity` is expanded, as installed components never use it.
     */
    val entries: Map<String, Int>,
    val items: Int,
    val calendarSkipped: Int,
    val shortcuts: Int,
    val malformed: List<String>,
    val unknownDrawable: List<String>,
    val duplicates: Int,
    val conflicting: List<String>,
    /** Entries written in the short form `pkg/.Activity` and expanded. */
    val expanded: Int,
)

private val COMPONENT = Regex("ComponentInfo\\{([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*)/([^/{}\\s]+)\\}")
private val SHORTCUT = Regex(":[A-Z_]+")

/**
 * Compiles appfilter.xml into a component to drawable-index map. Calendar entries are
 * skipped. Malformed components and unknown drawables are collected, never thrown.
 * The first mapping of a component wins, except that a fully qualified entry beats an
 * expanded short-form one, so an expansion never overrides an explicit mapping.
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
    var expanded = 0
    val fromShortForm = HashSet<String>()

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
                    var short = false
                    val key = when {
                        component == null || drawable == null -> null
                        SHORTCUT.matches(component) -> component.also { shortcuts++ }
                        else -> COMPONENT.matchEntire(component)?.let {
                            val (pkg, cls) = it.destructured
                            if (cls.startsWith(".")) "$pkg/$pkg$cls".also { expanded++; short = true } else "$pkg/$cls"
                        }
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
                    if (existing == null) {
                        if (short) fromShortForm += key
                    } else {
                        duplicates++
                        val replaces = !short && fromShortForm.remove(key)
                        if (replaces) entries[key] = index
                        if (existing != index) conflicting += "$key -> $drawable" + if (replaces) ", replaces the short form" else ""
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
    return AppfilterResult(entries, items, calendars, shortcuts, malformed, unknown, duplicates, conflicting, expanded)
}
