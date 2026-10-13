package dev.iconoclast.app.theme

import dev.iconoclast.render.Theme
import dev.iconoclast.render.texture.TextureStore
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The export's entry times are 1980-01-01 00:00 local, in any zone, so its bytes never depend on the phone's zone. */
class ThemeZipTimeTest {
    private val doc = ThemeDocument(id = "a", name = "Jade", created = 1, modified = 2, theme = Theme())

    private fun exportIn(zone: String): ByteArray {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try {
            return ThemeZipExport.write(doc, TextureStore())
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun sameBytesInEveryZone() {
        val utc = exportIn("UTC")
        for (zone in listOf("America/New_York", "Asia/Kolkata", "Pacific/Kiritimati")) assertContentEquals(utc, exportIn(zone), zone)
        ZipInputStream(ByteArrayInputStream(utc)).use { zip ->
            val e = generateSequence { zip.nextEntry }.first()
            assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0), e.timeLocal)
        }
    }
}
