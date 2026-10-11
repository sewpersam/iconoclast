package dev.iconoclast.app.theme

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.TextureOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The override resolver: one test per override field, then staleness, reset and the file format. */
class EffectiveThemeTest {
    private val jade = GoldenSet.STARTERS.getValue("preset_jade")
    private val doc = ThemeDocument(id = "t", name = "T", created = 1, modified = 2, theme = jade)
    private fun with(o: IconOverride) = doc.withOverride("fdroid", o)

    @Test
    fun noOverrideIsTheDocumentTheme() {
        assertSame(jade, doc.effectiveTheme("fdroid"))
        assertEquals("fdroid", doc.effectiveGeometry("fdroid"))
    }

    @Test
    fun strokeWeight() {
        val t = with(IconOverride(strokeWeight = 3f)).effectiveTheme("fdroid")
        assertEquals(jade.copy(shape = jade.shape.copy(strokeWeight = 3f)), t)
    }

    @Test
    fun fillMode() {
        val t = with(IconOverride(fillMode = FillMode.SILHOUETTE)).effectiveTheme("fdroid")
        assertEquals(jade.copy(fill = jade.fill.copy(mode = FillMode.SILHOUETTE)), t)
    }

    @Test
    fun gapClosing() {
        val t = with(IconOverride(gapClosing = 1.5f)).effectiveTheme("fdroid")
        assertEquals(jade.copy(fill = jade.fill.copy(gapClosing = 1.5f)), t)
    }

    @Test
    fun textureOffset() {
        val t = with(IconOverride(textureOffset = TextureOffset(0.25f, 0.75f))).effectiveTheme("fdroid")
        assertEquals(jade.copy(textureOffset = TextureOffset(0.25f, 0.75f)), t)
    }

    @Test
    fun hideStreaks() {
        assertTrue(jade.layers.any { it is EffectLayer && it.effect is Effect.Streaks })
        val t = with(IconOverride(hideStreaks = true)).effectiveTheme("fdroid")
        assertEquals(jade.copy(layers = jade.layers.filterNot { it is EffectLayer && it.effect is Effect.Streaks }), t)
    }

    @Test
    fun swapDrawable() {
        val d = with(IconOverride(swapDrawable = "camera"))
        assertEquals("camera", d.effectiveGeometry("fdroid"))
        assertEquals(jade, d.effectiveTheme("fdroid"), "a swap changes the geometry only")
        assertEquals("camera", d.effectiveGeometry("camera"), "the swap target keeps its own")
    }

    @Test
    fun overridesApplyOnlyToTheirIcon() {
        val d = with(IconOverride(strokeWeight = 3f, hideStreaks = true, swapDrawable = "camera"))
        assertSame(jade, d.effectiveTheme("signal"))
        assertEquals("signal", d.effectiveGeometry("signal"))
    }

    @Test
    fun resetToThemeRemovesTheOverride() {
        val d = with(IconOverride(strokeWeight = 3f))
        assertEquals(emptyMap(), d.withOverride("fdroid", null).overrides)
        assertEquals(emptyMap(), d.withOverride("fdroid", IconOverride()).overrides)
    }

    @Test
    fun staleOverridesAreKeptAndListed() {
        val d = doc.withOverride("gone_app", IconOverride(strokeWeight = 2f))
            .withOverride("fdroid", IconOverride(swapDrawable = "gone_too"))
            .withOverride("camera", IconOverride(hideStreaks = true))
        val known = setOf("fdroid", "camera")
        assertEquals(listOf("fdroid", "gone_app"), d.staleOverrides { it in known })
        assertEquals(3, d.overrides.size)
        val parsed = migrate(d.toJson())
        assertEquals(d.overrides, parsed.overrides, "kept across save and load")
    }

    @Test
    fun overridesRoundTripThroughTheFile() {
        val d = doc.withOverride("fdroid", IconOverride(strokeWeight = 1.5f, fillMode = FillMode.INVERT, gapClosing = 0f,
            textureOffset = TextureOffset(0.1f, 0.9f), hideStreaks = true, swapDrawable = "camera"))
            .withOverride("camera", IconOverride(fillMode = FillMode.OUTLINE))
        val json = d.toJson()
        assertTrue(json.indexOf("\"camera\"") < json.indexOf("\"fdroid\""), "sorted by name")
        assertTrue("\"swapDrawable\": null" in json, "every field is written")
        val parsed = migrate(json)
        assertEquals(d.overrides, parsed.overrides)
        assertEquals(json, parsed.toJson())
        assertNull(parsed.overrides.getValue("camera").strokeWeight)
        val negativeZero = doc.withOverride("fdroid", IconOverride(gapClosing = -0f)).toJson()
        assertTrue("\"gapClosing\": 0.0" in negativeZero, "-0 is written as 0")
    }

    @Test
    fun outOfRangeOverridesAreDamage() {
        val ok = doc.withOverride("fdroid", IconOverride(strokeWeight = 2f)).toJson()
        for (bad in listOf("\"strokeWeight\": 2.0" to "\"strokeWeight\": 9.0", "\"gapClosing\": null" to "\"gapClosing\": -1.0",
            "\"textureOffset\": null" to "\"textureOffset\": {\"x\": 1.0, \"y\": 0.0}", "\"swapDrawable\": null" to "\"swapDrawable\": \" \"")) {
            assertTrue(bad.first in ok, bad.first)
            assertFailsWith<ThemeFormatException>(bad.second) { migrate(ok.replace(bad.first, bad.second)) }
        }
    }

    @Test
    fun anEffectiveThemeCannotBeSaved() {
        val effective = with(IconOverride(textureOffset = TextureOffset(0.5f, 0.5f))).effectiveTheme("fdroid")
        assertFailsWith<IllegalArgumentException> { doc.copy(theme = effective).toJson() }
    }
}
