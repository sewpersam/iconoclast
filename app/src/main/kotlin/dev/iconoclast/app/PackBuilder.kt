package dev.iconoclast.app

import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.AppfilterWriter
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.pack.DrawableXmlWriter
import dev.iconoclast.pack.ZipRewriter
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * What one slot draws, resolved from the theme document's overrides before the build:
 * [geometryName], the drawable whose line art it draws (a swap target, or the slot's own),
 * and [theme], the effective per-icon theme. The slot's own drawable name stays its seed.
 */
class SlotLook(val geometryName: String, val theme: Theme)

/**
 * Renders a coverage plan and rewrites the template into an unsigned, aligned pack. The
 * same looks and plan give the same bytes, spec 3.5. Each drawable of the plan is drawn
 * once, under its own [SlotLook]; overrides never reach this class, only themes and names.
 *
 * Each icon's PNG goes to a file in a work directory as soon as it is drawn, and the
 * rewrite streams those files into the pack, so the heap holds only the icons being drawn,
 * never the whole pack: a grain look runs to about 300 KB an icon.
 */
class PackBuilder(private val source: IconSource, private val renderer: IconRenderer) {

    /**
     * Renders on at most four threads, reports each finished icon, and writes the unsigned
     * pack to [out]. [look] gives each drawable's look by its name. A swap target missing
     * from the icon set draws the slot's own geometry instead. [work] is emptied first and
     * holds the icon files until the rewrite is done. Icons are [resolution] pixels square.
     */
    suspend fun build(
        template: ByteArray, plan: CoveragePlan, look: (drawable: String) -> SlotLook,
        work: File, out: File, resolution: OutputResolution = OutputResolution.FULL,
        progress: (done: Int, total: Int) -> Unit,
    ) {
        val pool = Dispatchers.Default.limitedParallelism(RENDER_THREADS)
        val previewIndex = source.indexOf(PREVIEW_DRAWABLE).takeIf { it >= 0 } ?: plan.drawables.firstOrNull() ?: 0
        val jobs = plan.drawables + previewIndex
        val done = AtomicInteger()
        withContext(Dispatchers.IO) { work.deleteRecursively(); check(work.mkdirs()) { "could not create $work" } }
        try {
            val pngs = coroutineScope {
                jobs.mapIndexed { job, index ->
                    async(pool) {
                        val png = render(index, look, resolution)
                        File(work, "$job.png").also { it.writeBytes(png) }.also { progress(done.incrementAndGet(), jobs.size) }
                    }
                }.awaitAll()
            }
            withContext(Dispatchers.IO) {
                val replacements = HashMap<String, File>()
                for (slot in plan.drawables.indices) replacements[slotPath(slot)] = pngs[slot]
                replacements[PACK_ICON] = pngs.last()
                replacements[APPFILTER] = File(work, "appfilter.xml").apply {
                    writeBytes(AppfilterWriter.write(appfilter(plan)).toByteArray())
                }
                replacements[DRAWABLE_XML] = File(work, "drawable.xml").apply {
                    writeBytes(DrawableXmlWriter.write(plan.drawables.indices.map(::slotName)).toByteArray())
                }
                ZipRewriter.rewrite(template, replacements, out)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /**
     * The pack's appfilter: every installed component of the plan, each under the exact
     * name the system reports, which is what launchers look up; then Arcticons' own
     * entries for the same drawables, so another build of a themed app, with its own
     * activity name, finds its icon too.
     */
    fun appfilter(plan: CoveragePlan): List<Pair<String, String>> {
        val slotOf = plan.drawables.withIndex().associate { (slot, d) -> d to slot }
        val installed = plan.items.mapTo(HashSet()) { it.first }
        val extra = source.components.componentsOf(slotOf.keys).filter { it.first !in installed }
        return plan.items.map { (c, slot) -> c to slotName(slot) } + extra.map { (c, d) -> c to slotName(slotOf.getValue(d)) }
    }

    /** One drawable's PNG: its look's geometry and theme, seeded by its own name. */
    fun render(index: Int, look: (drawable: String) -> SlotLook, resolution: OutputResolution = OutputResolution.FULL): ByteArray {
        val name = source.names.name(index)
        val l = look(name)
        val swapped = source.indexOf(l.geometryName)
        val geometry = if (swapped >= 0) swapped else index
        return renderer.renderPng(source.names.name(geometry), source.icon(geometry), name, l.theme, resolution.px, resolution)
    }

    companion object {
        const val RENDER_THREADS = 4

        /** Every slot in its own geometry under one theme: a document with no overrides. */
        fun uniform(theme: Theme): (String) -> SlotLook = { SlotLook(it, theme) }
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
