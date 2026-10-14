package dev.iconoclast.app.coverage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverageTest {
    private val dir: File = Files.createTempDirectory("coverage").toFile().apply { deleteOnExit() }

    private val appfilter = mapOf(
        "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity" to "fdroid",
        "com.android.camera2/com.android.camera.CameraLauncher" to "camera",
        "org.mozilla.firefox/org.mozilla.fenix.HomeActivity" to "firefox",
        // A calendar app: its <calendar> entry is skipped, its <item> gives the static drawable.
        "org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar" to "acalendar_plus",
    )

    @Test
    fun storeRoundTripsAndDamageReadsAsNeverApplied() {
        val store = CoverageStore(dir)
        assertNull(store.read())
        val c = AppliedCoverage("theme-1", 1_700_000_000_000, setOf("b/b.B", "a/a.A"))
        store.write(c)
        assertEquals(c, store.read())
        assertEquals(c, CoverageStore(dir).read(), "a new store reads the file")
        val withLook = c.copy(look = "abc")
        store.write(withLook)
        assertEquals(withLook, store.read())
        // A file from before the look was stored reads as an unknown look.
        File(dir, "last-apply.json").writeText("""{"version":1,"themeId":"t","appliedAt":1,"components":["a/a.A"]}""")
        assertEquals(AppliedCoverage("t", 1, setOf("a/a.A")), store.read())
        File(dir, "last-apply.json").writeText("{ not json")
        assertNull(store.read())
    }

    @Test
    fun diffListsNewInstallsAndUncoveredApps() {
        val last = AppliedCoverage("t", 5, setOf(
            "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity",
            "com.unknown/com.unknown.Main",
        ))
        val installed = listOf(
            "org.mozilla.firefox/org.mozilla.fenix.HomeActivity",
            "com.unknown/com.unknown.Main",
            "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity",
            "com.android.camera2/com.android.camera.CameraLauncher",
            "com.newer/com.newer.Main",
            "org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar",
        )
        val r = CoverageReport.of(installed, last, appfilter::get, overridden = setOf("camera"))
        assertEquals(installed.sorted(), r.entries.map { it.component })
        assertEquals(
            listOf("com.android.camera2/com.android.camera.CameraLauncher", "com.newer/com.newer.Main",
                "org.mozilla.firefox/org.mozilla.fenix.HomeActivity", "org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar"),
            r.installedSinceApply,
        )
        assertEquals(r.installedSinceApply - "com.newer/com.newer.Main", r.reapplyWouldTheme)
        assertEquals(listOf("com.newer/com.newer.Main", "com.unknown/com.unknown.Main"), r.notCovered)
        val status = r.entries.associate { it.component to it.status }
        assertEquals(CoverageStatus.OVERRIDDEN, status["com.android.camera2/com.android.camera.CameraLauncher"])
        assertEquals(CoverageStatus.THEMED, status["org.withouthat.acalendarplus/org.withouthat.acalendar.ACalendar"])
        assertEquals(CoverageStatus.NOT_IN_ARCTICONS, status["com.unknown/com.unknown.Main"])
    }

    @Test
    fun neverAppliedHasNoNewInstalls() {
        val r = CoverageReport.of(appfilter.keys, null, appfilter::get, emptySet())
        assertEquals(emptyList(), r.installedSinceApply)
        assertEquals(emptyList(), r.notCovered)
        assertEquals(4, r.entries.count { it.status == CoverageStatus.THEMED })
    }
}
