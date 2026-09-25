package com.iftl.threedee.viewer.ui

import org.junit.Assert.*
import org.junit.Test

class InteractionMathTest {
    @Test
    fun `spreading zooms in and pinching zooms out`() {
        assertTrue(InteractionMath.zoomDelta(100f, 150f, 500) < 0)
        assertTrue(InteractionMath.zoomDelta(150f, 100f, 500) > 0)
        assertEquals(0f, InteractionMath.zoomDelta(100f, 100f, 500), 0f)
    }

    @Test
    fun `fullscreen only restores after a deliberate inward pinch`() {
        for (span in listOf(100f, 105f, 140f, 90f)) assertFalse(
            InteractionMath.shouldRestoreFullscreen(100f, span)
        )
        assertTrue(InteractionMath.shouldRestoreFullscreen(100f, 80f))
        assertFalse(InteractionMath.shouldRestoreFullscreen(0f, 0f))
    }

    @Test
    fun `the complete card stays reachable at either edge`() {
        assertEquals(120, InteractionMath.clampPosition(900, 240, 360))
        assertEquals(0, InteractionMath.clampPosition(-900, 240, 360))
        assertEquals(0, InteractionMath.clampPosition(15, 360, 360))
    }

    @Test
    fun `a moving pinch midpoint follows the fingers`() {
        val state = PinchResizeState()
        state.begin(100f, 100f, 200f, 200f, 50f, 50f, 300, 300)
        val b = state.update(120f, 130f, 220f, 230f, 100, 1000)
        assertArrayEquals(intArrayOf(70, 80, 300, 300), b)
    }

    @Test
    fun `rectangular pinch preserves ratio and honours maximum`() {
        val state = PinchResizeState()
        state.begin(100f, 100f, 200f, 200f, 0f, 0f, 1080, 2290)
        val b = state.update(100f, 100f, 200f, 200f, 570, 1080)
        assertTrue(b[2] <= 1080 && b[3] <= 1080)
        assertEquals(1080f / 2290, b[2].toFloat() / b[3], 0.002f)
    }
}
