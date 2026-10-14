package dev.iconoclast.render.verify

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Marble
import dev.iconoclast.render.StrokeStyle
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

/**
 * Painted brush strokes, one sheet per tile size. Args: samples.bin, output directory, file
 * prefix. Writes <prefix>-216.png (every Painted look at a phone grid size), <prefix>-128.png (the editor
 * preview size) and
 * <prefix>-432.png (three samples at pack size, to judge the paint texture).
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val dir = File(args[1]).apply { mkdirs() }
    val prefix = args[2]
    val renderer = IconRenderer()
    renderer.textures.put(Marble.PNG)
    fun styled(t: Theme, f: (Effect.BrushStrokes) -> Effect.BrushStrokes) = t.copy(layers = t.layers.map { l ->
        if (l is EffectLayer && l.effect is Effect.BrushStrokes) l.copy(effect = f(l.effect as Effect.BrushStrokes)) else l
    })
    val painted: (Effect.BrushStrokes) -> Effect.BrushStrokes = { it.copy(style = StrokeStyle.PAINTED) }
    val swipes = GoldenSet.FEEDBACK.getValue("fb_strokes_parallel")
    val rows = listOf(
        "All one way: 3 broad swipes, 30 deg" to swipes,
        "All one way: 2 swipes, 60 deg, wide" to styled(swipes) { it.copy(count = 2, angle = 60f, width = 0.11f) },
        "All one way: 6 thinner, 330 deg" to styled(swipes) { it.copy(count = 6, angle = 330f, width = 0.05f, length = 0.8f) },
        "All one way: 1 swipe, 180 deg, red" to styled(swipes) { it.copy(count = 1, angle = 180f, width = 0.16f, color = 0xFFC8452F.toInt()) },
        "Outward from bottom-left corner" to GoldenSet.FEEDBACK.getValue("fb_strokes_corner"),
        "Outward, defaults (Add sheet)" to styled(GoldenSet.FEEDBACK.getValue("fb_strokes_corner")) { Effect.BrushStrokes(style = StrokeStyle.PAINTED, color = 0xFFE8D5B0.toInt()) },
        "Jade, Painted" to styled(GoldenSet.STARTERS.getValue("preset_jade"), painted),
        "Brush B, Painted" to styled(GoldenSet.BRUSH_VARIANTS.getValue("preset_brush_b"), painted),
    )
    val backdrop = 0xFF5A5F66.toInt()
    write(File(dir, "$prefix-216.png"), rows, GoldenSet.LOOK_SAMPLES, 216, backdrop, renderer, bundle)
    write(File(dir, "$prefix-128.png"), rows, GoldenSet.LOOK_SAMPLES, 128, backdrop, renderer, bundle)
    write(File(dir, "$prefix-432.png"), listOf(rows[0], rows[1], rows[3], rows[4], rows[7]), listOf("camera", "spotify"), 432, backdrop, renderer, bundle)
    println("wrote $prefix sheets to $dir")
}
