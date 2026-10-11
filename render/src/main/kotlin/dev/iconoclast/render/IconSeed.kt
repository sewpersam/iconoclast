package dev.iconoclast.render

/**
 * The 64-bit seed of a drawable, from its name. Every per-icon random choice derives from
 * it, so an icon looks the same on every device and in every release that keeps its name.
 *
 * seed = mix64(fnv1a64(utf8(name))). FNV-1a 64 is the Fowler, Noll and Vo hash with offset
 * basis 0xcbf29ce484222325 and prime 0x100000001b3; mix64 is the SplitMix64 output
 * function (Steele, Lea and Flood, "Fast splittable pseudorandom number generators", 2014),
 * which spreads FNV's weak low bits over the whole word. Not String.hashCode, which is 32
 * bits and a JVM detail.
 */
object IconSeed {
    fun of(name: String): Long = mix64(fnv1a64(name.toByteArray(Charsets.UTF_8)))

    fun fnv1a64(bytes: ByteArray): Long {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (b in bytes) {
            h = h xor (b.toLong() and 0xFF)
            h *= 0x100000001b3L
        }
        return h
    }

    /** SplitMix64's finaliser, a bijection on 64 bits. */
    fun mix64(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
        return z xor (z ushr 31)
    }
}
