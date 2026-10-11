package dev.iconoclast.render.texture

import dev.iconoclast.render.png.PngEncoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    /** Hashes are of canonical bytes: another encoder's PNG of the same pixels gets the same hash. */
    @Test
    fun sameTexelsFromAnotherEncoderGetOneHash() {
        val w = 6
        val h = 4
        val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val px = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val g = (x * 40 + y * 9) and 0xFF
            img.setRGB(x, y, (0xFF shl 24) or (g shl 16) or (g shl 8) or g)
            val o = (y * w + x) * 4
            px[o] = g.toByte(); px[o + 1] = g.toByte(); px[o + 2] = g.toByte(); px[o + 3] = -1
        }
        val other = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "png", it) }.toByteArray()
        val ours = TextureStore.encode(w, h, px)
        assertFalse(other.contentEquals(ours), "the two files differ")
        val store = TextureStore()
        val hash = store.put(other)
        assertEquals(hash, store.put(ours))
        assertEquals(TextureStore.sha256(ours), hash)
        assertContentEquals(ours, store.bytes(hash))
        store.put(hash, other)
    }

    /** A 20000 x 20000 header is refused from the IHDR, before any allocation. */
    @Test
    fun hugeHeaderIsRefusedNotAllocated() {
        fun header(side: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val body = java.nio.ByteBuffer.allocate(17).put("IHDR".toByteArray()).putInt(side).putInt(side)
                .put(8).put(6).put(0).put(0).put(0).array()
            out.write(java.nio.ByteBuffer.allocate(4).putInt(13).array())
            out.write(body)
            out.write(java.nio.ByteBuffer.allocate(4).putInt(java.util.zip.CRC32().apply { update(body) }.value.toInt()).array())
            return out.toByteArray()
        }
        assertFailsWith<IllegalArgumentException> { TextureStore().put(header(20000)) }
        assertFailsWith<IllegalArgumentException> { dev.iconoclast.render.png.PngDecoder.decode(header(20000)) }
        assertFailsWith<IllegalArgumentException> { dev.iconoclast.render.png.PngDecoder.decode(header(40000), maxSide = Int.MAX_VALUE) }
    }

    @Test
    fun refusesOversizeAndNonPng() {
        val wide = PngEncoder.encode(2049, 1, ByteArray(2049 * 4))
        assertFailsWith<IllegalArgumentException> { TextureStore().put(wide) }
        assertFailsWith<IllegalArgumentException> { TextureStore.encode(2049, 1, ByteArray(2049 * 4)) }
        TextureStore().put(PngEncoder.encode(2048, 1, ByteArray(2048 * 4)))
        assertFailsWith<IllegalArgumentException> { TextureStore().put(ByteArray(64)) }
    }

    /** Least recently used out past the byte cap; an evicted texture loads back through the loader. */
    @Test
    fun evictsPastTheCapAndReloadsThroughTheLoader() {
        val files = HashMap<String, ByteArray>()
        val pngs = (1..3).map { TextureStore.encode(16, 16, pixels(16, 16).also { p -> p[0] = it.toByte() }) }
        val one = 16 * 16 * 4L + pngs.maxOf { it.size }
        val store = TextureStore(maxBytes = 2 * one, load = { files[it] }, exists = { it in files })
        val hashes = pngs.map { png -> store.put(png).also { files[it] = store.bytes(it)!! } }
        assertTrue(store.residentBytes <= 2 * one)
        assertFalse(store.isResident(hashes[0]), "the eldest is evicted")
        assertTrue(store.isResident(hashes[1]) && store.isResident(hashes[2]))
        assertTrue(hashes[0] in store, "on disk counts as present")
        val back = store[hashes[0]]!!
        assertContentEquals(TextureStore().let { it[it.put(pngs[0])]!!.premultiplied }, back.premultiplied)
        assertTrue(store.isResident(hashes[0]))
        assertFalse(store.isResident(hashes[1]), "reloading evicts the next eldest")
        assertContentEquals(files[hashes[1]], store.bytes(hashes[1]))
    }

    /** A file that no longer matches its hash, or is gone, reads as absent; nothing throws. */
    @Test
    fun damagedOrMissingFilesReadAsAbsent() {
        val png = TextureStore.encode(4, 4, pixels(4, 4))
        val hash = TextureStore.canonical(png).hash
        val other = TextureStore.encode(4, 4, pixels(4, 4).also { it[0] = 99 })
        assertNull(TextureStore(load = { other })[hash])
        assertNull(TextureStore(load = { byteArrayOf(1, 2, 3) })[hash])
        assertNull(TextureStore()[hash])
        assertFalse(hash in TextureStore())
        assertEquals(hash, TextureStore().put(png))
    }
}
