package dev.iconoclast.app.editor

import dev.iconoclast.render.Theme

/**
 * The working theme and its undo and redo stacks, spec 2.2 screen 2. Snapshots are whole
 * [Theme] values, immutable, so a step costs one reference. At most [limit] steps are
 * kept; the oldest drops first.
 *
 * A slider drag is one step: [drag] moves the working theme without recording, and
 * [endDrag] records the theme from before the drag. Not thread-safe; the view model
 * calls it from the main thread.
 */
class EditorHistory(initial: Theme, private val limit: Int = LIMIT) {
    var theme: Theme = initial
        private set

    private val undo = ArrayDeque<Theme>()
    private val redo = ArrayDeque<Theme>()

    /** The theme before the drag in progress, or null when no drag is. */
    private var dragStart: Theme? = null

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val dragging get() = dragStart != null

    /** A committed change: one undo step. Returns whether the theme changed. */
    fun commit(next: Theme): Boolean {
        endDrag()
        if (next == theme) return false
        record(theme)
        theme = next
        return true
    }

    /** A change in the middle of a drag. Recorded as one step by [endDrag]. */
    fun drag(next: Theme) {
        if (dragStart == null) dragStart = theme
        theme = next
    }

    /** Ends the drag in progress. Returns whether it changed the theme, so it is a step to save. */
    fun endDrag(): Boolean {
        val start = dragStart ?: return false
        dragStart = null
        if (start == theme) return false
        record(start)
        return true
    }

    fun undo(): Boolean {
        endDrag()
        val previous = undo.removeLastOrNull() ?: return false
        redo.addLast(theme)
        theme = previous
        return true
    }

    fun redo(): Boolean {
        endDrag()
        val next = redo.removeLastOrNull() ?: return false
        undo.addLast(theme)
        theme = next
        return true
    }

    private fun record(previous: Theme) {
        undo.addLast(previous)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    companion object {
        const val LIMIT = 100
    }
}
