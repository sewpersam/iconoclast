package dev.iconoclast.iconsource

import dev.iconoclast.render.format.ComponentTable
import dev.iconoclast.render.format.ComponentTableFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Store builds rename launcher activities, so an installed app's exact component is often
 * missing from the appfilter while its package is there. The pack is built on the phone,
 * so it maps the installed component to the package's drawable.
 */
class AppFallbackTest {
    private val names = DrawableNames.parse(File(requireNotNull(System.getProperty("arcticons.names"))).readBytes())
    private val index = ComponentIndex(ComponentTable(ByteBuffer.wrap(File(requireNotNull(System.getProperty("arcticons.components"))).readBytes())))

    private fun app(component: String) = index.resolveApp(component).let { if (it < 0) null else names.name(it) }

    @Test
    fun exactEntriesStillWin() {
        assertEquals("antennapod", app("de.danoeh.antennapod/de.danoeh.antennapod.activity.SplashActivity"))
        assertEquals("opencamera", app("app.grapheneos.camera/app.grapheneos.camera.ui.activities.MainActivity"))
    }

    @Test
    fun aRenamedActivityFallsBackToItsPackage() {
        assertEquals(-1, index.resolve("com.aurora.store/com.aurora.store.SomeFutureActivity"))
        assertEquals("aurora_store", app("com.aurora.store/com.aurora.store.SomeFutureActivity"))
        assertEquals("antennapod", app("de.danoeh.antennapod/de.danoeh.antennapod.ui.NewMain"))
        assertEquals("antennapod", app("de.danoeh.antennapod/.ui.NewMain"))
    }

    @Test
    fun aPackagePrefixIsNotAPackage() {
        // com.aurora.store4 and com.aurora.store.nightly are other packages, not com.aurora.store.
        assertEquals(null, app("com.aurora.stor/com.aurora.store.MainActivity"))
        assertEquals(null, app("com.unknown.app/com.unknown.app.Main"))
        assertEquals(null, app(":BROWSER_UNKNOWN"))
    }

    @Test
    fun theMostUsedDrawableOfAPackageWinsAndTiesGoLow() {
        val table = ComponentIndex(
            ComponentTable(
                ByteBuffer.wrap(
                    ComponentTableFormat.encode(
                        mapOf("a.b/a.b.One" to 7, "a.b/a.b.Two" to 3, "a.b/a.b.Three" to 3, "c.d/c.d.X" to 9, "c.d/c.d.Y" to 4, "a.bc/a.bc.Z" to 1),
                    ),
                ),
            ),
        )
        assertEquals(3, table.resolveApp("a.b/a.b.Other"))
        assertEquals(4, table.resolveApp("c.d/c.d.Other"))
        assertEquals(-1, table.resolveApp("a/a.Other"))
        assertEquals(listOf("a.b/a.b.Three" to 3, "a.b/a.b.Two" to 3, "c.d/c.d.Y" to 4), table.componentsOf(setOf(3, 4)))
    }

    @Test
    fun componentsOfListsEveryBuildOfADrawable() {
        val d = names.indexOf("aurora_store")
        val all = index.componentsOf(setOf(d)).map { it.first }
        assertTrue("com.aurora.store/com.aurora.store.MainActivity" in all)
        assertTrue("com.aurora.store.nightly/com.aurora.store.MainActivity" in all)
    }
}
