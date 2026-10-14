package dev.iconoclast.render.verify

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Marble
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.StrokeStyle
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
 * Contact sheets for phone feedback round 1, on the JVM render path. Args: the golden
 * samples.bin and an output directory. Writes feedback.png (every [GoldenSet.FEEDBACK]
 * look), strokes.png (brush strokes Classic beside Painted, and all one way), and
 * knockout.png (lines cut through to a light and a dark wallpaper).
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val dir = File(args[1]).apply { mkdirs() }
    val renderer = IconRenderer()
    renderer.textures.put(Marble.PNG)
    val names = GoldenSet.LOOK_SAMPLES
    fun sheet(file: String, rows: List<Pair<String, Theme>>, backdrop: Int = 0xFF5A5F66.toInt(), tile: Int = 180, cols: List<String> = names) {
        val started = System.nanoTime()
        write(File(dir, file), rows, cols, tile, backdrop, renderer, bundle)
        println("$file: ${rows.size * cols.size} tiles in ${(System.nanoTime() - started) / 1_000_000} ms")
    }
    sheet("feedback.png", GoldenSet.FEEDBACK.toList())

    val jade = GoldenSet.STARTERS.getValue("preset_jade")
    val brushB = GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_b")
    fun styled(t: Theme, f: (Effect.BrushStrokes) -> Effect.BrushStrokes) = t.copy(layers = t.layers.map { l ->
        if (l is EffectLayer && l.effect is Effect.BrushStrokes) l.copy(effect = f(l.effect as Effect.BrushStrokes)) else l
    })
    val swipes = GoldenSet.FEEDBACK.getValue("fb_strokes_parallel")
    sheet("strokes.png", listOf(
        "Jade, Classic (before)" to jade,
        "Jade, Painted (after)" to styled(jade) { it.copy(style = StrokeStyle.PAINTED) },
        "Brush B, Classic (before)" to brushB,
        "Brush B, Painted (after)" to styled(brushB) { it.copy(style = StrokeStyle.PAINTED) },
        "All one way: 3 broad swipes, 30 deg" to swipes,
        "All one way: 2 swipes, steep" to styled(swipes) { it.copy(count = 2, angle = 60f, width = 0.11f) },
        "All one way: 6 thinner, 330 deg" to styled(swipes) { it.copy(count = 6, angle = 330f, width = 0.05f, length = 0.8f) },
        "All one way: 30 fine, Classic" to styled(swipes) { it.copy(count = 30, width = 0.02f, style = StrokeStyle.CLASSIC) },
        "Outward from bottom-left corner" to GoldenSet.FEEDBACK.getValue("fb_strokes_corner"),
    ), tile = 200)

    // Edit latency on the JVM: the 24 samples at the 128 px preview size, masks warm, one thread.
    val bench = listOf("plain (reference)" to GoldenSet.PRESETS.getValue("plain"), "jade (reference)" to jade) + GoldenSet.FEEDBACK.toList()
    for ((title, theme) in bench) {
        val r = IconRenderer().also { it.textures.put(Marble.PNG) }
        // Each pass a new pattern seed, as a pattern slider drag gives: no cached field.
        fun pass(k: Int) = theme.copy(layers = theme.layers.map { l ->
            if (l is PaintLayer && l.source is Paint.Noise) l.copy(source = (l.source as Paint.Noise).copy(seed = k)) else l
        }).let { t -> GoldenSet.SAMPLES.forEach { n -> r.render(n, bundle.icon(GoldenSet.SAMPLES.indexOf(n)), t, 128) } }
        pass(100)
        val times = (1..5).map { val t0 = System.nanoTime(); pass(it); (System.nanoTime() - t0) / 1_000_000 }.sorted()
        println("bench $title: 24 icons at 128 px, median ${times[2]} ms")
    }

    val knock = GoldenSet.FEEDBACK.getValue("fb_knockout")
    sheet("knockout.png", listOf("Lines cut through, light wallpaper" to knock), backdrop = 0xFFE9E4D8.toInt())
    sheet("knockout-dark.png", listOf("Lines cut through, dark wallpaper" to knock), backdrop = 0xFF101418.toInt())
}

/** One row per theme, one column per sample, each tile over [backdrop] as a wallpaper would show it. */
private fun write(out: File, rows: List<Pair<String, Theme>>, names: List<String>, tile: Int, backdrop: Int, renderer: IconRenderer, bundle: IconBundle) {
    val label = 26
    val gap = 8
    val w = names.size * (tile + gap) + gap
    val h = rows.size * (tile + gap + label) + gap
    val sheet = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = Color(backdrop, true)
    g.fillRect(0, 0, w, h)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
    val lightBg = ((backdrop shr 8) and 0xFF) > 128
    for ((r, row) in rows.withIndex()) {
        val (title, theme) = row
        val top = gap + r * (tile + gap + label)
        g.color = if (lightBg) Color.BLACK else Color.WHITE
        g.drawString(title, gap, top + 18)
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
    val px = ByteArray(4 * w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val v = sheet.getRGB(x, y)
        val o = 4 * (y * w + x)
        px[o] = (v shr 16).toByte()
        px[o + 1] = (v shr 8).toByte()
        px[o + 2] = v.toByte()
        px[o + 3] = 0xFF.toByte()
    }
    out.writeBytes(PngEncoder.encode(w, h, px, Deflate.Effort.FAST))
}
