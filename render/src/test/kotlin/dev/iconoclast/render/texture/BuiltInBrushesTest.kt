package dev.iconoclast.render.texture

import dev.iconoclast.render.Breakup
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Readiness item 5: built-in brushes by hash, always present, and every texture a theme uses is listed. */
class BuiltInBrushesTest {

    /** Generates each brush, encodes it canonically and checks the hash in the source. */
    @Test
    fun brushesMatchTheirHashes() {
        val actual = BuiltInBrushes.ALL.associate { it.name to TextureStore.sha256(BuiltInBrushes.png(it.sha256)!!) }
        assertEquals(BuiltInBrushes.ALL.associate { it.name to it.sha256 }, actual)
        for (b in BuiltInBrushes.ALL) {
            val png = BuiltInBrushes.png(b.sha256)!!
            assertTrue(png.size < 200 * 1024, "${b.name} is ${png.size} bytes")
            assertEquals(b.sha256, TextureStore.canonical(png).hash, "a re-import is the same brush")
            val t = BuiltInBrushes.texture(b.sha256)!!
            // Not flat: a brush with no contrast breaks nothing up.
            val greys = (0 until t.width * t.height).map { t.premultiplied[4 * it].toInt() and 0xFF }
            assertTrue(greys.max() - greys.min() > 150, "${b.name} spans ${greys.min()}..${greys.max()}")
        }
    }

    @Test
    fun brushesAreAlwaysInTheStore() {
        val store = TextureStore(load = { null }, exists = { false })
        for (b in BuiltInBrushes.ALL) {
            assertTrue(b.sha256 in store)
            assertNotNull(store[b.sha256])
            assertContentEquals(BuiltInBrushes.png(b.sha256), store.bytes(b.sha256))
        }
        assertEquals(0L, store.residentBytes, "brushes take no room in the bounded store")
    }

    @Test
    fun textureHashesCoverEffectsAndLinkedLayers() {
        val user = "ab".repeat(32)
        val linked = "cd".repeat(32)
        val theme = Theme().let { t -> t.copy(layers = t.layers + PaintLayer("jade", Paint.Texture(linked), clip = ClipTarget.ICON_FILL)) }
            .withEffect(EffectLayer("s1", Effect.Streaks(breakup = Breakup(texture = BuiltInBrushes.DRY.sha256), paintLayer = "jade")))
            .withEffect(EffectLayer("s2", Effect.Streaks(breakup = Breakup(texture = user)), enabled = false))
            .withEffect(EffectLayer("s3", Effect.Streaks(breakup = Breakup(texture = null))))
        assertEquals(setOf(linked, BuiltInBrushes.DRY.sha256, user), theme.textureHashes())
    }
}
