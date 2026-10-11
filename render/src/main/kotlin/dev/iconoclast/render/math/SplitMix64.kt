package dev.iconoclast.render.math

import dev.iconoclast.render.IconSeed

/**
 * SplitMix64 (Steele, Lea and Flood, 2014): the seeded generator behind every random
 * choice in :render. Plain 64-bit integer arithmetic, so the sequence for a seed is the
 * same everywhere. Not thread-safe; make one per use.
 */
class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return IconSeed.mix64(state)
    }

    /** Uniform in [0, 1), 24 bits. */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24)

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15
    }
}
