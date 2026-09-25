package com.iftl.threedee.viewer.render

/** Column-major world-to-viewport projection, with caller-owned output and no frame allocations. */
object LabelProjector {
    fun project(
        view: DoubleArray,
        projection: DoubleArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        z: Float,
        out: FloatArray,
    ): Boolean {
        val vx = view[0] * x + view[4] * y + view[8] * z + view[12]
        val vy = view[1] * x + view[5] * y + view[9] * z + view[13]
        val vz = view[2] * x + view[6] * y + view[10] * z + view[14]
        val vw = view[3] * x + view[7] * y + view[11] * z + view[15]
        val cx = projection[0] * vx + projection[4] * vy + projection[8] * vz + projection[12] * vw
        val cy = projection[1] * vx + projection[5] * vy + projection[9] * vz + projection[13] * vw
        val cz = projection[2] * vx + projection[6] * vy + projection[10] * vz + projection[14] * vw
        val cw = projection[3] * vx + projection[7] * vy + projection[11] * vz + projection[15] * vw
        if (cw <= 0 || !cw.isFinite()) return false
        val nx = cx / cw
        val ny = cy / cw
        val nz = cz / cw
        if (nx !in -1.0..1.0 || ny !in -1.0..1.0 || nz !in -1.0..1.0) return false
        out[0] = ((nx * 0.5 + 0.5) * width).toFloat()
        out[1] = ((0.5 - ny * 0.5) * height).toFloat()
        return true
    }
}
