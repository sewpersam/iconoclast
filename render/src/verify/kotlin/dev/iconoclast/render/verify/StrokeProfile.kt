package dev.iconoclast.render.verify

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.StrokeStyle
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

/**
 * Times brush strokes at the 128 px preview while the stroke length changes every pass: the
 * uncached stroke path. Jade's Classic strokes, the same strokes Painted, and the Painted swipes.
 */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    fun strokes(t: Theme, f: (Effect.BrushStrokes) -> Effect.BrushStrokes) = t.copy(layers = t.layers.map { l ->
        if (l is EffectLayer && l.effect is Effect.BrushStrokes) l.copy(effect = f(l.effect as Effect.BrushStrokes)) else l
    })
    val jade = GoldenSet.STARTERS.getValue("preset_jade")
    val looks = listOf(
        "Jade, Classic" to jade,
        "Jade, Painted" to strokes(jade) { it.copy(style = StrokeStyle.PAINTED) },
        "Swipes, Painted" to GoldenSet.FEEDBACK.getValue("fb_strokes_parallel"),
    )
    for ((title, look) in looks) {
        val r = IconRenderer()
        val base = (look.layers.first { it is EffectLayer && it.effect is Effect.BrushStrokes } as EffectLayer).effect as Effect.BrushStrokes
        val times = ArrayList<Long>()
        for (k in 0 until 60) {
            val t = strokes(look) { it.copy(length = (base.length - 0.06f + k * 0.002f).coerceIn(0.02f, 1.2f)) }
            val t0 = System.nanoTime()
            GoldenSet.SAMPLES.forEach { n -> r.render(n, bundle.icon(GoldenSet.SAMPLES.indexOf(n)), t, 128) }
            times += (System.nanoTime() - t0) / 1_000_000
        }
        println("stroke edit, $title, 24 icons at 128 px: median ${times.sorted()[30]} ms")
    }
}
