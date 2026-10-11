package dev.iconoclast.render

import kotlin.test.Test
import kotlin.test.assertEquals

class IconSeedTest {

    /** The FNV-1a 64 test vectors, and seeds pinned by an independent Python implementation. */
    @Test
    fun seedsAreFixedFunctionsOfTheName() {
        assertEquals(0xcbf29ce484222325uL.toLong(), IconSeed.fnv1a64(ByteArray(0)))
        assertEquals(0xaf63dc4c8601ec8cuL.toLong(), IconSeed.fnv1a64("a".toByteArray()))
        assertEquals(-780787492076525413L, IconSeed.of(""))
        assertEquals(198367012849983736L, IconSeed.of("a"))
        assertEquals(-6733570560301168964L, IconSeed.of("fdroid"))
        assertEquals(697740300554861167L, IconSeed.of("google_chrome"))
        assertEquals(-2251447986271062641L, IconSeed.of("café"), "UTF-8, not UTF-16")
    }
}
