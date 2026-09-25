package com.iftl.threedee.viewer.render

/** Exact union coverage: a card may be covered by several higher cards together. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

object ViewportCoverage {
    fun isCovered(rect: ScreenRect, covers: List<ScreenRect>): Boolean {
        var remaining = listOf(rect)
        for (cover in covers) {
            remaining =
                remaining.flatMap { r ->
                    val l = maxOf(r.left, cover.left)
                    val t = maxOf(r.top, cover.top)
                    val rr = minOf(r.right, cover.right)
                    val b = minOf(r.bottom, cover.bottom)
                    if (l >= rr || t >= b) listOf(r)
                    else
                        buildList {
                            if (r.top < t) add(ScreenRect(r.left, r.top, r.right, t))
                            if (b < r.bottom) add(ScreenRect(r.left, b, r.right, r.bottom))
                            if (r.left < l) add(ScreenRect(r.left, t, l, b))
                            if (rr < r.right) add(ScreenRect(rr, t, r.right, b))
                        }
                }
            if (remaining.isEmpty()) return true
        }
        return false
    }
}
