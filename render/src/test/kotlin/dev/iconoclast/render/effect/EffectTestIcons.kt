package dev.iconoclast.render.effect

import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb

/** Small icons for effect tests. */
internal object EffectTestIcons {
    /** A closed square, 12 to 36 on the 48 grid. */
    val square = Icon(listOf(
        Primitive(Primitive.flags(true, false, false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND), 1f, 4f,
            byteArrayOf(Verb.MOVE, Verb.LINE, Verb.LINE, Verb.LINE, Verb.CLOSE),
            floatArrayOf(12f, 12f, 36f, 12f, 36f, 36f, 12f, 36f)),
    ))
}
