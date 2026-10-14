package dev.iconoclast.render.layer

import dev.iconoclast.render.Mapping
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.texture.TextureStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MappingTest {

    /** w x h opaque texture, each texel's red its index mod 256. */
    private fun texture(w: Int, h: Int) = TextureStore().let { s ->
        val px = ByteArray(w * h * 4) { if (it % 4 == 0) (it / 4 % 256).toByte() else if (it % 4 == 3) -1 else 0 }
        s[s.put(TextureStore.encode(w, h, px))]!!
    }

    /** The offset is a fixed function of the name: SplitMix64 over IconSeed, pinned in Python. */
    @Test
    fun offsetIsSeededByTheDrawableName() {
        assertEquals(TextureOffset(0.638075053691864f, 0.902005672454834f), Mappings.offset(Mapping(), TextureOffset.of("fdroid")))
        assertEquals(Mappings.offset(Mapping(), TextureOffset.of("fdroid")), Mappings.offset(Mapping(), TextureOffset.of("fdroid")))
        assertFalse(Mappings.offset(Mapping(), TextureOffset.of("fdroid")) == Mappings.offset(Mapping(), TextureOffset.of("camera")))
        assertEquals(TextureOffset.CENTRE, Mappings.offset(Mapping(randomShift = 0f), TextureOffset.of("fdroid")))
    }

    @Test
    fun fillAtScaleOneLockedIsTheTextureItself() {
        val t = texture(8, 8)
        val out = Mappings.texture(t, Mapping(randomShift = 0f), TextureOffset.of("x"), 8)
        for (i in 0 until 64) assertEquals(i / 255f, out[i * 4], "texel $i")
    }

    @Test
    fun tilesRepeat() {
        val t = texture(4, 4)
        val out = Mappings.texture(t, Mapping(mode = MappingMode.TILE, scale = 0.5f, randomShift = 0f), TextureOffset.of("x"), 8)
        for (y in 0 until 8) for (x in 0 until 4) assertEquals(out[(y * 8 + x) * 4], out[(y * 8 + x + 4) * 4])
        for (y in 0 until 4) assertEquals(out[(y * 8) * 4], out[((y + 4) * 8) * 4])
    }

    @Test
    fun fitShowsAllOfAWideTextureAndLeavesTheRestClear() {
        val out = Mappings.texture(texture(16, 8), Mapping(mode = MappingMode.FIT, randomShift = 0f), TextureOffset.of("x"), 16)
        assertEquals(0f, out[3], "top row is outside the texture")
        assertEquals(1f, out[(8 * 16 + 8) * 4 + 3], "centre is inside")
    }

    /** Ruling 5: FIT moves within its room. A smaller texture stays whole; a zoomed one differs per icon. */
    @Test
    fun fitMovesWithinTheRoom() {
        val wide = texture(16, 8)
        for (name in listOf("fdroid", "camera", "signal")) {
            val out = Mappings.texture(wide, Mapping(mode = MappingMode.FIT), TextureOffset.of(name), 16)
            assertEquals(16 * 8, (0 until 256).count { out[it * 4 + 3] == 1f }, "$name: the whole texture shows")
        }
        val a = Mappings.texture(wide, Mapping(mode = MappingMode.FIT), TextureOffset.of("fdroid"), 16)
        assertFalse(a.contentEquals(Mappings.texture(wide, Mapping(mode = MappingMode.FIT), TextureOffset.of("camera"), 16)))
        val sq = texture(32, 32)
        val z = Mapping(mode = MappingMode.FIT, scale = 2f)
        assertFalse(Mappings.texture(sq, z, TextureOffset.of("fdroid"), 8).contentEquals(Mappings.texture(sq, z, TextureOffset.of("camera"), 8)))
    }

    /** A square texture at scale 1 leaves FILL no room: one patch for all. Scale 2 gives each icon its own. */
    @Test
    fun fillNeedsRoomToMove() {
        val sq = texture(32, 32)
        assertContentEquals(Mappings.texture(sq, Mapping(), TextureOffset.of("fdroid"), 8), Mappings.texture(sq, Mapping(), TextureOffset.of("camera"), 8))
        val m = Mapping(scale = 2f)
        assertFalse(Mappings.texture(sq, m, TextureOffset.of("fdroid"), 8).contentEquals(Mappings.texture(sq, m, TextureOffset.of("camera"), 8)))
    }

    /** Every icon gets its own patch, and the same patch every time. */
    @Test
    fun patchesDifferByNameAndRepeatByName() {
        val t = texture(32, 32)
        val m = Mapping(scale = 2f)
        val a = Mappings.texture(t, m, TextureOffset.of("fdroid"), 8)
        assertContentEquals(a, Mappings.texture(t, m, TextureOffset.of("fdroid"), 8))
        assertFalse(a.contentEquals(Mappings.texture(t, m, TextureOffset.of("camera"), 8)))
        assertContentEquals(Mappings.texture(t, m.copy(randomShift = 0f), TextureOffset.of("fdroid"), 8), Mappings.texture(t, m.copy(randomShift = 0f), TextureOffset.of("camera"), 8))
        val n = Mappings.noise(NoiseKind.FBM, 4, 1, Mapping(), TextureOffset.of("fdroid"), 16, 432)
        assertContentEquals(n, Mappings.noise(NoiseKind.FBM, 4, 1, Mapping(), TextureOffset.of("fdroid"), 16, 432))
        assertFalse(n.contentEquals(Mappings.noise(NoiseKind.FBM, 4, 1, Mapping(), TextureOffset.of("camera"), 16, 432)))
        assertTrue(n.all { it in 0f..1f })
    }
}
