package dev.iconoclast.render.effect

import dev.iconoclast.render.EffectKind
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Readiness item 4: effects read the icon mask from the cache, never the body alone. */
class EffectSourceTest {

    @Test
    fun effectsReadBodyUnionLinesFromTheCache() {
        val r = IconRenderer()
        val theme = Theme(fill = FillParams(mode = FillMode.INVERT))
        val input = r.stackInput("square", EffectTestIcons.square, "square", theme, 96)
        assertEquals(EffectKind.ICON, input.iconKey!!.kind)
        val icon = EffectSource.icon(input)
        val body = input.masks.body!!
        val lines = input.masks.lines!!
        for (i in icon.indices) assertEquals(body[i] + lines[i] - body[i] * lines[i], icon[i])
        assertTrue((icon.indices).any { lines[it] > 0f && body[it] == 0f }, "the lines are in, which the body alone lacks")
        val misses = r.cache.misses
        assertTrue(EffectSource.icon(r.stackInput("square", EffectTestIcons.square, "square", theme, 96)) === icon, "one cached instance")
        assertEquals(misses, r.cache.misses)
        // Outline mode has no body at all; the effects still see the lines.
        val outline = r.stackInput("square", EffectTestIcons.square, "square", Theme(), 96)
        assertEquals(null, outline.masks.body)
        assertTrue(EffectSource.icon(outline).sum() > 0f)
    }
}
