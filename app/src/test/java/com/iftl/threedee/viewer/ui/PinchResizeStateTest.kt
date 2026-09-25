package com.iftl.threedee.viewer.ui

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic geometry tests complement the Android multi-pointer integration tests. The card is
 * a 720x720 at (180, 785) throughout.
 */
class PinchResizeStateTest {

    private val minSize = 570
    private val maxSize = 4200
    private val left = 180f
    private val top = 785f
    private val size = 720

    private fun state(fx: Float, fy: Float, half: Float = 100f): PinchResizeState =
        PinchResizeState().apply {
            begin(fx - half, fy - half, fx + half, fy + half, left, top, size, size)
        }

    /** The regression that mattered: touching down must not move the card at all. */
    @Test
    fun `no jump when the gesture starts`() {
        // Sweep the focus across the whole card, including the edges where the old
        // mixed-coordinate maths clamped and teleported it.
        for (fracX in listOf(0.0f, 0.25f, 0.5f, 0.85f, 1.0f)) {
            for (fracY in listOf(0.0f, 0.25f, 0.5f, 0.85f, 1.0f)) {
                val fx = left + fracX * size
                val fy = top + fracY * size
                val s = state(fx, fy)
                val b = s.update(fx - 100f, fy - 100f, fx + 100f, fy + 100f, minSize, maxSize)
                assertEquals("left moved (focus $fracX,$fracY)", left.toInt(), b[0])
                assertEquals("top moved (focus $fracX,$fracY)", top.toInt(), b[1])
                assertEquals("width changed", size, b[2])
                assertEquals("height changed", size, b[3])
            }
        }
    }

    @Test
    fun `doubling finger distance doubles the card`() {
        val fx = left + size / 2f
        val fy = top + size / 2f
        val s = state(fx, fy)
        val b = s.update(fx - 200f, fy - 200f, fx + 200f, fy + 200f, minSize, maxSize)
        assertEquals(size * 2, b[2])
        assertEquals(size * 2, b[3])
    }

    @Test
    fun `the point under the fingers stays put while scaling`() {
        val fracX = 0.85f
        val fracY = 0.9f // near the bottom edge: the case that used to clamp and jump
        val fx = left + fracX * size
        val fy = top + fracY * size
        val s = state(fx, fy)
        val b = s.update(fx - 160f, fy - 160f, fx + 160f, fy + 160f, minSize, maxSize)
        // Wherever the focus sat inside the card, it must still sit there afterwards.
        val newFracX = (fx - b[0]) / b[2].toFloat()
        val newFracY = (fy - b[1]) / b[3].toFloat()
        assertTrue("focus X drifted: $newFracX vs $fracX", abs(newFracX - fracX) < 0.01f)
        assertTrue("focus Y drifted: $newFracY vs $fracY", abs(newFracY - fracY) < 0.01f)
    }

    @Test
    fun `size stays within the allowed range`() {
        val fx = left + size / 2f
        val fy = top + size / 2f
        val s = state(fx, fy)
        val tiny = s.update(fx - 1f, fy, fx + 1f, fy, minSize, maxSize)
        assertTrue("under min: ${tiny[2]}", tiny[2] >= minSize)
        val huge = s.update(fx - 5000f, fy - 5000f, fx + 5000f, fy + 5000f, minSize, maxSize)
        assertTrue("over max: ${huge[2]}", huge[2] <= maxSize)
    }

    /** A single garbage sample must not be able to teleport the card. */
    @Test
    fun `one noisy sample cannot blow up the size`() {
        val fx = left + size / 2f
        val fy = top + size / 2f
        val s = state(fx, fy, half = 10f)
        val b = s.update(fx - 100000f, fy, fx + 100000f, fy, minSize, Int.MAX_VALUE)
        assertTrue(
            "scale exceeded MAX_SCALE: ${b[2]}",
            b[2] <= (size * PinchResizeState.MAX_SCALE).toInt() + 1,
        )
    }

    /** A pinch must scale the card uniformly -- never squash one axis against a portrait canvas. */
    @Test
    fun `aspect ratio is preserved while scaling`() {
        val fx = left + size / 2f
        val fy = top + size / 2f
        val s = state(fx, fy)
        for (half in listOf(30f, 60f, 150f, 400f)) {
            val b = s.update(fx - half, fy - half, fx + half, fy + half, minSize, maxSize)
            assertEquals("w/h diverged at half=$half", b[2], b[3])
        }
    }

    /**
     * Regression: zooming past the size ceiling must NOT fling the card into the top-left corner.
     * That happened when the caller clamped the size tighter than the value handed to update(), so
     * the anchor was solved for a card far bigger than the one actually produced.
     */
    @Test
    fun `zooming past the ceiling keeps the card under the fingers`() {
        val canvasMax = 1080 // the effective ceiling the caller will honour
        val fx = left + 0.5f * size
        val fy = top + 0.5f * size
        val s = state(fx, fy, half = 40f)
        // Spread the fingers far enough that the raw size would blow well past the ceiling.
        val b = s.update(fx - 4000f, fy - 4000f, fx + 4000f, fy + 4000f, minSize, canvasMax)

        assertTrue(
            "size exceeded the ceiling: ${b[2]}x${b[3]}",
            b[2] <= canvasMax && b[3] <= canvasMax,
        )
        // The pinch centre must still land at the same relative spot inside the card.
        val newFracX = (fx - b[0]) / b[2].toFloat()
        val newFracY = (fy - b[1]) / b[3].toFloat()
        assertTrue("card lurched horizontally: frac=$newFracX", abs(newFracX - 0.5f) < 0.02f)
        assertTrue("card lurched vertically: frac=$newFracY", abs(newFracY - 0.5f) < 0.02f)
        // And concretely: it must not end up pinned at the top-left origin.
        assertTrue(
            "card jumped to the corner: (${b[0]},${b[1]})",
            b[0] > -canvasMax && b[1] > -canvasMax,
        )
    }
}
