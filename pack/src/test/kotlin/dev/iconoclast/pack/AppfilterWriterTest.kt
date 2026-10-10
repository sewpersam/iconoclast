package dev.iconoclast.pack

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppfilterWriterTest {
    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())

    @Test
    fun escapesEveryXmlSpecialCharacter() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f\$g", escapeXml("a&b<c>d\"e'f\$g"))
    }

    @Test
    fun componentsSurviveAnXmlParser() {
        val items = listOf(
            "com.example/com.example.Main" to "slot_0000",
            "com.ex\$ample/.Inner\$Activity" to "slot_0001",
            "q.\"x\"/q.<A>&'B'" to "slot_0002",
        )
        val xml = AppfilterWriter.write(items)
        val nodes = parse(xml).getElementsByTagName("item")
        assertEquals(items.size, nodes.length)
        for ((i, item) in items.withIndex()) {
            val e = nodes.item(i) as org.w3c.dom.Element
            assertEquals("ComponentInfo{${item.first}}", e.getAttribute("component"))
            assertEquals(item.second, e.getAttribute("drawable"))
        }
        assertFalse("<calendar" in xml)
    }

    @Test
    fun drawableXmlListsSlotsInOrder() {
        val xml = DrawableXmlWriter.write(listOf("slot_0000", "slot_0001"))
        val doc = parse(xml)
        val names = doc.getElementsByTagName("item").let { n -> (0 until n.length).map { (n.item(it) as org.w3c.dom.Element).getAttribute("drawable") } }
        assertEquals(listOf("slot_0000", "slot_0001"), names)
        assertTrue("<version>1</version>" in xml)
    }
}
