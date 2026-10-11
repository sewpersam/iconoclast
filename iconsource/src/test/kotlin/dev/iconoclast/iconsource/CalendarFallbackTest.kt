package dev.iconoclast.iconsource

import dev.iconoclast.render.format.ComponentTable
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val index = ComponentIndex(ComponentTable(ByteBuffer.wrap(components.readBytes())))
        val d = index.resolve("org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar")
        assertEquals("acalendar_plus", names.name(d))
    }
}
