package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.layer.BlendModes
import dev.iconoclast.render.layer.Mappings
import dev.iconoclast.render.layer.Noise
import dev.iconoclast.render.layer.StackEvaluator
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput
import dev.iconoclast.render.math.floor

/**
 * Streaks, spec 2.3: the icon mask (body union lines) turned to the streak's axis and
 * smeared along it (effect/Smear), times the breakup sampled in the same frame, turned
 * back. The paint is the linked layer's source, unclipped, under its own mapping and the
 * icon's offset (StackEvaluator.sourceColors), or the streak's colour.
 *
 * Breakup: a texture is tiled with its short side spanning the canvas / scale across the
 * streak and stretched [dev.iconoclast.render.Breakup.stretch] times along it; without a
 * texture, fBm noise of 4 octaves at 16 / scale cells across, stretched the same. Each
 * icon gets its own patch from its drawable name, as texture layers do. Its luminance t
 * becomes `clamp((t - (1 - density)) gain + 1/2)`, gain `1 / (1 - 0.95 contrast)`.
 */
internal object Streaks {

    fun paint(e: Effect.Streaks, blend: BlendMode, layers: List<Layer>, input: StackInput): EffectOutput {
        val size = input.size
        val frame = Smear.Frame(size, e.angle)
        val side = frame.side
        val smeared = Smear.smear(frame.toFrame(EffectSource.icon(input)), side, e.length * size, e.decay)
        // The exponential tail never reaches 0. Below [FLOOR] it cannot change an 8-bit
        // pixel; dropping it spares the breakup and the linked paint everywhere it would be.
        for (k in smeared.indices) if (smeared[k] < FLOOR) smeared[k] = 0f
        e.breakup?.let { b ->
            val gain = 1f / (1f - 0.95f * b.contrast)
            val shift = 1f - b.density
            val (ox, oy) = Mappings.offset(Mapping(), input.seedName)
            val tex = b.texture?.let { checkNotNull(input.textures[it]) { "texture $it is not in the store" } }
            for (j in 0 until side) for (i in 0 until side) {
                val k = j * side + i
                if (smeared[k] == 0f) continue
                // Canvas units across the frame, centred.
                val u = frame.u(i) / size
                val v = frame.v(j) / size
                val t = if (tex != null) {
                    val per = minOf(tex.width, tex.height) / b.scale
                    texLum(tex, ox * tex.width + u * per / b.stretch, oy * tex.height + v * per)
                } else {
                    val cells = NOISE_CELLS / b.scale
                    Noise.fbm(ox * Mappings.NOISE_PERIOD + u * cells / b.stretch, oy * Mappings.NOISE_PERIOD + v * cells, 4, b.seed)
                }
                smeared[k] *= ((t - shift) * gain + 0.5f).coerceIn(0f, 1f)
            }
        }
        val cov = frame.fromFrame(smeared)
        val source = layers.firstOrNull { it.id == e.paintLayer } as? PaintLayer
        return if (source != null) EffectOutput(cov, null, StackEvaluator.sourceColors(source, input, need = cov), blend)
        else EffectOutput(cov, EffectPaint.solid(e.color), null, blend)
    }

    /** Luminance of the premultiplied texture at texel position (x, y), tiled, bilinear. */
    private fun texLum(tex: dev.iconoclast.render.texture.Texture, x: Float, y: Float): Float {
        val w = tex.width
        val h = tex.height
        val fx = x - 0.5f
        val fy = y - 0.5f
        val x0f = floor(fx)
        val y0f = floor(fy)
        val wx = fx - x0f
        val wy = fy - y0f
        val x0 = ((x0f.toInt() % w) + w) % w
        val y0 = ((y0f.toInt() % h) + h) % h
        val x1 = (x0 + 1) % w
        val y1 = (y0 + 1) % h
        val px = tex.premultiplied
        fun lum(i: Int, j: Int): Float {
            val o = (j * w + i) * 4
            return BlendModes.lum((px[o].toInt() and 0xFF) / 255f, (px[o + 1].toInt() and 0xFF) / 255f, (px[o + 2].toInt() and 0xFF) / 255f)
        }
        val top = lum(x0, y0) + (lum(x1, y0) - lum(x0, y0)) * wx
        val bottom = lum(x0, y1) + (lum(x1, y1) - lum(x0, y1)) * wx
        return top + (bottom - top) * wy
    }

    /** Smear coverage below this is dropped: under half an 8-bit step. */
    private const val FLOOR = 1f / 512

    /** Noise cells across the canvas at breakup scale 1. */
    private const val NOISE_CELLS = 16f
}
