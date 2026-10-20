package dev.iconoclast.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NeoRowTest {
    private val ours = "dev.iconoclast.app.pack.g000013"

    @Test
    fun `Neo order - system, lawnicons, themed, then packs by label, one per package`() {
        val found = listOf(
            ours to "Iconoclast Pack",
            "com.donnnno.arcticons.daynight" to "Arcticons Day & Night",
            "com.donnnno.arcticons" to "Arcticons",
            "com.donnnno.arcticons.light" to "Arcticons Black",
            // The same pack answers a second intent: one entry, first place.
            "com.donnnno.arcticons" to "Arcticons",
        )
        assertEquals(
            listOf("", "themed", "com.donnnno.arcticons", "com.donnnno.arcticons.light", "com.donnnno.arcticons.daynight", ours),
            NeoRow.order(found, lawnicons = false),
        )
        assertEquals(listOf("", "app.lawnchair.lawnicons", "themed"), NeoRow.order(emptyList(), lawnicons = true))
    }

    @Test
    fun `position counts only packs that show an icon`() {
        val e = listOf(
            NeoRow.Entry("", true),
            NeoRow.Entry("themed", false),
            NeoRow.Entry("com.donnnno.arcticons", true),
            NeoRow.Entry("com.donnnno.arcticons.light", false),
            NeoRow.Entry(ours, true),
            NeoRow.Entry("z.pack", true),
        )
        assertEquals(2 to 4, NeoRow.position(e, ours))
    }

    @Test
    fun `no position when the pack shows no icon or shows twice`() {
        assertNull(NeoRow.position(listOf(NeoRow.Entry("", true), NeoRow.Entry(ours, false)), ours))
        assertNull(NeoRow.position(listOf(NeoRow.Entry(ours, true), NeoRow.Entry(ours, true)), ours))
    }
}
