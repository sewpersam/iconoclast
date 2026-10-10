package dev.icondesigner.render.verify

import dev.icondesigner.render.FillMode
import dev.icondesigner.render.FillParams
import dev.icondesigner.render.IconRenderer
import dev.icondesigner.render.Theme
import dev.icondesigner.render.format.IconBundle
import dev.icondesigner.render.png.PngEncoder
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
    for (mode in FillMode.entries) {
        val theme = Theme(fill = FillParams(mode = mode))
        val previewMs = best { IconRenderer().let { r -> preview.forEach { r.render(it, bundle.icon(it), theme, 128) } } }
        val renderMs = best { IconRenderer().let { r -> apply.forEach { r.render(it, bundle.icon(it), theme, 432) } } }
        val pngMs = best { IconRenderer().let { r -> apply.forEach { r.renderPng(it, bundle.icon(it), theme, 432) } } }
        val pool = Executors.newFixedThreadPool(threads)
        val parallelMs = try {
            best {
                val r = IconRenderer()
                pool.invokeAll(apply.map { i -> Callable { r.renderPng(i, bundle.icon(i), theme, 432) } }).forEach { it.get() }
            }
        } finally {
            pool.shutdown()
        }
        val bytes = IconRenderer().let { r -> apply.sumOf { r.renderPng(it, bundle.icon(it), theme, 432).size.toLong() } }
        println("%-18s preview 24@128 %5.0f ms | apply 300@432 render %6.0f ms, render+png %6.0f ms, %d threads %6.0f ms | %d KB/icon"
            .format(mode, previewMs, renderMs, pngMs, threads, parallelMs, bytes / 300 / 1024))
    }
    // A paint-only change: geometry and fill come from the cache.
    val r = IconRenderer()
    val theme = Theme(fill = FillParams(mode = FillMode.INVERT))
    preview.forEach { r.render(it, bundle.icon(it), theme, 128) }
    val warm = best { preview.forEach { r.render(it, bundle.icon(it), theme.copy(glyph = theme.glyph.copy(body = 0xFF336699.toInt())), 128) } }
    println("INVERT preview 24@128, paint change with warm cache %.0f ms".format(warm))
}

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
