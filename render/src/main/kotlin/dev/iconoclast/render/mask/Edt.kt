package dev.iconoclast.render.mask

/** Stands for "no feature pixel in reach". Squared distances never get near it. */
const val FAR = 1e20f

/**
 * Felzenszwalb and Huttenlocher's exact Euclidean distance transform. Returns, for each
 * pixel of a [w] x [h] grid, the squared distance in pixels to the nearest pixel where
 * [feature] is true, or about [FAR] if there is none. Pixels outside the grid are not
 * features. Squared distances below 2^24 are whole numbers, exact in a Float.
 */
fun squaredEdt(feature: BooleanArray, w: Int, h: Int): FloatArray {
    val d = FloatArray(w * h) { if (feature[it]) 0f else FAR }
    val n = maxOf(w, h)
    val f = FloatArray(n)
    val out = FloatArray(n)
    val v = IntArray(n)
    val z = FloatArray(n + 1)
    for (x in 0 until w) {
        for (y in 0 until h) f[y] = d[y * w + x]
        edt1d(f, h, out, v, z)
        for (y in 0 until h) d[y * w + x] = out[y]
    }
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) f[x] = d[row + x]
        edt1d(f, w, out, v, z)
        for (x in 0 until w) d[row + x] = out[x]
    }
    return d
}

/** Lower envelope of parabolas rooted at (q, f[q]), sampled at 0 until n. */
private fun edt1d(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
    var k = 0
    v[0] = 0
    z[0] = Float.NEGATIVE_INFINITY
    z[1] = Float.POSITIVE_INFINITY
    for (q in 1 until n) {
        // z[0] is minus infinity, so the loop never pops the first parabola.
        var s = intersect(f, q, v[k])
        while (s <= z[k]) {
            k--
            s = intersect(f, q, v[k])
        }
        k++
        v[k] = q
        z[k] = s
        z[k + 1] = Float.POSITIVE_INFINITY
    }
    k = 0
    for (q in 0 until n) {
        while (z[k + 1] < q) k++
        val p = v[k]
        d[q] = (q - p) * (q - p) + f[p]
    }
}

/** Where the parabolas rooted at q and p cross. */
private fun intersect(f: FloatArray, q: Int, p: Int): Float =
    ((f[q] + q * q) - (f[p] + p * p)) / (2 * q - 2 * p)
