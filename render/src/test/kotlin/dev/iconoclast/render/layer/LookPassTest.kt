package dev.iconoclast.render.layer

import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Crop
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.IconSeed
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Paint
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.background.Paints
import dev.iconoclast.render.texture.Texture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Schema 8's placement, crop, angular gradient and blur. */
class LookPassTest {

    private fun place(m: Mapping, name: String, pinned: TextureOffset? = null) =
        Mappings.place(m, pinned ?: TextureOffset.of(name), IconSeed.of(name), pinned != null)

    @Test
    fun seedZeroFullShiftIsTheIconsOwnOffset() {
        val p = place(Mapping(), "fdroid")
        assertEquals(TextureOffset.of("fdroid"), p.offset)
        assertEquals(1f, p.scale)
        assertEquals(0f, p.rotation)
        assertEquals(TextureOffset.CENTRE, place(Mapping(randomShift = 0f), "fdroid").offset)
    }

    @Test
    fun randomTurnAndZoomDifferPerIconAndRepeat() {
        val m = Mapping(rotation = 10f, randomRotation = 90f, randomScale = 1f)
        val a = place(m, "fdroid")
        assertEquals(a, place(m, "fdroid"))
        val b = place(m, "camera")
        assertTrue(a.rotation != b.rotation && a.scale != b.scale)
        assertTrue(a.rotation in -80f..100f && a.scale in 0.5f..2f)
    }

    @Test
    fun shuffleChangesTheArrangementButNotAPinnedOffset() {
        val m = Mapping(randomRotation = 45f)
        assertFalse(place(m, "fdroid") == place(m.copy(seed = 3), "fdroid"))
        assertFalse(place(m, "fdroid").offset == place(m.copy(seed = 3), "fdroid").offset)
        val pin = TextureOffset(0.25f, 0.75f)
        assertEquals(pin, place(m.copy(seed = 3), "fdroid", pin).offset)
        assertEquals(TextureOffset(0.375f, 0.625f), place(m.copy(randomShift = 0.5f), "fdroid", pin).offset)
    }

    private fun texture(w: Int, h: Int): Texture {
        val px = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val o = (y * w + x) * 4
            px[o] = (x * 255 / (w - 1)).toByte(); px[o + 1] = (y * 255 / (h - 1)).toByte(); px[o + 3] = -1
        }
        return Texture(w, h, px)
    }

    @Test
    fun fullCropIsNoCrop() {
        val t = texture(32, 16)
        for (mode in MappingMode.entries) {
            val m = Mapping(mode, scale = 1.5f, rotation = 20f)
            assertContentEquals(
                Mappings.texture(t, m, TextureOffset.of("x"), 12),
                Mappings.texture(t, m.copy(crop = Crop(0f, 0f, 1f, 1f)), TextureOffset.of("x"), 12),
            )
        }
    }

    @Test
    fun cropShowsOnlyItsRectangle() {
        val t = texture(32, 32)
        // The right half, fitted: where it shows, red runs from about half to full.
        val out = Mappings.texture(t, Mapping(MappingMode.FIT, randomShift = 0f, crop = Crop(0.5f, 0f, 1f, 1f)), TextureOffset.CENTRE, 16)
        val red = (0 until 16).filter { out[(8 * 16 + it) * 4 + 3] > 0f }.map { out[(8 * 16 + it) * 4] }
        assertTrue(red.size == 8 && red.first() > 0.45f && red.last() > 0.95f, "$red")
        // Tiling a crop repeats the crop, never the rest of the image.
        val tiled = Mappings.texture(t, Mapping(MappingMode.TILE, scale = 0.25f, crop = Crop(0.5f, 0f, 1f, 1f)), TextureOffset.of("y"), 16)
        assertTrue((0 until 256).all { tiled[it * 4] >= 0.45f })
    }

    @Test
    fun angularRunsClockwiseFromItsAngle() {
        val stops = listOf(ColorStop(0f, 0xFF000000.toInt()), ColorStop(1f, 0xFFFFFFFF.toInt()))
        val size = 64
        val px = Paints.fill(Paint.AngularGradient(0.5f, 0.5f, 0f, stops), size)
        fun at(x: Int, y: Int) = px[(y * size + x) * 4]
        // Right of centre just below the start: dark; a quarter round, below: a quarter grey.
        assertTrue(at(63, 33) < 0.05f)
        assertEquals(0.25f, at(32, 63), 0.03f)
        assertEquals(0.5f, at(0, 32), 0.03f)
        assertEquals(0.75f, at(32, 0), 0.03f)
    }

    @Test
    fun blurKeepsAFlatTileFlatAndSoftensAnEdge() {
        val size = 32
        fun run(fill: (Int) -> Float): FloatArray {
            val buf = FloatArray(size * size * 4) { i -> if (i % 4 == 3) 1f else fill(i / 4) }
            val input = StackInput(TextureOffset.of("x"), size, 432, dev.iconoclast.render.fill.GlyphMasks(null, null),
                icon = { null }, shape = null, border = null, borderColor = null, textures = dev.iconoclast.render.texture.TextureStore())
            StackEvaluator.paint(buf, listOf(EffectLayer("b", Effect.Blur(0.1f, ClipTarget.CANVAS))), input)
            return buf
        }
        val flat = run { 0.6f }
        assertTrue(flat.indices.all { if (it % 4 == 3) flat[it] == 1f else kotlin.math.abs(flat[it] - 0.6f) < 1e-4f }, "rim kept")
        val step = run { if (it % size < size / 2) 0f else 1f }
        val row = 16 * size
        assertTrue(step[(row + 14) * 4] > 0.05f && step[(row + 17) * 4] < 0.95f)
    }
}
