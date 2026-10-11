package dev.iconoclast.app

import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.AppfilterWriter
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.pack.DrawableXmlWriter
import dev.iconoclast.pack.ZipRewriter
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renders a coverage plan under a theme and rewrites the template into an unsigned,
 * aligned pack. The same theme and plan give the same bytes, spec 3.5.
 */
class PackBuilder(private val source: IconSource, private val renderer: IconRenderer) {

    /** Renders on at most four threads and reports each finished icon. */
    suspend fun build(template: ByteArray, plan: CoveragePlan, theme: Theme, progress: (done: Int, total: Int) -> Unit): ByteArray {
        val pool = Dispatchers.Default.limitedParallelism(RENDER_THREADS)
        val previewIndex = source.indexOf(PREVIEW_DRAWABLE).takeIf { it >= 0 } ?: plan.drawables.firstOrNull() ?: 0
        val jobs = plan.drawables + previewIndex
        val done = AtomicInteger()
        val pngs = coroutineScope {
            jobs.map { index ->
                async(pool) {
                    renderer.renderPng(source.names.name(index), source.icon(index), theme, OUTPUT_SIZE).also {
                        progress(done.incrementAndGet(), jobs.size)
                    }
                }
            }.awaitAll()
        }
        val replacements = HashMap<String, ByteArray>()
        for (slot in plan.drawables.indices) replacements[slotPath(slot)] = pngs[slot]
        replacements[PACK_ICON] = pngs.last()
        replacements[APPFILTER] = AppfilterWriter.write(plan.items.map { (c, slot) -> c to slotName(slot) }).toByteArray()
        replacements[DRAWABLE_XML] = DrawableXmlWriter.write(plan.drawables.indices.map(::slotName)).toByteArray()
        return ZipRewriter.rewrite(template, replacements)
    }

    companion object {
        const val RENDER_THREADS = 4
        const val TEMPLATE_ASSET = "pack/template.apk"
        const val PREVIEW_DRAWABLE = "arcticons"
        private const val PACK_ICON = "res/drawable-nodpi-v4/pack_icon.png"
        private const val APPFILTER = "assets/appfilter.xml"
        private const val DRAWABLE_XML = "assets/drawable.xml"

        fun slotName(slot: Int) = "slot_%04d".format(java.util.Locale.ROOT, slot)
        fun slotPath(slot: Int) = "res/drawable-nodpi-v4/${slotName(slot)}.png"

        /** The number of slots the template was built with. */
        fun slotCount(template: ByteArray) = ZipRewriter.entryNames(template).count { it.startsWith("res/drawable-nodpi-v4/slot_") }
    }
}
