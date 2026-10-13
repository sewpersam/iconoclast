package dev.iconoclast.render.verify

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.png.Deflate
import dev.iconoclast.render.png.PngEncoder
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer

/**
 * The brush strokes contact sheet: 12 sample glyphs under the Jade starter, Brush A, B
 * and C, and the M6 Jade's streaks (Scanlines) for comparison, on the JVM render path.
 * Args: the golden samples.bin, the output PNG, the tile size in pixels.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val out = File(args[1])
    val tile = args.getOrNull(2)?.toInt() ?: 216
    val names = listOf("fdroid", "camera", "google_chrome", "signal", "spotify", "weather",
        "calculator", "clock", "settings", "termux", "wikipedia", "whatsapp")
    val rows: List<Pair<String, Theme>> = listOf(
        "Jade" to GoldenSet.STARTERS.getValue("preset_jade"),
        "Brush A" to GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_a"),
        "Brush B" to GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_b"),
        "Brush C" to GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_c"),
        "Scanlines (old Jade)" to GoldenSet.JADE_M6,
    )
    val label = 28
    val gap = 8
    val w = names.size * (tile + gap) + gap
    val h = rows.size * (tile + gap + label) + gap
    val sheet = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = Color(0x5A, 0x5F, 0x66)
    g.fillRect(0, 0, w, h)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
    val renderer = IconRenderer()
    val started = System.nanoTime()
    for ((r, row) in rows.withIndex()) {
        val (title, theme) = row
        val top = gap + r * (tile + gap + label)
        g.color = Color.WHITE
        g.drawString(title, gap, top + 20)
        for ((c, name) in names.withIndex()) {
            val img = renderer.render(name, bundle.icon(GoldenSet.SAMPLES.indexOf(name)), theme, tile)
            val x0 = gap + c * (tile + gap)
            val y0 = top + label
            for (y in 0 until tile) for (x in 0 until tile) {
                val o = 4 * (y * tile + x)
                val a = img.pixels[o + 3].toInt() and 0xFF
                val bg = sheet.getRGB(x0 + x, y0 + y)
                fun ch(i: Int, shift: Int) = minOf(255, (img.pixels[o + i].toInt() and 0xFF) + ((bg shr shift) and 0xFF) * (255 - a) / 255)
                sheet.setRGB(x0 + x, y0 + y, (0xFF shl 24) or (ch(0, 16) shl 16) or (ch(1, 8) shl 8) or ch(2, 0))
            }
        }
    }
    g.dispose()
    println("rendered ${rows.size * names.size} tiles at $tile px in ${(System.nanoTime() - started) / 1_000_000} ms")
    val px = ByteArray(4 * w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val v = sheet.getRGB(x, y)
        val o = 4 * (y * w + x)
        px[o] = (v shr 16).toByte()
        px[o + 1] = (v shr 8).toByte()
        px[o + 2] = v.toByte()
        px[o + 3] = 0xFF.toByte()
    }
    out.parentFile.mkdirs()
    out.writeBytes(PngEncoder.encode(w, h, px, Deflate.Effort.FAST))
    println("wrote $out")
}
