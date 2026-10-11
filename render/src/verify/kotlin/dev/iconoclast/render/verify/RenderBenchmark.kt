package dev.iconoclast.render.verify

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.Matte
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.png.PngEncoder
import dev.iconoclast.render.texture.TextureStore
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * JVM timings for spec 3.3's two budgets: 24 icons at 128 px and 300 icons at 432 px.
 * Args: bundle, threads. `./gradlew :render:benchmarkRenderer -Prows=INVERT,JADE_LIKE` limits the theme rows. Icons are spread evenly over the bundle. Each measurement runs
 * after a warm-up pass and reports the best of [RUNS], all with a cold mask cache.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val threads = args[1].toInt()
    val preview = (0 until 24).map { it * (bundle.size / 24) }
    val apply = (0 until 300).map { it * (bundle.size / 300) }
    println("jvm ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, " +
        "${Runtime.getRuntime().availableProcessors()} cpus")
    // One shared texture store, decoded once: the timings are rendering, not texture loads.
    val textures = TextureStore()
    val textured = jadeTextured(textures.put(TextureStore.encode(TEXTURE_SIDE, TEXTURE_SIDE, texturePixels())))
    // M6: each effect alone on the effects base (invert fill, compare INVERT), and the combined jade-like look.
    val effects = dev.iconoclast.render.GoldenSet.EFFECTS.map { (dir, t) -> dir.removePrefix("effect_").uppercase() to t } +
        ("JADE_LIKE" to dev.iconoclast.render.GoldenSet.JADE_LIKE)
    val only = System.getProperty("rows")?.split(",")?.toSet()
    val themes = (FillMode.entries.map { it.name to Theme(fill = FillParams(mode = it)) } + ("JADE" to JADE) + ("JADE_TEXTURED" to textured) + effects)
        .filter { only == null || it.first in only }
    for ((label, theme) in themes) {
        val previewMs = best { IconRenderer(textures = textures).let { r -> preview.forEach { r.render("icon$it", bundle.icon(it), theme, 128) } } }
        val renderMs = best { IconRenderer(textures = textures).let { r -> apply.forEach { r.render("icon$it", bundle.icon(it), theme, 432) } } }
        val pngMs = best { IconRenderer(textures = textures).let { r -> apply.forEach { r.renderPng("icon$it", bundle.icon(it), theme, 432) } } }
        val pool = Executors.newFixedThreadPool(threads)
        val parallelMs = try {
            best {
                val r = IconRenderer(textures = textures)
                pool.invokeAll(apply.map { i -> Callable { r.renderPng("icon$i", bundle.icon(i), theme, 432) } }).forEach { it.get() }
            }
        } finally {
            pool.shutdown()
        }
        val bytes = IconRenderer(textures = textures).let { r -> apply.sumOf { r.renderPng("icon$it", bundle.icon(it), theme, 432).size.toLong() } }
        println("%-18s preview 24@128 %5.0f ms | apply 300@432 render %6.0f ms, render+png %6.0f ms, %d threads %6.0f ms | %d KB/icon"
            .format(label, previewMs, renderMs, pngMs, threads, parallelMs, bytes / 300 / 1024))
    }
    // PNG encoding alone, per icon: the 300 renders are made once, then encoded.
    for ((label, theme) in listOf("JADE" to JADE, "JADE_TEXTURED" to textured, "JADE_LIKE" to dev.iconoclast.render.GoldenSet.JADE_LIKE)) {
        val r = IconRenderer(textures = textures)
        val images = apply.map { r.render("icon$it", bundle.icon(it), theme, 432) }
        val ms = best { images.forEach { PngEncoder.encode(it) } }
        println("%-18s png encode %.1f ms per icon, 300@432".format(label, ms / images.size))
    }
    // A paint-only change: geometry and fill come from the cache.
    val r = IconRenderer()
    val recolored = JADE.recolored
    preview.forEach { r.render("icon$it", bundle.icon(it), JADE, 128) }
    val warm = best { preview.forEach { r.render("icon$it", bundle.icon(it), recolored, 128) } }
    println("JADE preview 24@128, paint change with warm cache %.1f ms".format(warm))
    // The compositing cost alone: 300 icons at full size with geometry and fill cached.
    val big = IconRenderer(dev.iconoclast.render.MaskCache(1L shl 31))
    apply.forEach { big.render("icon$it", bundle.icon(it), JADE, 432) }
    val warmApply = best { apply.forEach { big.render("icon$it", bundle.icon(it), recolored, 432) } }
    println("JADE apply 300@432 render, paint change with warm cache %.0f ms".format(warmApply))
}

/** The M4 Jade preset, flat colour, GoldenSet.PRESETS "jade": the M4 and M5 baseline these numbers compare with. */
private val JADE = Theme(
    fill = FillParams(mode = FillMode.INVERT),
    background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f)),
    layers = Theme.baseLayers(Paint.Solid(0xFF000000.toInt()), body = 0xFF00A86B.toInt(), line = 0xFF00A86B.toInt()),
)

/**
 * Jade with texture: a background texture layer, and the fill textured, masked by levels
 * and feathered, at the new-layer mapping (scale 2), the editor's default.
 */
private fun jadeTextured(hash: String) = JADE.copy(
    layers = JADE.layers.flatMap { l ->
        when (l.id) {
            Layer.BACKGROUND_ID -> listOf(l, PaintLayer("bg-texture", Paint.Texture(hash), clip = ClipTarget.BACKGROUND,
                blend = BlendMode.SCREEN, opacity = 0.5f, mapping = Mapping(scale = 2f)))
            Layer.ICON_FILL_ID -> listOf(l, PaintLayer("fill-texture", Paint.Texture(hash), clip = ClipTarget.ICON_FILL,
                blend = BlendMode.MULTIPLY, mapping = Mapping(scale = 2f), matte = Matte(black = 0.2f, white = 0.8f, feather = 0.01f)))
            else -> listOf(l)
        }
    },
)

private const val TEXTURE_SIDE = 1024

/**
 * An opaque grey stand-in for a photo, premultiplied RGBA: smooth waves at three scales
 * plus +-8 levels of hashed grain, as a camera's sensor noise would give.
 */
private fun texturePixels(): ByteArray {
    val px = ByteArray(TEXTURE_SIDE * TEXTURE_SIDE * 4)
    for (y in 0 until TEXTURE_SIDE) for (x in 0 until TEXTURE_SIDE) {
        val wave = 60 * StrictMath.sin(x / 23.0) + 40 * StrictMath.sin(y / 41.0 + x / 97.0) + 20 * StrictMath.sin((x + y) / 7.0)
        val grain = ((x * 73856093) xor (y * 19349663)).ushr(7) % 17 - 8
        val g = (128 + wave.toInt() + grain).coerceIn(0, 255)
        val o = (y * TEXTURE_SIDE + x) * 4
        px[o] = g.toByte(); px[o + 1] = g.toByte(); px[o + 2] = g.toByte(); px[o + 3] = -1
    }
    return px
}

/** Jade with another fill colour: a paint-only change. */
private val Theme.recolored: Theme
    get() = copy(layers = layers.map { if (it.id == Layer.ICON_FILL_ID) (it as PaintLayer).copy(source = Paint.Solid(0xFF336699.toInt())) else it })

private const val RUNS = 3

private fun best(block: () -> Unit): Double {
    block()
    var best = Double.MAX_VALUE
    repeat(RUNS) {
        val t = System.nanoTime()
        block()
        best = minOf(best, (System.nanoTime() - t) / 1e6)
    }
    return best
}
