package dev.iconoclast.render.verify

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.MAX_ICON_SCALE
import dev.iconoclast.render.Marble
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

/**
 * The icon-follows-background contact sheet, schema 11, at 100, 85, 70 and 50%. The
 * "Before" rows draw schema 10's layout: the icon kept its canvas size, so the shape
 * trimmed it; drawn here as the migration writes it. Args: samples.bin and an output dir.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val dir = File(args[1]).apply { mkdirs() }
    val renderer = IconRenderer()
    renderer.textures.put(Marble.PNG)
    fun at(t: Theme, s: Float) = t.copy(background = t.background.copy(size = s))
    fun before(t: Theme, s: Float) = at(t, s).let { it.copy(shape = it.shape.copy(iconScale = (it.shape.iconScale / s).coerceAtMost(MAX_ICON_SCALE))) }
    val sizes = listOf(1f, 0.85f, 0.7f, 0.5f)
    val plain = Theme()
    val gradient = GoldenSet.BG_SIZE.getValue("bg_gradient_75")
    val layers = GoldenSet.BG_SIZE.getValue("bg_layers_50")
    val rows = sizes.map { "Before, default ${(it * 100).toInt()}%" to before(plain, it) } +
        sizes.map { "Now, default ${(it * 100).toInt()}%" to at(plain, it) } +
        sizes.map { "Now, gradient ${(it * 100).toInt()}%" to at(gradient, it) } +
        sizes.map { "Now, pattern, border ${(it * 100).toInt()}%" to at(layers, it) }
    write(File(dir, "icon-follows-bg.png"), rows, listOf("fdroid", "camera", "spotify", "weather"), 160, 0xFF5A5F66.toInt(), renderer, bundle)
}
