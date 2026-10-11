package dev.iconoclast.iconsource

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The swap search, spec 2.2 screen 3, over every drawable name at the pin: substring
 * matches, names starting with the query first, each group in name order.
 */
class DrawableSearchTest {
    private val lines = File(requireNotNull(System.getProperty("arcticons.names"))).readLines().filter { it.isNotEmpty() }
    private val names = DrawableNames(lines.toTypedArray())

    @Test
    fun searchesEveryName() {
        assertTrue(names.size > 15_000, "${names.size} names")
        for (i in 0 until names.size step 997) assertTrue(i in names.search(names.name(i)), names.name(i))
    }

    @Test
    fun prefixMatchesComeFirstThenSubstringsEachInNameOrder() {
        for (query in listOf("camera", "Google Maps", "clock", "a")) {
            val q = query.lowercase().replace(' ', '_')
            val got = names.search(query).map(names::name)
            val prefix = lines.filter { it.startsWith(q) }.sorted()
            val inner = lines.filter { !it.startsWith(q) && q in it }.sorted()
            assertEquals(prefix + inner, got, query)
        }
        assertEquals("camera", names.name(names.search("camera").first()))
        val cam = names.search("camera").map(names::name)
        assertTrue(cam.indexOf("camera360") < cam.indexOf("a_better_camera"), "a prefix match outranks an earlier name")
    }

    @Test
    fun limitKeepsTheRanking() {
        val all = names.search("camera")
        for (limit in listOf(1, 5, 40, all.size + 3)) assertEquals(all.take(limit), names.search("camera", limit), "limit $limit")
    }
}

/**
 * Spec 2.5: calendar entries are out of scope and fall back to the static drawable. The
 * compiled appfilter skips `<calendar>` entries, so a calendar app resolves through its
 * `<item>` entry. aCalendar+ has both at the pin.
 */
class CalendarFallbackTest {
    @Test
    fun calendarAppsResolveToTheirStaticDrawable() {
        val namesFile = File(requireNotNull(System.getProperty("arcticons.names")))
        val components = File(requireNotNull(System.getProperty("arcticons.components")))
        val names = DrawableNames.parse(namesFile.readBytes())
        val index = ComponentIndex(dev.iconoclast.render.format.ComponentTable(java.nio.ByteBuffer.wrap(components.readBytes())))
        val d = index.resolve("org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar")
        assertEquals("acalendar_plus", names.name(d))
    }
}
