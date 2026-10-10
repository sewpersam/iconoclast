package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.Verb

/**
 * Collects absolute path commands, mapping every point through [transform] as it is added.
 * Affine maps send cubics to cubics, so baking transforms here is exact.
 */
class PathBuilder(private val transform: Affine) {
    private val verbs = ArrayList<Byte>()
    private val coords = ArrayList<Float>()

    val isEmpty: Boolean get() = verbs.isEmpty()

    fun moveTo(x: Double, y: Double) = add(Verb.MOVE, x, y)
    fun lineTo(x: Double, y: Double) = add(Verb.LINE, x, y)

    fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
        verbs += Verb.CUBIC
        point(x1, y1); point(x2, y2); point(x, y)
    }

    fun close() {
        verbs += Verb.CLOSE
    }

    private fun add(verb: Byte, x: Double, y: Double) {
        verbs += verb
        point(x, y)
    }

    private fun point(x: Double, y: Double) {
        coords += transform.x(x, y).toFloat()
        coords += transform.y(x, y).toFloat()
    }

    /** True if some segment draws anything, even a zero-length one. A lone move does not. */
    fun hasSegments(): Boolean = verbs.any { it == Verb.LINE || it == Verb.CUBIC || it == Verb.CLOSE }

    fun verbs(): ByteArray = verbs.toByteArray()
    fun coords(): FloatArray = coords.toFloatArray()
}
