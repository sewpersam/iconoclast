package dev.iconoclast.render.effect

import dev.iconoclast.render.EffectKind
import dev.iconoclast.render.Field
import dev.iconoclast.render.layer.StackInput
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

    /** Signed sub-pixel distance to the icon mask's edge in pixels, positive inside, cached. */
    fun signedDistance(input: StackInput): FloatArray {
        val compute = { Field(SignedDistance.of(icon(input), input.size, input.size)) }
        val key = input.iconKey ?: return compute().values
        return input.cache.field(key.derived(EffectKind.SIGNED_DISTANCE), compute).values
    }
}
