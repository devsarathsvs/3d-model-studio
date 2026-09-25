package com.iftl.threedee.viewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The 3D content behind this view is a plain rectangle -- Filament renders into a shared
 * TextureView, so a container can't clip its own corners directly. This paints the canvas
 * background colour into the four corner slivers outside a rounded-rect, which reads as a genuinely
 * rounded card, plus a thin accent border for definition.
 *
 * Each corner is masked independently via [setCornerMaskEnabled], because painting the canvas
 * colour is only correct where the canvas is actually what sits behind. Where another card overlaps
 * that corner, painting canvas-black would stamp a hard black notch over the card behind -- so that
 * corner is simply left unmasked instead. Cards share a background colour, so an unmasked corner
 * blends into the card underneath rather than punching a hole in it.
 */
class FrameMaskView(context: Context) : android.view.View(context) {

    var cornerRadiusPx: Float = 0f
        set(value) {
            field = value
            rebuildPaths()
        }

    var canvasBackgroundColor: Int = Color.BLACK
    var borderColor: Int = 0x4D4FC3F7
        set(value) {
            field = value
            invalidate()
        }

    var borderWidthPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outerPath = Path()
    private val roundedPath = Path()
    private val fullMaskPath = Path()
    private val cornerRectPath = Path()
    /** Index order: 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left. */
    private val cornerPaths = Array(4) { Path() }
    private val cornerEnabled = booleanArrayOf(true, true, true, true)

    init {
        setWillNotDraw(false)
    }

    /** @param corner 0=TL, 1=TR, 2=BR, 3=BL. */
    fun setCornerMaskEnabled(corner: Int, enabled: Boolean) {
        if (cornerEnabled[corner] != enabled) {
            cornerEnabled[corner] = enabled
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPaths()
    }

    private fun rebuildPaths() {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = cornerRadiusPx

        outerPath.reset()
        outerPath.addRect(0f, 0f, w, h, Path.Direction.CW)
        roundedPath.reset()
        roundedPath.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        fullMaskPath.reset()
        fullMaskPath.op(outerPath, roundedPath, Path.Op.DIFFERENCE)

        // Split the ring of slivers into its four corners so they can be toggled individually.
        val rects =
            arrayOf(
                RectF(0f, 0f, r, r),
                RectF(w - r, 0f, w, r),
                RectF(w - r, h - r, w, h),
                RectF(0f, h - r, r, h),
            )
        for (i in 0..3) {
            cornerRectPath.reset()
            cornerRectPath.addRect(rects[i], Path.Direction.CW)
            cornerPaths[i].reset()
            cornerPaths[i].op(fullMaskPath, cornerRectPath, Path.Op.INTERSECT)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        maskPaint.color = canvasBackgroundColor
        for (i in 0..3) {
            if (cornerEnabled[i]) canvas.drawPath(cornerPaths[i], maskPaint)
        }
        if (borderWidthPx > 0f) {
            borderPaint.color = borderColor
            borderPaint.strokeWidth = borderWidthPx
            val inset = borderWidthPx / 2f
            canvas.drawRoundRect(
                inset,
                inset,
                width - inset,
                height - inset,
                cornerRadiusPx,
                cornerRadiusPx,
                borderPaint,
            )
        }
    }
}
