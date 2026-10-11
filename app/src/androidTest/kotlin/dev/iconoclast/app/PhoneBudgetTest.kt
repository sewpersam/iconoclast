package dev.iconoclast.app

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.pack.ZipRewriter
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * Spec 3.3's phone budgets, measured on the device. Each run starts with a new renderer,
 * so the mask cache is cold. The first run in a fresh process also pays for class loading
 * and JIT warm-up: it is logged, and the gate applies to the runs after it. Run each test
 * in its own process (`am instrument -e class ...#method`) to read the cold number.
 */
@RunWith(AndroidJUnit4::class)
class PhoneBudgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** 24 sample icons at 128 px in the plain theme, under 150 ms. */
    @Test
    fun previewOf24IconsAt128Px() {
        val bundle = IconBundle(ByteBuffer.wrap(instrumentation.context.assets.open("samples.bin").use { it.readBytes() }))
        assertEquals(24, GoldenSet.SAMPLES.size)
        val theme = Theme()
        val runs = (0 until RUNS).map {
            val renderer = IconRenderer()
            timed { for (i in GoldenSet.SAMPLES.indices) renderer.render(GoldenSet.SAMPLES[i], bundle.icon(i), theme, PREVIEW_SIZE) }
        }
        Log.i(TAG, "preview 24@$PREVIEW_SIZE plain, ms per run, first is cold: $runs")
        assertTrue("preview runs $runs ms, budget $PREVIEW_BUDGET_MS ms", runs.drop(1).all { it < PREVIEW_BUDGET_MS })
    }

    /** A 300-drawable plan in invert fill at full size, PNGs and zip rewrite included, under 20 s. */
    @Test
    fun applyOf300IconsInInvertFill() = apply(Theme(fill = FillParams(mode = FillMode.INVERT)), "invert")

    /**
     * M6: the same with the combined jade-like look, GoldenSet.JADE_LIKE: procedural jade,
     * veins, textured streaks, a bevel and grain. Task 10 re-runs it with the Jade preset.
     */
    @Test
    fun applyOf300IconsJadeLike() = apply(GoldenSet.JADE_LIKE, "jade-like")

    /**
     * The spec's Jade shape: black rounded square, stroke 1.75, invert fill, procedural jade
     * fill with veins, low-opacity streaks linked to the fill; no bevel, no grain.
     * GoldenSet.JADE_LIKE without those two layers.
     */
    @Test
    fun applyOf300IconsJadeSpecShape() = apply(
        GoldenSet.JADE_LIKE.let { t -> t.copy(layers = t.layers.filterNot { it.id == "bevel" || it.id == "grain" }) },
        "jade-spec",
    )

    /** M6 Task 10: the shipped Jade preset, read from its JSON in the app's assets as the app reads it. */
    @Test
    fun applyOf300IconsJadePreset() = apply(preset("jade.json"), "jade-preset")

    /**
     * M7 Task 11: the Jade preset as a compact, 324 px pack. Compared with
     * [applyOf300IconsJadePreset] in the same session; the budget is stated at 432 px.
     */
    @Test
    fun applyOf300IconsJadePresetCompact() {
        measure(preset("jade.json"), "jade-preset-324", resolution = OutputResolution.COMPACT)
    }

    /** The Neon preset, the third starter: outline, gradient line, outer glow. */
    @Test
    fun applyOf300IconsNeonPreset() = apply(preset("neon.json"), "neon-preset")

    /**
     * The review's heap case: grain at 0.6 (GoldenSet "effect_grain"), about 300 KB an icon.
     * Before the streaming pack build this held every PNG and two copies of the pack in heap.
     * Also runs the same look without grain and with grain at full amount, for the pack-size
     * note: what grain adds per icon.
     */
    @Test
    fun applyOf300IconsGrain() {
        val grain = GoldenSet.EFFECTS.getValue("effect_grain")
        val base = grain.copy(layers = grain.layers.filterNot { it.id == "grain" })
        val full = grain.copy(layers = grain.layers.map { l ->
            if (l is EffectLayer && l.effect is Effect.Grain) l.copy(effect = (l.effect as Effect.Grain).copy(amount = 1f), opacity = 1f) else l
        })
        val none = measure(base, "grain-none", runs = 1)
        val at06 = measure(grain, "grain-0.6")
        val atFull = measure(full, "grain-full", runs = 1)
        Log.i(TAG, "grain per icon: none ${none / 1024} KB, 0.6 ${at06 / 1024} KB (+${(at06 - none) / 1024}), " +
            "full ${atFull / 1024} KB (+${(atFull - none) / 1024})")
    }

    private fun preset(file: String): Theme =
        migrate(instrumentation.targetContext.assets.open("${Presets.DIR}/$file").use { it.readBytes().decodeToString() }).theme

    /** Builds the pack [runs] times; gates the warm runs, logs time and peak heap. Returns the mean icon PNG size. */
    private fun apply(theme: Theme, label: String) {
        measure(theme, label)
    }

    private fun measure(theme: Theme, label: String, runs: Int = RUNS, resolution: OutputResolution = OutputResolution.FULL): Long {
        val target = instrumentation.targetContext.assets
        val source = IconSource.open(target)
        val template = target.open(PackBuilder.TEMPLATE_ASSET).use { it.readBytes() }
        val drawables = (0 until APPLY_ICONS).map { it * (source.bundle.size / APPLY_ICONS) }
        val plan = CoveragePlan(
            drawables = drawables,
            items = drawables.indices.map { "budget.app$it/budget.app$it.Main" to it },
            unmatched = emptyList(),
        )
        val dir = File(instrumentation.targetContext.cacheDir, "budget").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "unsigned.apk")
        val rt = Runtime.getRuntime()
        var iconBytes = 0L
        val times = (0 until runs).map {
            System.gc()
            val heap = HeapPeak()
            val ms = try {
                timed { runBlocking { PackBuilder(source, IconRenderer()).build(template, plan, PackBuilder.uniform(theme), File(dir, "icons"), apk, resolution) { _, _ -> } } }
            } finally {
                heap.stop()
            }
            ZipFile(apk).use { z ->
                assertEquals(ZipRewriter.entryNames(template).size, z.size())
                iconBytes = (0 until APPLY_ICONS).sumOf { z.getEntry(PackBuilder.slotPath(it)).size } / APPLY_ICONS
            }
            Log.i(TAG, "apply run $label: $ms ms, ${apk.length()} bytes, ${iconBytes / 1024} KB per icon, " +
                "peak java heap ${heap.java / MB} MB of ${rt.maxMemory() / MB} MB, peak native heap ${heap.native / MB} MB")
            ms
        }
        Log.i(TAG, "apply $APPLY_ICONS@${resolution.px} $label, ${PackBuilder.RENDER_THREADS} threads, " +
            "${rt.availableProcessors()} cpus, ms per run, first is cold: $times")
        dir.deleteRecursively()
        assertTrue("apply runs $times ms, budget $APPLY_BUDGET_MS ms", times.drop(1).all { it < APPLY_BUDGET_MS })
        return iconBytes
    }

    /** Samples the used Java heap and the allocated native heap every 5 ms until [stop]. */
    private class HeapPeak {
        @Volatile private var running = true
        @Volatile var java = 0L; private set
        @Volatile var native = 0L; private set
        private val thread = Thread {
            val rt = Runtime.getRuntime()
            while (running) {
                java = maxOf(java, rt.totalMemory() - rt.freeMemory())
                native = maxOf(native, Debug.getNativeHeapAllocatedSize())
                Thread.sleep(5)
            }
        }.apply { isDaemon = true; start() }

        fun stop() {
            running = false
            thread.join()
        }
    }

    private inline fun timed(block: () -> Unit): Long {
        val t = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - t) / 1_000_000
    }

    private companion object {
        const val TAG = "PhoneBudget"
        const val RUNS = 4
        const val PREVIEW_SIZE = 128
        const val PREVIEW_BUDGET_MS = 150L
        const val APPLY_ICONS = 300
        const val APPLY_BUDGET_MS = 20_000L
        const val MB = 1024 * 1024
    }
}
