package com.iftl.threedee.viewer.ui

/** Gesture decisions shared by the Android adapter and JVM regression tests. */
object InteractionMath {
    fun zoomDelta(previousSpan: Float, span: Float, viewportSpan: Int): Float =
        -((span - previousSpan) / viewportSpan.coerceAtLeast(1)) * 6f

    fun shouldRestoreFullscreen(startSpan: Float, currentSpan: Float): Boolean =
        startSpan > 0f && currentSpan / startSpan < 0.88f

    fun clampPosition(position: Int, size: Int, canvasSize: Int): Int =
        position.coerceIn(0, (canvasSize - size).coerceAtLeast(0))
}
