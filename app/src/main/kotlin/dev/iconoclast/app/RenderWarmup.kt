package dev.iconoclast.app

import android.os.SystemClock
import android.util.Log
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb

/**
 * Runs the render path once at app start, off the main thread, so class loading and the
 * JIT are done before the first preview. A synthetic icon on a throwaway renderer: it
 * needs no bundle, so it starts while the bundle is still being read, and it leaves the
 * shared mask cache alone.
 */
object RenderWarmup {
    fun run() {
        val t0 = SystemClock.elapsedRealtime()
        val renderer = IconRenderer()
        for (mode in FillMode.entries) for (i in 0 until ROUNDS) {
            // Each round a new stroke weight, so every pass computes its masks.
            val theme = Theme(shape = Theme().shape.copy(strokeWeight = 1f + i * 0.1f), fill = FillParams(mode = mode))
            renderer.render("warmup", ICON, theme, SIZE)
        }
        Log.i("IconoclastEditor", "render warm-up: ${FillMode.entries.size * ROUNDS} renders in ${SystemClock.elapsedRealtime() - t0} ms")
    }

    private const val SIZE = 128
    private const val ROUNDS = 3

    private val STROKE = Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND)

    /** A closed square, a free dash, and a ring of four cubics: enclosed, lone and curved strokes. */
    private val ICON = Icon(listOf(
        Primitive(STROKE, 1f, 4f, byteArrayOf(Verb.MOVE, Verb.LINE, Verb.LINE, Verb.LINE, Verb.CLOSE),
            floatArrayOf(8f, 8f, 40f, 8f, 40f, 40f, 8f, 40f)),
        Primitive(STROKE, 1f, 4f, byteArrayOf(Verb.MOVE, Verb.LINE), floatArrayOf(4f, 44f, 14f, 44f)),
        Primitive(STROKE, 1f, 4f, byteArrayOf(Verb.MOVE, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CUBIC, Verb.CLOSE),
            floatArrayOf(
                24f, 16f,
                28.4f, 16f, 32f, 19.6f, 32f, 24f,
                32f, 28.4f, 28.4f, 32f, 24f, 32f,
                19.6f, 32f, 16f, 28.4f, 16f, 24f,
                16f, 19.6f, 19.6f, 16f, 24f, 16f,
            )),
    ))
}
