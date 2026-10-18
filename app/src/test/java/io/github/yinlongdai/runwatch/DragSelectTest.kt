package io.github.yinlongdai.runwatch

import io.github.yinlongdai.runwatch.ui.dragSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragSelectTest {
    private val order = listOf("a", "b", "c", "d", "e")

    @Test
    fun dragDownSelectsTheRange() {
        assertEquals(listOf("b", "c", "d"), dragSelection(order, emptyList(), "b", "d"))
    }

    @Test
    fun dragUpAddsFromTheStartRow() {
        assertEquals(listOf("d", "c", "b"), dragSelection(order, emptyList(), "d", "b"))
    }

    @Test
    fun keepsEarlierSelectionAndDoesNotDuplicate() {
        assertEquals(listOf("e", "c", "a", "b"), dragSelection(order, listOf("e", "c"), "a", "c"))
    }

    @Test
    fun draggingBackShrinksTheRange() {
        // Rows the finger has left go back to how they were before the drag.
        assertEquals(listOf("e", "a"), dragSelection(order, listOf("e"), "a", "a"))
    }

    @Test
    fun startingOnASelectedRowDeselects() {
        assertEquals(listOf("a", "e"), dragSelection(order, listOf("a", "b", "c", "e"), "b", "d"))
    }

    @Test
    fun unknownKeyIsIgnored() {
        assertNull(dragSelection(order, emptyList(), "a", "zzz"))
    }
}
