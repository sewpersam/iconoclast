package dev.iconoclast.render.mask

/** Component labels: 0 outside the mask, 1 to [count] inside. */
class Labels(val labels: IntArray, val count: Int)

/**
 * Labels the 4-connected components of [mask] in scan order, by flood fill from the first
 * unlabelled pixel. Four-connectivity keeps background regions from leaking through the
 * diagonal joints of an 8-connected stroke.
 */
fun label4(mask: BooleanArray, w: Int, h: Int): Labels {
    val labels = IntArray(w * h)
    val stack = IntArray(w * h)
    var count = 0
    for (start in labels.indices) {
        if (!mask[start] || labels[start] != 0) continue
        count++
        var top = 0
        stack[top++] = start
        labels[start] = count
        while (top > 0) {
            val p = stack[--top]
            val x = p % w
            val y = p / w
            if (x > 0) top = visit(p - 1, mask, labels, stack, top, count)
            if (x < w - 1) top = visit(p + 1, mask, labels, stack, top, count)
            if (y > 0) top = visit(p - w, mask, labels, stack, top, count)
            if (y < h - 1) top = visit(p + w, mask, labels, stack, top, count)
        }
    }
    return Labels(labels, count)
}

private fun visit(q: Int, mask: BooleanArray, labels: IntArray, stack: IntArray, top: Int, id: Int): Int {
    if (!mask[q] || labels[q] != 0) return top
    labels[q] = id
    stack[top] = q
    return top + 1
}

/** For each label, whether its component touches the grid's edge. Index 0 is unused. */
fun touchesBorder(l: Labels, w: Int, h: Int): BooleanArray {
    val border = BooleanArray(l.count + 1)
    for (x in 0 until w) {
        border[l.labels[x]] = true
        border[l.labels[(h - 1) * w + x]] = true
    }
    for (y in 0 until h) {
        border[l.labels[y * w]] = true
        border[l.labels[y * w + w - 1]] = true
    }
    border[0] = false
    return border
}
