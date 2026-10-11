package dev.iconoclast.render.math

/*
 * The rounding functions every per-pixel path uses, so the rule is one rule: StrictMath or
 * plain arithmetic, nothing else. sqrt goes through StrictMath; float floor and ceil are
 * plain arithmetic with the same bits (DeterminismRulesTest compares them). They give the
 * bits kotlin.math gave; routing them here keeps the platform Math class out of :render
 * entirely. DeterminismRulesTest enforces it.
 */

internal fun sqrt(x: Float): Float = StrictMath.sqrt(x.toDouble()).toFloat()
internal fun sqrt(x: Double): Double = StrictMath.sqrt(x)
/**
 * floor by plain arithmetic: truncation, then one step down for negatives with a
 * fraction. Exact for every float below 2^31 in magnitude, which is all the renderer
 * passes; StrictMath beyond, and zero keeps its sign. On ART StrictMath.floor is a native
 * call that costs more than the arithmetic around it; the bits are the same.
 */
internal fun floor(x: Float): Float {
    if (!(x > -2.1e9f && x < 2.1e9f) || x == 0f) return StrictMath.floor(x.toDouble()).toFloat()
    val f = x.toInt().toFloat()
    return if (f > x) f - 1f else f
}

internal fun ceil(x: Float): Float {
    if (!(x > -2.1e9f && x < 2.1e9f) || x == 0f) return StrictMath.ceil(x.toDouble()).toFloat()
    val f = x.toInt().toFloat()
    if (f < x) return f + 1f
    // A negative fraction rounds up to minus zero.
    return if (f == 0f && x < 0f) -0f else f
}

internal fun ceil(x: Double): Double = StrictMath.ceil(x)
internal fun floor(x: Double): Double = StrictMath.floor(x)
