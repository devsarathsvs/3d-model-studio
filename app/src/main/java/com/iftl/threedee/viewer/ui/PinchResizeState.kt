package com.iftl.threedee.viewer.ui

import kotlin.math.hypot

/**
 * Pure pinch-to-resize mathematics, tested independently of the Android event adapter.
 *
 * Every coordinate here is in ONE space: the container's parent. Mixing parent-relative view
 * positions with screen-absolute pointer positions is what previously made cards jump when the
 * pinch focus landed near an edge (the mismatch cancelled out algebraically until the focus
 * fraction clamped, at which point it very much did not).
 */
class PinchResizeState {

    private var startDist = 1f
    private var startWidth = 0
    private var startHeight = 0
    private var focusX = 0f
    private var focusY = 0f
    private var focusFracX = 0.5f
    private var focusFracY = 0.5f

    /**
     * All args parent-relative. [left]/[top]/[width]/[height] are the container's current bounds.
     */
    fun begin(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        left: Float,
        top: Float,
        width: Int,
        height: Int,
    ) {
        startDist = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat().coerceAtLeast(1f)
        startWidth = width
        startHeight = height
        focusX = (x0 + x1) / 2f
        focusY = (y0 + y1) / 2f
        focusFracX = if (width > 0) ((focusX - left) / width).coerceIn(0f, 1f) else 0.5f
        focusFracY = if (height > 0) ((focusY - top) / height).coerceIn(0f, 1f) else 0.5f
    }

    /**
     * @param maxSize must be the EFFECTIVE limit the caller will actually honour (i.e. already
     *   constrained by the canvas). Position is derived from the final, clamped size -- if the
     *   caller clamps the size again afterwards, the anchor maths silently refers to a card that
     *   never existed and the card lurches away, which is what previously threw every pinched card
     *   into the top-left corner.
     * @return [left, top, width, height], parent-relative.
     */
    fun update(x0: Float, y0: Float, x1: Float, y1: Float, minSize: Int, maxSize: Int): IntArray {
        val dist = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat().coerceAtLeast(1f)
        // Bound scale relative to the start of this gesture.
        val scale = (dist / startDist).coerceIn(MIN_SCALE, MAX_SCALE)
        val rawW = (startWidth * scale).coerceAtLeast(1f)
        val rawH = (startHeight * scale).coerceAtLeast(1f)
        // Fit into [minSize, maxSize] with ONE uniform factor, so the card keeps its aspect
        // instead of being squashed against whichever axis is tighter.
        val upper = minOf(maxSize / rawW, maxSize / rawH)
        val lower = maxOf(minSize / rawW, minSize / rawH).coerceAtMost(upper)
        val factor = 1f.coerceIn(lower, upper)
        val w = (rawW * factor).toInt().coerceAtLeast(1)
        val h = (rawH * factor).toInt().coerceAtLeast(1)
        // Keep the point under the user's fingers pinned to the same spot in the card, using the
        // size we are actually returning.
        val l = ((x0 + x1) / 2f - focusFracX * w).toInt()
        val t = ((y0 + y1) / 2f - focusFracY * h).toInt()
        return intArrayOf(l, t, w, h)
    }

    companion object {
        const val MIN_SCALE = 0.2f
        const val MAX_SCALE = 5f
    }
}
