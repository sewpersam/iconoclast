package dev.iconoclast.buildlogic.arcticons

import dev.iconoclast.render.format.ComponentTable
import dev.iconoclast.render.format.ComponentTableFormat
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class AppfilterCompilerTest {

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
        <resources>
            <!-- comment -->
            <item component="ComponentInfo{com.a/com.a.Main}" drawable="alpha"/>
            <item component="ComponentInfo{com.b/com.b.Calc-Dark}" drawable="beta" name="x"/>
            <item component="ComponentInfo{com.a/com.a.Main}" drawable="alpha"/>
            <item component="ComponentInfo{com.a/com.a.Main}" drawable="beta"/>
            <item component="ComponentInfo{{com.c/com.c.Main}" drawable="alpha"/>
            <item component="ComponentInfo{ com.d/com.d.Main}" drawable="alpha"/>
            <item component="ComponentInfo{com.e/a/com.e.Main}" drawable="alpha"/>
            <item component="ComponentInfo{3com.f/x.Y}" drawable="alpha"/>
            <item component="ComponentInfo{com.g/com.g.Main}"/>
            <item component="ComponentInfo{com.h/com.h.Main}" drawable="missing"/>
            <item component=":BROWSER" drawable="beta"/>
            <calendar component="ComponentInfo{com.cal/com.cal.Main}" prefix="calendar_"/>
            <calendar component="ComponentInfo{com.cal/com.cal.Other}" prefix="calendar_"/>
        </resources>"""

    @Test
    fun compilesSkipsAndCounts() {
        val r = compileAppfilter(xml.byteInputStream(), mapOf("alpha" to 0, "beta" to 1))
        assertEquals(mapOf("com.a/com.a.Main" to 0, "com.b/com.b.Calc-Dark" to 1, ":BROWSER" to 1), r.entries)
        assertEquals(11, r.items)
        assertEquals(2, r.calendarSkipped)
        assertEquals(1, r.shortcuts)
        assertEquals(5, r.malformed.size)
        assertEquals(listOf("com.h/com.h.Main -> missing"), r.unknownDrawable)
        assertEquals(2, r.duplicates)
        assertEquals(listOf("com.a/com.a.Main -> beta"), r.conflicting)
    }

    /** Installed components are always fully qualified, so the short form must be expanded. */
    @Test
    fun shortFormResolvesFromAFullyQualifiedQuery() {
        val xml = """<resources>
            <item component="ComponentInfo{com.s/.ui.Main}" drawable="alpha"/>
            <item component="ComponentInfo{com.t/.Main}" drawable="alpha"/>
            <item component="ComponentInfo{com.t/com.t.Main}" drawable="beta"/>
            <item component="ComponentInfo{com.u/com.u.Main}" drawable="beta"/>
            <item component="ComponentInfo{com.u/.Main}" drawable="alpha"/>
        </resources>"""
        val r = compileAppfilter(xml.byteInputStream(), mapOf("alpha" to 0, "beta" to 1))
        // The fully qualified entry wins whichever comes first.
        assertEquals(mapOf("com.s/com.s.ui.Main" to 0, "com.t/com.t.Main" to 1, "com.u/com.u.Main" to 1), r.entries)
        assertEquals(3, r.expanded)
        assertEquals(listOf("com.t/com.t.Main -> beta, replaces the short form", "com.u/com.u.Main -> alpha"), r.conflicting)
        val table = ComponentTable(ByteBuffer.wrap(ComponentTableFormat.encode(r.entries)))
        assertEquals(0, table.lookup("com.s/com.s.ui.Main"))
    }

    @Test
    fun brokenXmlKeepsEntriesReadBeforeTheError() {
        val broken = """<resources><item component="ComponentInfo{com.a/com.a.Main}" drawable="alpha"/><item component="""
        val r = compileAppfilter(broken.byteInputStream(), mapOf("alpha" to 0))
        assertEquals(mapOf("com.a/com.a.Main" to 0), r.entries)
        assertEquals(1, r.malformed.size)
    }
}
