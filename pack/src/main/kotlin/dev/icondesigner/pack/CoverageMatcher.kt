package dev.icondesigner.pack

/**
 * Which installed apps the pack themes, spec 2.5. Each matched drawable is rendered once
 * into one slot; every component that maps to it points at that slot, spec 6.3.
 *
 * @property drawables the drawable index rendered into each slot, slot 0 first.
 * @property items each matched component and its slot, sorted by component.
 * @property unmatched components with no Arcticons drawable; they keep their system icon.
 */
class CoveragePlan(
    val drawables: List<Int>,
    val items: List<Pair<String, Int>>,
    val unmatched: List<String>,
)

class TooManyIconsException(val needed: Int, val available: Int) :
    IllegalStateException("$needed icons are needed but the pack has $available slots")

object CoverageMatcher {
    /**
     * [components] are `package/class` strings of launchable activities. [resolve] gives a
     * component's drawable index, or -1. The plan depends only on the set of components.
     */
    fun match(components: Collection<String>, slotCount: Int, resolve: (String) -> Int): CoveragePlan {
        val matched = ArrayList<Pair<String, Int>>()
        val unmatched = ArrayList<String>()
        for (c in components.toSortedSet()) {
            val drawable = resolve(c)
            if (drawable >= 0) matched += c to drawable else unmatched += c
        }
        val drawables = matched.map { it.second }.distinct().sorted()
        if (drawables.size > slotCount) throw TooManyIconsException(drawables.size, slotCount)
        val slotOf = drawables.withIndex().associate { (slot, d) -> d to slot }
        return CoveragePlan(drawables, matched.map { (c, d) -> c to slotOf.getValue(d) }, unmatched)
    }
}
