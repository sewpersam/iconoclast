package dev.iconoclast.render.verify

import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Marble
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

/**
 * The background Size contact sheet: the [GoldenSet.BG_SIZE] looks at 100, 85, 70 and 50%,
 * icon size as the look sets it. Args: the golden samples.bin and an output directory.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val dir = File(args[1]).apply { mkdirs() }
    val renderer = IconRenderer()
    renderer.textures.put(Marble.PNG)
    val looks = listOf("Gradient" to "bg_gradient_75", "Image, crop, random" to "bg_image_75", "Pattern, finish, border" to "bg_layers_50")
    val rows = looks.flatMap { (label, key) ->
        val t = GoldenSet.BG_SIZE.getValue(key)
        listOf(1f, 0.85f, 0.7f, 0.5f).map { s -> "$label ${(s * 100).toInt()}%" to t.copy(background = t.background.copy(size = s)) }
    }
    write(File(dir, "bg-size.png"), rows, listOf("fdroid", "camera", "spotify", "weather"), 160, 0xFF5A5F66.toInt(), renderer, bundle)
}
