package com.iftl.threedee.viewer.render

import org.junit.Assert.*
import org.junit.Test

class ViewportCoverageTest {
    private val card = ScreenRect(0, 0, 100, 100)

    @Test
    fun `combined covers hide a card`() {
        assertTrue(
            ViewportCoverage.isCovered(
                card,
                listOf(ScreenRect(0, 0, 50, 100), ScreenRect(50, 0, 100, 100)),
            )
        )
    }

    @Test
    fun `even a narrow gap means the card must render`() {
        assertFalse(
            ViewportCoverage.isCovered(
                card,
                listOf(ScreenRect(0, 0, 49, 100), ScreenRect(50, 0, 100, 100)),
            )
        )
    }

    @Test
    fun `empty or adjacent covers leave card visible`() {
        assertFalse(ViewportCoverage.isCovered(card, emptyList()))
        assertFalse(ViewportCoverage.isCovered(card, listOf(ScreenRect(100, 0, 200, 100))))
    }
}
