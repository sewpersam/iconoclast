package dev.iconoclast.iconsource

import dev.iconoclast.render.format.ComponentTable
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Iconoclast's own glyph, `iconsource/bundled/`: in the icon set as `iconoclast` and mapped
 * to the app's launcher activity, so every pack, and the Neo shotgun, theme Iconoclast too.
 */
class BundledGlyphTest {
    private val names = DrawableNames.parse(File(requireNotNull(System.getProperty("arcticons.names"))).readBytes())
    private val index = ComponentIndex(ComponentTable(ByteBuffer.wrap(File(requireNotNull(System.getProperty("arcticons.components"))).readBytes())))

    @Test
    fun theLauncherActivityResolvesToTheGlyph() {
        val app = Properties().apply { File("../gradle.properties").reader().use { load(it) } }.getProperty("iconoclast.applicationId")
        val manifest = File("../app/src/main/AndroidManifest.xml").readText()
        assertTrue("android:name=\".MainActivity\"" in manifest, "the launcher activity is MainActivity")
        val glyph = names.indexOf("iconoclast")
        assertTrue(glyph >= 0, "the glyph is in the icon set")
        assertEquals(glyph, index.resolve("$app/dev.iconoclast.app.MainActivity"))
    }

    @Test
    fun arcticonsEntriesStillResolve() {
        assertEquals("acalendar_plus", names.name(index.resolve("org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar")))
    }
}
