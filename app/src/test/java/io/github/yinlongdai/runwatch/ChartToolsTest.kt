package io.github.yinlongdai.runwatch

import io.github.yinlongdai.runwatch.data.ChartMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartToolsTest {

    @Test
    fun robustRange_ignoresSpikes() {
        val y = DoubleArray(1000) { 1.0 + it / 1000.0 }.also { it[500] = 1e6 }
        val (lo, hi) = ChartMath.robustRange(listOf(y))!!
        assertTrue(lo >= 1.0 && lo < 1.02)
        assertTrue("spike excluded, got $hi", hi < 2.0)
        assertNull(ChartMath.robustRange(listOf(doubleArrayOf(Double.NaN))))
    }

    @Test
    fun stats_lastMinMax() {
        assertEquals(ChartMath.Stats(2.0, 0.5, 3.0), ChartMath.stats(doubleArrayOf(1.0, 3.0, 0.5, 2.0)))
        assertNull(ChartMath.stats(DoubleArray(0)))
    }

    @Test
    fun zoomWindow_keepsAnchorAndBounds() {
        val full = 0.0..100.0
        // Zoom 2x around the middle: 25..75.
        val z = ChartMath.zoomWindow(full, full, anchor = 0.5, zoom = 2.0, pan = 0.0)
        assertEquals(25.0, z.start, 1e-9)
        assertEquals(75.0, z.endInclusive, 1e-9)
        // Zooming around the left edge keeps the left edge fixed.
        val left = ChartMath.zoomWindow(full, full, anchor = 0.0, zoom = 4.0, pan = 0.0)
        assertEquals(0.0, left.start, 1e-9)
        assertEquals(25.0, left.endInclusive, 1e-9)
        // Panning can't leave the data range, and zooming out stops at the full range.
        val panned = ChartMath.zoomWindow(full, 25.0..75.0, anchor = 0.5, zoom = 1.0, pan = 5.0)
        assertEquals(0.0, panned.start, 1e-9)
        assertEquals(full, ChartMath.zoomWindow(full, 25.0..75.0, anchor = 0.5, zoom = 0.1, pan = 0.0))
        // Can't zoom past the minimum window.
        val deep = ChartMath.zoomWindow(full, full, anchor = 0.5, zoom = 1e6, pan = 0.0)
        assertEquals(0.5, deep.endInclusive - deep.start, 1e-9)
    }
}
