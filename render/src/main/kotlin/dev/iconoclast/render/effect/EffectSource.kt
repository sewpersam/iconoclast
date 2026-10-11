package dev.iconoclast.render.effect

import dev.iconoclast.render.layer.StackInput

/**
 * What every effect starts from: the icon mask, fill body union lines, the ICON clip
 * target, from the mask cache under its EffectKey. Never the fill body alone, which in
 * outline mode is absent and in invert fill leaves out the lines. Values are read-only.
 */
internal object EffectSource {

    /** The icon mask, or all zeros when the icon has neither body nor lines. */
    fun icon(input: StackInput): FloatArray = input.icon()?.values ?: FloatArray(input.size * input.size)
}
