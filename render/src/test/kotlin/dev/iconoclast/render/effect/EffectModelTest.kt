package dev.iconoclast.render.effect

import dev.iconoclast.render.Breakup
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EffectModelTest {

    @Test
    fun effectsGoAboveTheMarkerInTheOrderAdded() {
        val a = EffectLayer("a", Effect.DropShadow())
        val b = EffectLayer("b", Effect.Streaks())
        val t = Theme().withEffect(a).withEffect(b)
        assertEquals(
            listOf(Layer.BACKGROUND_ID, Layer.EFFECTS_ID, "a", "b", Layer.ICON_FILL_ID, Layer.ICON_LINES_ID),
            t.layers.map { it.id },
        )
        assertFailsWith<IllegalArgumentException>("ids stay unique") { t.withEffect(EffectLayer("a", Effect.Grain())) }
    }

    @Test
    fun rangesAreChecked() {
        val bad = listOf<() -> Any>(
            { EffectLayer(" ", Effect.Grain()) },
            { EffectLayer("x", Effect.Grain(), opacity = 1.5f) },
            { Effect.Streaks(angle = 361f) },
            { Effect.Streaks(length = 0.61f) },
            { Effect.Bevel(size = 0f) },
            { Effect.Bevel(depth = 5f) },
            { Effect.Bevel(softness = 2f) },
            { Effect.DropShadow(distance = 0.2f) },
            { Effect.InnerShadow(blur = -0.1f) },
            { Effect.OuterGlow(size = 0.2f) },
            { Effect.InnerGlow(spread = 1.1f) },
            { Effect.Grain(size = 0.5f) },
            { Effect.Grain(amount = 2f) },
            { Breakup(texture = "nothex") },
            { Breakup(stretch = 0.5f) },
            { Breakup(density = -1f) },
        )
        for ((i, make) in bad.withIndex()) assertFailsWith<IllegalArgumentException>("case $i") { make() }
        // The ends of every range are valid.
        Effect.Streaks(angle = 0f, length = 0f); Effect.Streaks(angle = 360f, length = 0.6f)
        Effect.Bevel(size = 0.001f, depth = 0f, softness = 0f); Effect.Bevel(size = 0.05f, depth = 4f, softness = 1f)
        Effect.DropShadow(distance = 0.1f, blur = 0.1f); Effect.OuterGlow(size = 0.1f, spread = 1f)
        Effect.Grain(amount = 0f, size = 1f); Effect.Grain(amount = 1f, size = 4f)
        Breakup(scale = 0.25f, stretch = 1f, density = 0f, contrast = 0f); Breakup(scale = 4f, stretch = 32f, density = 1f, contrast = 1f)
    }
}
