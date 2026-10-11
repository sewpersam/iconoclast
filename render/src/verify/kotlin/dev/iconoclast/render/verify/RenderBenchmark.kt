package dev.iconoclast.render.verify

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import dev.iconoclast.render.png.PngEncoder
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * JVM timings for spec 3.3's two budgets: 24 icons at 128 px and 300 icons at 432 px.
 * Args: bundle, threads. Icons are spread evenly over the bundle. Each measurement runs
 * after a warm-up pass and reports the best of [RUNS], all with a cold mask cache.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val threads = args[1].toInt()
    val preview = (0 until 24).map { it * (bundle.size / 24) }
    val apply = (0 until 300).map { it * (bundle.size / 300) }
    println("jvm ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, " +
        "${Runtime.getRuntime().availableProcessors()} cpus")
    val themes = FillMode.entries.map { it.name to Theme(fill = FillParams(mode = it)) } + ("JADE" to JADE)
    for ((label, theme) in themes) {
        val previewMs = best { IconRenderer().let { r -> preview.forEach { r.render("icon$it", bundle.icon(it), theme, 128) } } }
        val renderMs = best { IconRenderer().let { r -> apply.forEach { r.render("icon$it", bundle.icon(it), theme, 432) } } }
        val pngMs = best { IconRenderer().let { r -> apply.forEach { r.renderPng("icon$it", bundle.icon(it), theme, 432) } } }
        val pool = Executors.newFixedThreadPool(threads)
        val parallelMs = try {
            best {
                val r = IconRenderer()
                pool.invokeAll(apply.map { i -> Callable { r.renderPng("icon$i", bundle.icon(i), theme, 432) } }).forEach { it.get() }
            }
        } finally {
            pool.shutdown()
        }
        val bytes = IconRenderer().let { r -> apply.sumOf { r.renderPng("icon$it", bundle.icon(it), theme, 432).size.toLong() } }
        println("%-18s preview 24@128 %5.0f ms | apply 300@432 render %6.0f ms, render+png %6.0f ms, %d threads %6.0f ms | %d KB/icon"
            .format(label, previewMs, renderMs, pngMs, threads, parallelMs, bytes / 300 / 1024))
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

/** The Jade preset, app/src/main/assets/presets/jade.json, built in code: :render has no JSON. */
private val JADE = Theme(
    fill = FillParams(mode = FillMode.INVERT),
    background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f)),
    layers = Theme.baseLayers(Paint.Solid(0xFF000000.toInt()), body = 0xFF00A86B.toInt(), line = 0xFF00A86B.toInt()),
)

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
