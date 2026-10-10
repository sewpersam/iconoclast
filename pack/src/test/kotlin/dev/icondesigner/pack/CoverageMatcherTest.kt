package dev.icondesigner.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoverageMatcherTest {
    // Mirrors the compiled appfilter: calendar apps resolve through their static <item>.
    private val table = mapOf(
        "org.mozilla.firefox/org.mozilla.fenix.HomeActivity" to 40,
        "org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity" to 40,
        "com.google.android.calendar/com.android.calendar.AllInOneActivity" to 7,
        "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity" to 12,
    )
    private val resolve: (String) -> Int = { table[it] ?: -1 }

    @Test
    fun sharesOneSlotPerDrawableAndKeepsUnmatchedApart() {
        val plan = CoverageMatcher.match(
            listOf(
                "org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity",
                "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity",
                "com.unknown/com.unknown.Main",
                "org.mozilla.firefox/org.mozilla.fenix.HomeActivity",
                "com.google.android.calendar/com.android.calendar.AllInOneActivity",
            ),
            slotCount = 1024, resolve = resolve,
        )
        assertEquals(listOf(7, 12, 40), plan.drawables)
        assertEquals(
            listOf(
                "com.google.android.calendar/com.android.calendar.AllInOneActivity" to 0,
                "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity" to 1,
                "org.mozilla.firefox/org.mozilla.fenix.HomeActivity" to 2,
                "org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity" to 2,
            ),
            plan.items,
        )
        assertEquals(listOf("com.unknown/com.unknown.Main"), plan.unmatched)
    }

    @Test
    fun planDependsOnlyOnTheSet() {
        val a = CoverageMatcher.match(table.keys.toList(), 1024, resolve)
        val b = CoverageMatcher.match(table.keys.reversed() + table.keys.first(), 1024, resolve)
        assertEquals(a.items, b.items)
        assertEquals(a.drawables, b.drawables)
    }

    @Test
    fun tooManyDrawablesIsAClearError() {
        val e = assertFailsWith<TooManyIconsException> { CoverageMatcher.match(table.keys, 2, resolve) }
        assertEquals(3, e.needed)
        assertEquals(2, e.available)
    }
}
