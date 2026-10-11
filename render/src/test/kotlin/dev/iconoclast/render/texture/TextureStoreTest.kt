package dev.iconoclast.render.texture

import dev.iconoclast.render.png.PngEncoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextureStoreTest {

    private fun pixels(w: Int, h: Int) = ByteArray(w * h * 4) { i ->
        val p = i / 4
        if (i % 4 == 3) (128 + p % 128).toByte() else ((p * 7 + i % 4 * 31) % (128 + p % 128)).toByte()
    }

    @Test
    fun storesByContentHash() {
        val px = pixels(5, 3)
        val png = TextureStore.encode(5, 3, px)
        assertContentEquals(png, TextureStore.encode(5, 3, px.copyOf()), "equal pixels, equal bytes")
        val store = TextureStore()
        val hash = store.put(png)
        assertTrue(Regex("[0-9a-f]{64}").matches(hash))
        assertEquals(TextureStore.sha256(png), hash)
        val t = store[hash]!!
        assertEquals(5 to 3, t.width to t.height)
        assertContentEquals(px, t.premultiplied, "premultiplied pixels come back exactly")
        assertEquals(hash, store.put(png))
        store.remove(hash)
        assertNull(store[hash])
        assertFailsWith<IllegalArgumentException> { store.put("0".repeat(64), png) }
        store.put(hash, png)
        assertTrue(hash in store)
    }

    @Test
    fun refusesOversizeAndNonPng() {
        val wide = PngEncoder.encode(2049, 1, ByteArray(2049 * 4))
        assertFailsWith<IllegalArgumentException> { TextureStore().put(wide) }
        assertFailsWith<IllegalArgumentException> { TextureStore.encode(2049, 1, ByteArray(2049 * 4)) }
        TextureStore().put(PngEncoder.encode(2048, 1, ByteArray(2048 * 4)))
        assertFailsWith<IllegalArgumentException> { TextureStore().put(ByteArray(64)) }
    }
}
