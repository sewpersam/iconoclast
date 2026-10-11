package dev.iconoclast.app.editor

import dev.iconoclast.render.LineCap
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorHistoryTest {
    private fun weight(w: Float) = Theme(shape = ShapeParams(strokeWeight = w))

    @Test
    fun undoAndRedoWalkTheCommits() {
        val h = EditorHistory(weight(1f))
        assertTrue(h.commit(weight(2f)))
        assertTrue(h.commit(weight(3f)))
        assertTrue(h.undo())
        assertEquals(weight(2f), h.theme)
        assertTrue(h.undo())
        assertEquals(weight(1f), h.theme)
        assertFalse(h.undo())
        assertTrue(h.redo())
        assertEquals(weight(2f), h.theme)
        // A new commit drops the redo branch.
        h.commit(Theme(shape = ShapeParams(strokeWeight = 2f, cap = LineCap.BUTT)))
        assertFalse(h.canRedo)
    }

    @Test
    fun aDragIsOneStep() {
        val h = EditorHistory(weight(1f))
        for (w in listOf(1.1f, 1.5f, 2f, 2.5f)) h.drag(weight(w))
        assertTrue(h.dragging)
        assertFalse(h.canUndo, "nothing is recorded mid-drag")
        assertTrue(h.endDrag())
        assertFalse(h.dragging)
        assertEquals(weight(2.5f), h.theme)
        assertTrue(h.undo())
        assertEquals(weight(1f), h.theme)
        assertFalse(h.canUndo)
    }

    @Test
    fun aDragBackToTheStartIsNoStep() {
        val h = EditorHistory(weight(1f))
        h.drag(weight(2f))
        h.drag(weight(1f))
        assertFalse(h.endDrag())
        assertFalse(h.canUndo)
        assertFalse(h.commit(weight(1f)), "an equal theme is no change")
    }

    @Test
    fun undoMidDragClosesTheDragFirst() {
        val h = EditorHistory(weight(1f))
        h.drag(weight(3f))
        assertTrue(h.undo())
        assertEquals(weight(1f), h.theme)
        assertTrue(h.redo())
        assertEquals(weight(3f), h.theme)
    }

    @Test
    fun historyIsBoundedAtTheLimit() {
        val h = EditorHistory(weight(0.25f))
        assertEquals(100, EditorHistory.LIMIT)
        repeat(150) { h.commit(weight(0.25f + (it + 1) * 0.02f)) }
        var steps = 0
        while (h.undo()) steps++
        assertEquals(100, steps)
        // The oldest 50 dropped: undo stops at the 50th commit.
        assertEquals(weight(0.25f + 50 * 0.02f), h.theme)
    }
}

class EditorHistoryMarkTest {
    private fun weight(w: Float) = Theme(shape = ShapeParams(strokeWeight = w))

    /** Item 4: the removal's Undo reverts the removal, never a later edit. */
    @Test
    fun undoIfAtActsOnlyFromTheMark() {
        val h = EditorHistory(weight(1f))
        h.commit(weight(2f))
        val mark = h.mark()
        h.commit(weight(3f))
        assertFalse(h.undoIfAt(mark), "a later edit makes the mark inert")
        assertEquals(weight(3f), h.theme)
        h.undo()
        assertTrue(h.undoIfAt(mark), "back at the mark, it undoes the marked step")
        assertEquals(weight(1f), h.theme)
        assertFalse(h.undoIfAt(mark))
    }
}
