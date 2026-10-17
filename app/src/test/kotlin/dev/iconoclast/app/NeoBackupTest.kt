package dev.iconoclast.app

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NeoBackupTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                z.putNextEntry(ZipEntry(n)); z.write(d.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `rewrite swaps only the named entries and keeps order`() {
        val src = zip("meta" to "m", "launcher_5_5_5.db" to "layout", "NeoLauncher.db" to "old", "NeoLauncher.db-wal" to "wal")
        val out = NeoBackup.rewrite(src, mapOf("NeoLauncher.db" to "new".toByteArray(), "NeoLauncher.db-wal" to ByteArray(0)))
        val e = NeoBackup.entries(out)
        assertEquals(listOf("meta", "launcher_5_5_5.db", "NeoLauncher.db", "NeoLauncher.db-wal"), e.map { it.first })
        assertContentEquals("layout".toByteArray(), e[1].second)
        assertContentEquals("new".toByteArray(), e[2].second)
        assertEquals(0, e[3].second.size)
    }

    @Test
    fun `report flags custom icons whose pack is gone`() {
        val lines = NeoBackup.format(
            listOf(
                NeoBackup.Override("org.videolan.vlc/org.videolan.vlc.StartActivity#0", "dev.iconoclast.app.pack", "slot_0019"),
                NeoBackup.Override("com.android.dialer/com.android.dialer.main.impl.MainActivity#0", "com.donnnno.arcticons", "phone"),
            ),
        ) { it == "com.donnnno.arcticons" }
        assertEquals(
            listOf(
                "neo backup: 2 custom icons",
                "custom com.android.dialer/com.android.dialer.main.impl.MainActivity#0 -> com.donnnno.arcticons phone installed",
                "custom org.videolan.vlc/org.videolan.vlc.StartActivity#0 -> dev.iconoclast.app.pack slot_0019 PACK GONE",
            ),
            lines,
        )
    }
}
