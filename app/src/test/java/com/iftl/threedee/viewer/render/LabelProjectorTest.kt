package com.iftl.threedee.viewer.render

import org.junit.Assert.*
import org.junit.Test

class LabelProjectorTest {
    private val identity =
        DoubleArray(16).apply {
            this[0] = 1.0
            this[5] = 1.0
            this[10] = 1.0
            this[15] = 1.0
        }

    private fun projection(aspect: Double) =
        DoubleArray(16).apply {
            this[0] = 1 / aspect
            this[5] = 1.0
            this[10] = -1.002
            this[11] = -1.0
            this[14] = -0.2002
        }

    @Test
    fun `origin projects into the centre`() {
        val out = FloatArray(2)
        assertTrue(LabelProjector.project(identity, projection(1.0), 400, 400, 0f, 0f, -5f, out))
        assertArrayEquals(floatArrayOf(200f, 200f), out, 0.001f)
    }

    @Test
    fun `aspect changes require fresh projection not cached fractions`() {
        val out = FloatArray(2)
        LabelProjector.project(identity, projection(1.0), 400, 400, 1f, 0f, -5f, out)
        assertEquals(240f, out[0], 0.001f)
        LabelProjector.project(identity, projection(0.5), 400, 800, 1f, 0f, -5f, out)
        assertEquals(280f, out[0], 0.001f)
    }

    @Test
    fun `camera translation moves the anchor`() {
        val view = identity.copyOf().apply { this[12] = -1.0 }
        val out = FloatArray(2)
        LabelProjector.project(view, projection(1.0), 400, 400, 1f, 0f, -5f, out)
        assertEquals(200f, out[0], 0.001f)
    }

    @Test
    fun `hidden offscreen and behind camera anchors are rejected`() {
        val out = FloatArray(2)
        assertFalse(LabelProjector.project(identity, projection(1.0), 400, 400, 0f, 0f, 5f, out))
        assertFalse(LabelProjector.project(identity, projection(1.0), 400, 400, 100f, 0f, -5f, out))
        assertFalse(
            LabelProjector.project(identity, projection(1.0), 400, 400, 0f, 0f, -0.01f, out)
        )
    }
}
