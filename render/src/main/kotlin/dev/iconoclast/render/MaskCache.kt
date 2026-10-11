package dev.iconoclast.render

/**
 * Geometry depends on the drawable, the shape parameters and the size, nothing else. The
 * drawable is its name, so overrides and custom glyphs can take names of their own.
 */
data class GeometryKey(val drawable: String, val shape: ShapeParams, val size: Int)

/**
 * The fill stage adds the fill parameters that change its masks: the mode, and the gap
 * and closing where the mode reads them. The fallback threshold and the lone-lines switch
 * only choose between cached masks, so they are not part of the key.
 */
data class FillKey(val drawable: String, val shape: ShapeParams, val mode: FillMode, val gapWidth: Float, val gapClosing: Float, val size: Int) {
    companion object {
        /** Unused parameters are zeroed, so changing them cannot miss the cache. */
        fun of(drawable: String, shape: ShapeParams, fill: FillParams, size: Int) = when (fill.mode) {
            FillMode.OUTLINE -> FillKey(drawable, shape, fill.mode, 0f, 0f, size)
            FillMode.SILHOUETTE, FillMode.OUTLINE_SILHOUETTE -> FillKey(drawable, shape, fill.mode, 0f, fill.gapClosing + 0f, size)
            FillMode.INVERT -> FillKey(drawable, shape, fill.mode, fill.gapWidth + 0f, fill.gapClosing + 0f, size)
        }
    }
}

/** Invert fill's lone lines for the fill stage at [fill]. */
data class LoneLinesKey(val fill: FillKey)

/**
 * What an [EffectKey] names. A coverage kind caches a [Mask], 0 to 1; a field kind caches
 * a [Field], any finite value. [MaskCache.effect] and [MaskCache.field] check it, so a key
 * can never come back as the other type.
 */
enum class EffectKind(val coverage: Boolean) {
    /** The icon clip target, fill body union lines. */
    ICON(true),

    /** The icon mask, Gaussian blurred; [EffectKey.param] is sigma in pixels. */
    BLUR(true),

    /** Signed sub-pixel distance to the icon mask's edge in pixels, positive inside: mask/SignedDistance. */
    SIGNED_DISTANCE(false),
}

/**
 * Masks and fields derived from the fill stage's output: the icon clip target and what
 * the effects build from it. That output depends on the fill stage, whether invert fill
 * fell back, and the lone-lines switch, so all three are in the key, then the [kind] of
 * derived data and its one parameter, [param], 0 where the kind has none. Built by [of],
 * which clears the two flags where they cannot change the output, then [derived].
 */
data class EffectKey(
    val fill: FillKey,
    val fellBack: Boolean,
    val keepLoneLines: Boolean,
    val kind: EffectKind = EffectKind.ICON,
    val param: Float = 0f,
) {
    /** The same source, another kind of derived data. -0f is stored as 0f so the keys match. */
    fun derived(kind: EffectKind, param: Float = 0f) = copy(kind = kind, param = param + 0f)

    companion object {
        /** The key of the icon mask. */
        fun of(fill: FillKey, fellBack: Boolean, keepLoneLines: Boolean) = when {
            fill.mode != FillMode.INVERT -> EffectKey(fill, false, false)
            fellBack -> EffectKey(fill, true, false)
            else -> EffectKey(fill, false, keepLoneLines)
        }
    }
}

/** Background shape and border coverage. */
data class BackgroundKey(val shape: BackgroundShape, val fraction: Float, val borderWidth: Float, val size: Int)

/**
 * Least-recently-used cache of intermediate [Mask]s, bounded by bytes. Masks are read-only,
 * so one cached instance serves every render. Paint and background
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

    fun geometry(key: GeometryKey, compute: () -> Mask): Mask =
        get(key, compute) { 4L * it.pixels }

    /**
     * The fill stage's body before invert fill's fallback test. Only the body is held; the
     * renderer re-attaches the geometry from its own entry on every read, so an evicted
     * geometry is really gone and the byte count is the memory held.
     */
    fun fill(key: FillKey, compute: () -> Mask): Mask =
        get(key, compute) { 4L * it.pixels }

    fun loneLines(key: LoneLinesKey, compute: () -> Mask): Mask =
        get(key, compute) { 4L * it.pixels }

    /** A coverage mask derived from the fill output. [key]'s kind must be a coverage kind. */
    fun effect(key: EffectKey, compute: () -> Mask): Mask {
        require(key.kind.coverage) { "${key.kind} is a field" }
        return get(key, compute) { 4L * it.pixels }
    }

    /** A field derived from the fill output. [key]'s kind must be a field kind. */
    fun field(key: EffectKey, compute: () -> Field): Field {
        require(!key.kind.coverage) { "${key.kind} is coverage" }
        return get(key, compute) { 4L * it.pixels }
    }

    fun background(key: BackgroundKey, compute: () -> Pair<Mask, Mask?>): Pair<Mask, Mask?> =
        get(key, compute) { 4L * (it.first.pixels + (it.second?.pixels ?: 0)) }

    /** Bytes held now, never more than the bound passed in. */
    val bytesHeld: Long get() = synchronized(this) { bytes }

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
