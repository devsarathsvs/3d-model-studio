package com.iftl.threedee.viewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.iftl.threedee.viewer.render.LabelPoint

/** Labels stay inside the viewport; a small placement search avoids overlapping text boxes. */
class LabelOverlayView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val text =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize =
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    11f,
                    resources.displayMetrics,
                )
            typeface =
                android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.NORMAL,
                )
        }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 39, 55) }
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(119, 220, 210)
            strokeWidth = d
        }
    private var points: List<LabelPoint> = emptyList()
    private var boxes: Array<RectF> = emptyArray()
    private var captions: List<String> = emptyList()
    private var revision = -1
    private var shown = false

    fun update(labels: List<LabelPoint>, version: Int, visible: Boolean) {
        if (points !== labels) {
            points = labels
            boxes = Array(labels.size) { RectF() }
            captions = labels.map { it.text }
            revision = -1
        }
        if (shown == visible && revision == version) return
        shown = visible
        revision = version
        if (shown) placeLabels()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        placeLabels()
    }

    private fun placeLabels() {
        if (width <= 0 || height <= 0) return
        val pad = 7 * d
        val boxHeight = text.fontSpacing + 10 * d
        val maxTextWidth = (width - pad * 4).coerceAtLeast(1f)
        captions =
            points.map {
                TextUtils.ellipsize(it.text, text, maxTextWidth, TextUtils.TruncateAt.END)
                    .toString()
            }
        for (i in points.indices) {
            val p = points[i]
            val box = boxes[i]
            box.setEmpty()
            if (!p.visible) continue
            val bw = (text.measureText(captions[i]) + pad * 2).coerceAtMost(width.toFloat())
            var bestScore = Float.MAX_VALUE
            var bestX = 0f
            var bestY = 0f
            for (candidate in 0..15) {
                val right = candidate % 2 == 0
                val row = candidate / 2
                val dx = if (right) 18 * d else -18 * d - bw
                val dy =
                    if (row % 2 == 0) -(row / 2 + 1) * (boxHeight + 4 * d)
                    else (row / 2) * (boxHeight + 4 * d)
                val x = (p.x + dx).coerceIn(0f, (width - bw).coerceAtLeast(0f))
                val y = (p.y + dy).coerceIn(0f, (height - boxHeight).coerceAtLeast(0f))
                var score = kotlin.math.abs(x - p.x) + kotlin.math.abs(y - p.y)
                for (j in 0 until i) {
                    val b = boxes[j]
                    if (
                        !b.isEmpty &&
                            x < b.right + 3 * d &&
                            x + bw > b.left - 3 * d &&
                            y < b.bottom + 3 * d &&
                            y + boxHeight > b.top - 3 * d
                    )
                        score += 100_000f
                }
                if (score < bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
            box.set(bestX, bestY, bestX + bw, bestY + boxHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!shown) return
        for (i in points.indices) {
            val box = boxes[i]
            if (box.isEmpty) continue
            val p = points[i]
            canvas.drawLine(
                p.x,
                p.y,
                p.x.coerceIn(box.left, box.right),
                p.y.coerceIn(box.top, box.bottom),
                line,
            )
            canvas.drawCircle(p.x, p.y, 2.5f * d, line)
            canvas.drawRoundRect(box, 6 * d, 6 * d, fill)
            canvas.drawText(
                captions[i],
                box.left + 7 * d,
                box.centerY() - (text.ascent() + text.descent()) / 2,
                text,
            )
        }
    }
}
