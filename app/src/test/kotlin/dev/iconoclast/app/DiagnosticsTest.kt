package dev.iconoclast.app

import dev.iconoclast.pack.AppfilterWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsTest {
    @Test
    fun `reads back the appfilter the pack writes, escapes included`() {
        val items = listOf(
            "com.brave.browser/com.google.android.apps.chrome.Main" to "slot_0001",
            "a.b/a.b.Main\$Alias&Co" to "slot_0002",
        )
        assertEquals(items.toMap(), Diagnostics.parseAppfilter(AppfilterWriter.write(items)))
    }

    @Test
    fun `one line per app, missing components flagged and counted`() {
        val apps = listOf(
            Diagnostics.App("org.videolan.vlc", "main", "dev.imranr.obtainium", "10-16 09:00", listOf(
                Diagnostics.Component("org.videolan.vlc.StartActivity", "slot_0003", "vlc"),
            )),
            Diagnostics.App("app.grapheneos.camera", "main", null, null, listOf(
                Diagnostics.Component("app.grapheneos.camera.ui.activities.MainActivity", null, "camera"),
                Diagnostics.Component("other.Alias", null, null),
            )),
        )
        val text = Diagnostics.format(listOf("Iconoclast 0.1"), apps)
        assertEquals(
            listOf(
                "Iconoclast 0.1",
                "apps 2, components missing from pack but in Arcticons: 1",
                "app.grapheneos.camera main via=- upd=- | .ui.activities.MainActivity pack=MISSING arc=camera ; other.Alias pack=MISSING arc=none",
                "org.videolan.vlc main via=dev.imranr.obtainium upd=10-16 09:00 | .StartActivity pack=slot_0003 arc=vlc",
            ),
            text.trimEnd().lines(),
        )
    }
}
