package com.iftl.threedee.viewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Region
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.withSave
import com.google.android.material.button.MaterialButton
import com.iftl.threedee.viewer.R
import com.iftl.threedee.viewer.data.CatalogEntry
import com.iftl.threedee.viewer.render.ModelInstance
import com.iftl.threedee.viewer.render.ScreenRect
import com.iftl.threedee.viewer.state.CardState
import kotlin.math.hypot

@android.annotation.SuppressLint("ViewConstructor")
class ModelContainerView(
    context: Context,
    val entry: CatalogEntry,
    val modelInstance: ModelInstance,
    private val canvasHeightPx: () -> Int,
    private val onClose: (ModelContainerView) -> Unit,
    private val onFocus: (ModelContainerView) -> Unit,
    private val onChanged: () -> Unit,
    private val onGestureEnd: () -> Unit,
) : ViewGroup(context) {
    private val density = resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).toInt()

    private val headerHeight = dp(102)
    private var requestedWidth = dp(280)
    private var requestedHeight = dp(300)
    private val minSize
        get() = minOf(dp(174), parentWidth, parentHeight).coerceAtLeast(1)

    private val parentWidth
        get() = (parent as? View)?.width?.coerceAtLeast(1) ?: dp(360)

    private val parentHeight
        get() = (parent as? View)?.height?.coerceAtLeast(1) ?: dp(600)

    private var centreX = 0.5f
    private var centreY = 0.5f
    private var desiredWidthDp = 280f
    private var desiredHeightDp = 300f
    var isMaximized = false
        private set

    private val overlay = LabelOverlayView(context)
    private val header = View(context).apply { setBackgroundColor(Color.rgb(17, 23, 32)) }
    private val title =
        TextView(context).apply {
            text = context.getString(R.string.card_title, entry.displayName)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER_VERTICAL
            contentDescription = context.getString(R.string.expand_named, entry.displayName)
            setOnClickListener {
                if (isMaximized) restoreFromMaximized() else maximize()
                onGestureEnd()
            }
        }
    private val hint =
        TextView(context).apply {
            textSize = 11f
            setTextColor(Color.rgb(146, 162, 181))
        }
    private val interaction = button(R.id.control_interact, R.drawable.ic_interact)
    private val labels = button(R.id.control_labels, R.drawable.ic_labels)
    private val close = button(R.id.control_close, R.drawable.ic_close)
    private val fullscreen = button(R.id.control_fullscreen, R.drawable.ic_fullscreen)
    private val controls = arrayOf(interaction, labels, close, fullscreen)
    private val frame =
        FrameMaskView(context).apply {
            cornerRadiusPx = dp(12).toFloat()
            canvasBackgroundColor = Color.rgb(7, 8, 11)
            borderWidthPx = density
        }

    private enum class Gesture {
        NONE,
        DRAG,
        PINCH,
    }

    private var gesture = Gesture.NONE
    private var interactionGesture = false
    private val parentOrigin = IntArray(2)
    private var pointerA = -1
    private var pointerB = -1
    private var grabX = 0f
    private var grabY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var initialSpan = 0f
    private var previousSpan = 0f
    private val pinch = PinchResizeState()

    init {
        setWillNotDraw(true)
        addView(overlay)
        addView(header)
        addView(title)
        addView(hint)
        addView(interaction)
        addView(labels)
        addView(close)
        addView(fullscreen)
        addView(frame)
        frame.isClickable = false
        interaction.setOnClickListener {
            endGesture()
            modelInstance.setInteractionMode(!modelInstance.isInteractionMode)
            updateControls()
            onGestureEnd()
        }
        labels.setOnClickListener {
            modelInstance.setLabelsVisible(!modelInstance.labelsVisible)
            updateControls()
            onGestureEnd()
        }
        close.setOnClickListener {
            endGesture()
            onClose(this)
        }
        fullscreen.setOnClickListener {
            if (isMaximized) restoreFromMaximized() else maximize()
            onGestureEnd()
        }
        labels.isEnabled = modelInstance.hasLabels()
        updateControls()
    }

    private fun button(viewId: Int, iconResource: Int) =
        MaterialButton(context).apply {
            id = viewId
            setIconResource(iconResource)
            iconSize = dp(22)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            gravity = android.view.Gravity.CENTER
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = dp(3)
            insetBottom = dp(3)
            setPadding(0, 0, 0, 0)
            cornerRadius = dp(9)
            strokeWidth = dp(1)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(46, 60, 77))
        }

    private fun style(button: MaterialButton, enabled: Boolean, danger: Boolean = false) {
        button.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (enabled) Color.rgb(121, 227, 212) else Color.rgb(28, 39, 53)
            )
        button.iconTint = android.content.res.ColorStateList.valueOf(
            if (enabled) Color.rgb(10, 35, 37)
            else if (danger) Color.rgb(255, 169, 163) else Color.rgb(214, 224, 235)
        )
        button.isSelected = enabled
    }

    fun updateControls() {
        style(interaction, modelInstance.isInteractionMode)
        style(labels, modelInstance.labelsVisible)
        style(close, false, true)
        style(fullscreen, isMaximized)
        fullscreen.setIconResource(
            if (isMaximized) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
        hint.setText(
            if (width in 1 until dp(240)) {
                if (modelInstance.isInteractionMode) R.string.interact_hint_short
                else R.string.arrange_hint_short
            } else if (modelInstance.isInteractionMode) R.string.interact_hint
            else R.string.arrange_hint
        )
        interaction.contentDescription =
            context.getString(
                if (modelInstance.isInteractionMode) R.string.stop_interacting_named
                else R.string.interact_named,
                entry.displayName,
            )
        labels.contentDescription =
            context.getString(
                if (modelInstance.labelsVisible) R.string.hide_labels_named
                else R.string.show_labels_named,
                entry.displayName,
            )
        close.contentDescription = context.getString(R.string.close_named, entry.displayName)
        fullscreen.contentDescription =
            context.getString(
                if (isMaximized) R.string.restore_named else R.string.expand_named,
                entry.displayName,
            )
        title.contentDescription = fullscreen.contentDescription
        controls.forEach { TooltipCompat.setTooltipText(it, it.contentDescription) }
    }

    fun placeInitial(left: Int, top: Int, width: Int, height: Int) =
        applyBounds(left, top, width, height)

    private fun applyBounds(l: Int, t: Int, w: Int, h: Int, remember: Boolean = true) {
        val cw = w.coerceIn(minSize, parentWidth)
        val ch = h.coerceIn(minSize, parentHeight)
        if (cw != requestedWidth || ch != requestedHeight) {
            requestedWidth = cw
            requestedHeight = ch
            requestLayout()
        }
        x = InteractionMath.clampPosition(l, cw, parentWidth).toFloat()
        y = InteractionMath.clampPosition(t, ch, parentHeight).toFloat()
        if (remember && !isMaximized) {
            centreX = (x + cw / 2f) / parentWidth
            centreY = (y + ch / 2f) / parentHeight
            desiredWidthDp = cw / density
            desiredHeightDp = ch / density
        }
        refreshViewport()
        onChanged()
    }

    fun maximize() {
        if (isMaximized) return
        endGesture()
        isMaximized = true
        modelInstance.setInteractionMode(true)
        applyBounds(0, 0, parentWidth, parentHeight, false)
        updateControls()
    }

    fun restoreFromMaximized(): Boolean {
        if (!isMaximized) return false
        endGesture()
        isMaximized = false
        modelInstance.setInteractionMode(false)
        reflowTo(parentWidth, parentHeight)
        updateControls()
        return true
    }

    fun resetToArrangeMode() {
        endGesture()
        if (!restoreFromMaximized()) {
            modelInstance.setInteractionMode(false)
            updateControls()
        }
    }

    fun reflowTo(w: Int, h: Int) {
        if (isMaximized) applyBounds(0, 0, w, h, false)
        else {
            val cw = (desiredWidthDp * density).toInt().coerceIn(minSize, w.coerceAtLeast(minSize))
            val ch = (desiredHeightDp * density).toInt().coerceIn(minSize, h.coerceAtLeast(minSize))
            applyBounds(
                (centreX * w - cw / 2).toInt(),
                (centreY * h - ch / 2).toInt(),
                cw,
                ch,
                false,
            )
        }
    }

    fun snapshot() =
        CardState(
            entry.assetPath,
            centreX,
            centreY,
            desiredWidthDp,
            desiredHeightDp,
            isMaximized,
            modelInstance.isInteractionMode,
            modelInstance.labelsVisible,
            modelInstance.cameraPosition().toList(),
        )

    fun restore(state: CardState) {
        centreX = state.centreX
        centreY = state.centreY
        desiredWidthDp = state.widthDp
        desiredHeightDp = state.heightDp
        isMaximized = state.maximized
        modelInstance.restoreCamera(state.eye.toFloatArray())
        modelInstance.setInteractionMode(state.interaction)
        modelInstance.setLabelsVisible(state.labels)
        reflowTo(parentWidth, parentHeight)
        updateControls()
    }

    val contentRect
        get() =
            ScreenRect(x.toInt(), y.toInt() + headerHeight, x.toInt() + width, y.toInt() + height)

    val screenRect
        get() = ScreenRect(x.toInt(), y.toInt(), x.toInt() + width, y.toInt() + height)

    fun setFocused(focused: Boolean) {
        frame.borderColor = if (focused) Color.rgb(121, 227, 212) else Color.rgb(49, 64, 81)
        frame.borderWidthPx = (if (focused) 1.5f else 1f) * density
        invalidate()
    }

    fun refreshLabels() =
        overlay.update(
            modelInstance.labelPositionsPx,
            modelInstance.labelRevision,
            modelInstance.labelsVisible,
        )

    fun refreshViewport() {
        if (width > 0 && height > headerHeight) {
            modelInstance.setViewportPx(
                x.toInt(),
                y.toInt() + headerHeight,
                width,
                height - headerHeight,
                canvasHeightPx(),
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(requestedWidth, requestedHeight)
        fun measureChildTo(child: View, w: Int, h: Int) =
            child.measure(
                MeasureSpec.makeMeasureSpec(w.coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h.coerceAtLeast(1), MeasureSpec.EXACTLY),
            )
        measureChildTo(overlay, requestedWidth, requestedHeight - headerHeight)
        measureChildTo(header, requestedWidth, headerHeight)
        measureChildTo(title, requestedWidth - dp(28), dp(31))
        measureChildTo(hint, requestedWidth - dp(20), dp(15))
        val buttonWidth = (requestedWidth - dp(12)) / controls.size - dp(2)
        controls.forEach { measureChildTo(it, buttonWidth, dp(48)) }
        measureChildTo(frame, requestedWidth, requestedHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        overlay.layout(0, headerHeight, width, height)
        header.layout(0, 0, width, headerHeight)
        title.layout(dp(14), dp(4), width - dp(14), dp(35))
        hint.layout(dp(14), dp(35), width - dp(6), dp(50))
        val cell = (width - dp(12)) / controls.size
        for (i in controls.indices) controls[i].layout(
            dp(7) + cell * i,
            dp(51),
            dp(7) + cell * (i + 1) - dp(2),
            dp(99),
        )
        frame.layout(0, 0, width, height)
        if (changed) {
            refreshViewport()
            updateControls()
            onChanged()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onFocus(this)
        return false
    }

    private fun px(event: MotionEvent, i: Int) =
        event.rawX + event.getX(i) - event.getX(0) - parentOrigin[0]

    private fun py(event: MotionEvent, i: Int) =
        event.rawY + event.getY(i) - event.getY(0) - parentOrigin[1]

    private fun span(event: MotionEvent, a: Int, b: Int) =
        hypot(event.getX(a) - event.getX(b), event.getY(a) - event.getY(b))

    private fun startDrag(event: MotionEvent, index: Int) {
        pointerA = event.getPointerId(index)
        gesture = Gesture.DRAG
        grabX = px(event, index) - x
        grabY = py(event, index) - y
        if (interactionGesture)
            modelInstance.beginGrab(
                event.getX(index).toInt(),
                event.getY(index).toInt() - headerHeight,
            )
    }

    private fun startPinch(event: MotionEvent, a: Int, b: Int) {
        modelInstance.endGrab()
        pointerA = event.getPointerId(a)
        pointerB = event.getPointerId(b)
        gesture = Gesture.PINCH
        initialSpan = span(event, a, b)
        previousSpan = initialSpan
        pinch.begin(px(event, a), py(event, a), px(event, b), py(event, b), x, y, width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                (parent as View).getLocationOnScreen(parentOrigin)
                interactionGesture = modelInstance.isInteractionMode
                downX = event.rawX
                downY = event.rawY
                moved = false
                startDrag(event, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN ->
                if (event.pointerCount == 2) {
                    moved = true
                    startPinch(event, 0, 1)
                }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.rawX - downX, event.rawY - downY) > dp(6)) moved = true
                val a = event.findPointerIndex(pointerA)
                if (a < 0) return true
                if (gesture == Gesture.DRAG) {
                    if (interactionGesture)
                        modelInstance.updateGrab(
                            event.getX(a).toInt(),
                            event.getY(a).toInt() - headerHeight,
                        )
                    else if (!isMaximized)
                        applyBounds(
                            (px(event, a) - grabX).toInt(),
                            (py(event, a) - grabY).toInt(),
                            width,
                            height,
                        )
                } else if (gesture == Gesture.PINCH) {
                    val b = event.findPointerIndex(pointerB)
                    if (b < 0) return true
                    val distance = span(event, a, b)
                    if (interactionGesture) {
                        modelInstance.zoom(
                            ((event.getX(a) + event.getX(b)) / 2).toInt(),
                            ((event.getY(a) + event.getY(b)) / 2).toInt() - headerHeight,
                            InteractionMath.zoomDelta(previousSpan, distance, minOf(width, height)),
                        )
                    } else if (isMaximized) {
                        if (InteractionMath.shouldRestoreFullscreen(initialSpan, distance)) {
                            restoreFromMaximized()
                            gesture = Gesture.NONE
                        }
                    } else {
                        val cap = minOf(parentWidth, parentHeight)
                        val rect =
                            pinch.update(
                                px(event, a),
                                py(event, a),
                                px(event, b),
                                py(event, b),
                                minSize,
                                cap,
                            )
                        if (maxOf(rect[2], rect[3]) >= cap && distance > initialSpan * 1.08f) {
                            maximize()
                            gesture = Gesture.NONE
                        } else applyBounds(rect[0], rect[1], rect[2], rect[3])
                    }
                    previousSpan = distance
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gesture != Gesture.NONE) {
                    modelInstance.endGrab()
                    val remaining = (0 until event.pointerCount).filter { it != event.actionIndex }
                    if (remaining.size >= 2) startPinch(event, remaining[0], remaining[1])
                    else if (remaining.isNotEmpty()) startDrag(event, remaining[0])
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) performClick()
                endGesture()
                onGestureEnd()
            }
            MotionEvent.ACTION_CANCEL -> {
                endGesture()
                onGestureEnd()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onFocus(this)
        return true
    }

    private fun endGesture() {
        modelInstance.endGrab()
        gesture = Gesture.NONE
        pointerA = -1
        pointerB = -1
    }

    @Suppress("DEPRECATION")
    override fun dispatchDraw(canvas: Canvas) {
        val group = parent as? ViewGroup
        canvas.withSave {
            if (group != null)
                for (i in group.indexOfChild(this@ModelContainerView) + 1 until group.childCount) {
                    val other = group.getChildAt(i)
                    val l = other.x - x
                    val t = other.y - y
                    if (Build.VERSION.SDK_INT >= 26)
                        canvas.clipOutRect(l, t, l + other.width, t + other.height)
                    else
                        canvas.clipRect(
                            l,
                            t,
                            l + other.width,
                            t + other.height,
                            Region.Op.DIFFERENCE,
                        )
                }
            super.dispatchDraw(canvas)
        }
    }

    fun updateCornerMasks() {
        val group = parent as? ViewGroup ?: return
        val radius = dp(12)
        for (corner in 0..3) {
            val cx = x + if (corner == 1 || corner == 2) width - radius else 0
            val cy = y + if (corner >= 2) height - radius else 0
            var covered = false
            for (j in 0 until group.indexOfChild(this)) {
                val other = group.getChildAt(j)
                if (
                    cx < other.x + other.width &&
                        cx + radius > other.x &&
                        cy < other.y + other.height &&
                        cy + radius > other.y
                )
                    covered = true
            }
            frame.setCornerMaskEnabled(corner, !covered)
        }
        invalidate()
    }
}
