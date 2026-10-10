package dev.iconoclast.render

import dev.iconoclast.render.fill.GlyphMasks

/** Geometry depends on the drawable, the shape parameters and the size, nothing else. */
data class GeometryKey(val drawable: Int, val shape: ShapeParams, val size: Int)

/**
 * The fill stage adds the fill parameters that change its masks: the mode, and the gap
 * and closing where the mode reads them. The fallback threshold and the lone-lines switch
 * only choose between cached masks, so they are not part of the key.
 */
data class FillKey(val drawable: Int, val shape: ShapeParams, val mode: FillMode, val gapWidth: Float, val gapClosing: Float, val size: Int) {
    companion object {
        /** Unused parameters are zeroed, so changing them cannot miss the cache. */
        fun of(drawable: Int, shape: ShapeParams, fill: FillParams, size: Int) = when (fill.mode) {
            FillMode.OUTLINE -> FillKey(drawable, shape, fill.mode, 0f, 0f, size)
            FillMode.SILHOUETTE, FillMode.OUTLINE_SILHOUETTE -> FillKey(drawable, shape, fill.mode, 0f, fill.gapClosing + 0f, size)
            FillMode.INVERT -> FillKey(drawable, shape, fill.mode, fill.gapWidth + 0f, fill.gapClosing + 0f, size)
        }
    }
}

/** Invert fill's lone lines for the fill stage at [fill]. */
data class LoneLinesKey(val fill: FillKey)

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

    /**
     * The fill stage before invert fill's fallback test. Its [GlyphMasks.lines] is always
     * the geometry mask, already counted under its own key, so only the body is counted.
     */
    fun fill(key: FillKey, compute: () -> GlyphMasks): GlyphMasks =
        get(key, compute) { 4L * (it.body?.size ?: 0) }

    fun loneLines(key: LoneLinesKey, compute: () -> FloatArray): FloatArray =
        get(key, compute) { 4L * it.size }

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
