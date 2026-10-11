package dev.iconoclast.render.math

/*
 * The rounding functions every per-pixel path uses, through StrictMath, so the rule is one
 * rule: StrictMath or plain arithmetic, nothing else. sqrt, ceil and floor are exactly
 * specified in java.lang.Math as well, so these give the bits kotlin.math gave; routing
 * them here keeps the platform Math class out of :render entirely. RenderSourceRulesTest
 * enforces it.
 */

internal fun sqrt(x: Float): Float = StrictMath.sqrt(x.toDouble()).toFloat()
internal fun sqrt(x: Double): Double = StrictMath.sqrt(x)
internal fun ceil(x: Float): Float = StrictMath.ceil(x.toDouble()).toFloat()
internal fun ceil(x: Double): Double = StrictMath.ceil(x)
internal fun floor(x: Float): Float = StrictMath.floor(x.toDouble()).toFloat()
internal fun floor(x: Double): Double = StrictMath.floor(x)
