package dev.iconoclast.render.effect

import dev.iconoclast.render.EffectKind
import dev.iconoclast.render.Field
import dev.iconoclast.render.Mask
import dev.iconoclast.render.layer.StackInput
import dev.iconoclast.render.mask.Blur
import dev.iconoclast.render.mask.SignedDistance

/**
 * What every effect starts from: the icon mask, fill body union lines, the ICON clip
 * target, from the mask cache under its EffectKey, and what is derived from it under the
 * same key with a kind. Never the fill body alone, which in outline mode is absent and in
 * invert fill leaves out the lines. Values are read-only.
 */
internal object EffectSource {

    /** The icon mask, or all zeros when the icon has neither body nor lines. */
    fun icon(input: StackInput): FloatArray = input.icon()?.values ?: FloatArray(input.size * input.size)

    /**
     * Signed sub-pixel distance to the icon mask's edge in pixels, positive inside, cached
     * by [band]: within [band] px of the edge; beyond, plus or minus [band].
     */
    fun signedDistance(input: StackInput, band: Float = Float.MAX_VALUE): FloatArray {
        val compute = { Field(SignedDistance.of(icon(input), input.size, input.size, band)) }
        val key = input.iconKey ?: return compute().values
        return input.cache.field(key.derived(EffectKind.SIGNED_DISTANCE, band), compute).values
    }

    /**
     * The icon mask blurred with standard deviation [sigma] pixels, zero outside the
     * canvas, cached by sigma: shadows and glows of one blur share it, and a colour or
     * offset change reuses it.
     */
    fun blurred(input: StackInput, sigma: Float): FloatArray {
        val compute = { Mask(Blur.gaussian(icon(input), input.size, input.size, sigma)) }
        val key = input.iconKey ?: return compute().values
        return input.cache.effect(key.derived(EffectKind.BLUR, sigma), compute).values
    }
}
