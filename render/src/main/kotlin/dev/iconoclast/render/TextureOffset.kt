package dev.iconoclast.render

import dev.iconoclast.render.math.SplitMix64

/**
 * The per-icon offset, spec 2.3: which patch of every texture and noise field an icon
 * shows. Two numbers in [0, 1). One offset per icon, shared by every layer and effect that
 * reads one (texture and noise layers, the streak breakup, grain), so colour, matte and
 * grain stay registered: the M5 ruling. Layer/Mapping says how each mode moves by it.
 */
data class TextureOffset(val x: Float, val y: Float) {
    init {
        require(x >= 0f && x < 1f && y >= 0f && y < 1f) { "texture offset $x,$y" }
    }

    companion object {
        /** A locked mapping's offset: the centred patch. */
        val CENTRE = TextureOffset(0.5f, 0.5f)

        /**
         * An icon's own offset: the first two outputs of SplitMix64 seeded with
         * [IconSeed.of] its seed name, the slot's drawable name.
         */
        fun of(seedName: String): TextureOffset {
            val r = SplitMix64(IconSeed.of(seedName))
            return TextureOffset(r.nextFloat(), r.nextFloat())
        }
    }
}
