package dev.icondesigner.render.format

/**
 * Path verbs. Coordinates are absolute, in the 48-unit icon canvas.
 * MOVE and LINE take one point, CUBIC takes three, CLOSE takes none.
 */
object Verb {
    const val MOVE: Byte = 0
    const val LINE: Byte = 1
    const val CUBIC: Byte = 2
    const val CLOSE: Byte = 3

    fun pointCount(verb: Byte): Int = when (verb) {
        MOVE, LINE -> 1
        CUBIC -> 3
        CLOSE -> 0
        else -> throw IllegalArgumentException("unknown verb $verb")
    }
}

/**
 * One drawable shape of an icon. It is stroked, filled, or both.
 * [coords] holds x,y pairs for the points the [verbs] consume, in order.
 */
class Primitive(
    val flags: Int,
    val strokeWidth: Float,
    val miterLimit: Float,
    val verbs: ByteArray,
    val coords: FloatArray,
) {
    val isStroked: Boolean get() = flags and STROKE != 0
    val isFilled: Boolean get() = flags and FILL != 0
    val isEvenOdd: Boolean get() = flags and EVEN_ODD != 0
    val cap: Int get() = (flags shr CAP_SHIFT) and 3
    val join: Int get() = (flags shr JOIN_SHIFT) and 3

    companion object {
        const val STROKE = 1
        const val FILL = 2
        /** The source fill rule is evenodd. Set whether or not the primitive is filled. */
        const val EVEN_ODD = 4
        const val CAP_SHIFT = 3
        const val JOIN_SHIFT = 5

        const val CAP_BUTT = 0
        const val CAP_ROUND = 1
        const val CAP_SQUARE = 2

        const val JOIN_MITER = 0
        const val JOIN_ROUND = 1
        const val JOIN_BEVEL = 2

        fun flags(stroke: Boolean, fill: Boolean, evenOdd: Boolean, cap: Int, join: Int): Int =
            (if (stroke) STROKE else 0) or (if (fill) FILL else 0) or (if (evenOdd) EVEN_ODD else 0) or
                (cap shl CAP_SHIFT) or (join shl JOIN_SHIFT)
    }
}

class Icon(val primitives: List<Primitive>)
