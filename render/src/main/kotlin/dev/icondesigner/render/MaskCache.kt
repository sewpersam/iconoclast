package dev.icondesigner.render

import dev.icondesigner.render.fill.GlyphMasks

/** Geometry depends on the drawable, the shape parameters and the size, nothing else. */
data class GeometryKey(val drawable: Int, val shape: ShapeParams, val size: Int)

/** The fill stage adds the fill parameters. */
data class FillKey(val drawable: Int, val shape: ShapeParams, val fill: FillParams, val size: Int)

/** Background shape and border coverage. */
data class BackgroundKey(val shape: BackgroundShape, val fraction: Float, val borderWidth: Float, val size: Int)

/**
 * Least-recently-used cache of intermediate masks, bounded by bytes. Paint and background
 * changes reuse the cached geometry and fill masks, so moving a colour or texture slider
 * never recomputes geometry. Thread-safe. Two threads missing the same key may both
 * compute it; the results are identical, so either may stay.
 */
class MaskCache(private val maxBytes: Long = 64L shl 20) {
    private class Entry(val value: Any, val bytes: Long)

    private val map = LinkedHashMap<Any, Entry>(64, 0.75f, true)
    private var bytes = 0L

    /** Lookups that found a value, and lookups that computed one. */
    var hits = 0L; private set
    var misses = 0L; private set

    fun geometry(key: GeometryKey, compute: () -> FloatArray): FloatArray =
        get(key, compute) { 4L * it.size }

    fun fill(key: FillKey, compute: () -> GlyphMasks): GlyphMasks =
        get(key, compute) { 4L * ((it.body?.size ?: 0) + (it.lines?.size ?: 0)) }

    fun background(key: BackgroundKey, compute: () -> Pair<FloatArray, FloatArray?>): Pair<FloatArray, FloatArray?> =
        get(key, compute) { 4L * (it.first.size + (it.second?.size ?: 0)) }

    fun clear() = synchronized(this) {
        map.clear()
        bytes = 0
    }

    private fun <T : Any> get(key: Any, compute: () -> T, size: (T) -> Long): T {
        synchronized(this) {
            map[key]?.let {
                hits++
                @Suppress("UNCHECKED_CAST")
                return it.value as T
            }
            misses++
        }
        val value = compute()
        val n = size(value)
        synchronized(this) {
            map.put(key, Entry(value, n))?.let { bytes -= it.bytes }
            bytes += n
            val it = map.entries.iterator()
            while (bytes > maxBytes && it.hasNext()) {
                val e = it.next()
                if (e.key == key) continue
                bytes -= e.value.bytes
                it.remove()
            }
        }
        return value
    }
}
