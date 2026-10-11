package dev.iconoclast.app

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.pack.ZipRewriter
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

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

    private fun apply(theme: Theme, label: String) {
        val target = instrumentation.targetContext.assets
        val source = IconSource.open(target)
        val template = target.open(PackBuilder.TEMPLATE_ASSET).use { it.readBytes() }
        val drawables = (0 until APPLY_ICONS).map { it * (source.bundle.size / APPLY_ICONS) }
        val plan = CoveragePlan(
            drawables = drawables,
            items = drawables.indices.map { "budget.app$it/budget.app$it.Main" to it },
            unmatched = emptyList(),
        )
        val runs = (0 until RUNS).map {
            var apk = ByteArray(0)
            val ms = timed {
                apk = runBlocking { PackBuilder(source, IconRenderer()).build(template, plan, theme) { _, _ -> } }
            }
            assertEquals(ZipRewriter.entryNames(template).size, ZipRewriter.entryNames(apk).size)
            Log.i(TAG, "apply run: $ms ms, ${apk.size} bytes")
            ms
        }
        Log.i(TAG, "apply $APPLY_ICONS@full $label, ${PackBuilder.RENDER_THREADS} threads, " +
            "${Runtime.getRuntime().availableProcessors()} cpus, ms per run, first is cold: $runs")
        assertTrue("apply runs $runs ms, budget $APPLY_BUDGET_MS ms", runs.drop(1).all { it < APPLY_BUDGET_MS })
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
    }
}
