package dev.iconoclast.render

/**
 * Coverage, 0 to 1 per pixel, row-major. Read-only: the mask cache hands one instance to
 * every render that asks for it, so a write would corrupt every later render. Code outside
 * :render sees only [get] and copies; code inside reads [values] and never writes it.
 * Whoever builds a mask gives up its array.
 */
class Mask internal constructor(internal val values: FloatArray) {
    /** Number of pixels. */
    val pixels: Int get() = values.size

    operator fun get(i: Int): Float = values[i]

    fun sum(): Float = values.sum()

    /** A fresh copy the caller may write. */
    fun toFloatArray(): FloatArray = values.copyOf()

    fun contentEquals(other: FloatArray): Boolean = values.contentEquals(other)
}
