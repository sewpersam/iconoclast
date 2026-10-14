package dev.iconoclast.render.verify

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer

/** Times Jade at the 128 px preview while its stroke length changes every pass: the uncached stroke path. */
fun main(args: Array<String>) {
    val bundle = IconBundle(ByteBuffer.wrap(File(args[0]).readBytes()))
    val jade = GoldenSet.STARTERS.getValue("preset_jade")
    val r = IconRenderer()
    val times = ArrayList<Long>()
    for (k in 0 until 60) {
        val t = jade.copy(layers = jade.layers.map { l ->
            if (l is EffectLayer && l.effect is Effect.BrushStrokes) l.copy(effect = (l.effect as Effect.BrushStrokes).copy(length = 0.1f + k * 0.002f)) else l
        })
        val t0 = System.nanoTime()
        GoldenSet.SAMPLES.forEach { n -> r.render(n, bundle.icon(GoldenSet.SAMPLES.indexOf(n)), t, 128) }
        times += (System.nanoTime() - t0) / 1_000_000
    }
    println("stroke edit, 24 icons at 128 px: median ${times.sorted()[30]} ms")
}
