package com.iftl.threedee.viewer

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iftl.threedee.viewer.data.ModelCatalog
import com.iftl.threedee.viewer.render.LabelProjector
import com.iftl.threedee.viewer.state.WorkspaceStore
import com.iftl.threedee.viewer.ui.ModelContainerView
import java.io.File
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Filament assets and Android touch dispatch, including two-finger MotionEvents. */
@RunWith(AndroidJUnit4::class)
class WorkspaceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Avoid OEM MessageQueue idle-handler races; synchronize directly with the main thread.
    private fun onActivity(action: (MainActivity) -> Unit) {
        instrumentation.runOnMainSync {
            val activity =
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .single()
            action(activity)
        }
    }

    private fun cards(activity: MainActivity): List<ModelContainerView> {
        val layer = activity.findViewById<ViewGroup>(R.id.containerLayer)
        return (0 until layer.childCount).map { layer.getChildAt(it) as ModelContainerView }
    }

    private fun waitFor(timeout: Long = 60000, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        do {
            var ready = false
            instrumentation.runOnMainSync {
                val activity =
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<MainActivity>()
                        .singleOrNull()
                ready = activity?.let(condition) ?: false
            }
            if (ready) {
                instrumentation.runOnMainSync {}
                return
            }
            Thread.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for workspace state")
    }

    private fun button(card: ModelContainerView, id: Int): View = card.findViewById(id)

    private fun event(down: Long, action: Int, points: List<Pair<Float, Float>>) {
        val props =
            Array(points.size) { i ->
                MotionEvent.PointerProperties().apply {
                    id = i
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(points.size) { i ->
                MotionEvent.PointerCoords().apply {
                    x = points[i].first
                    y = points[i].second
                    pressure = 1f
                    size = 1f
                }
            }
        val e =
            MotionEvent.obtain(
                down,
                SystemClock.uptimeMillis(),
                action,
                points.size,
                props,
                coords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        instrumentation.sendPointerSync(e)
        e.recycle()
    }

    private fun pinch(x: Float, y: Float, start: Float, end: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf(x - start to y))
        event(
            down,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(x - start to y, x + start to y),
        )
        for (i in 1..24) {
            val s = start + (end - start) * i / 24
            event(down, MotionEvent.ACTION_MOVE, listOf(x - s to y, x + s to y))
            Thread.sleep(16)
        }
        event(
            down,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(x - end to y, x + end to y),
        )
        event(down, MotionEvent.ACTION_UP, listOf(x - end to y))
        instrumentation.runOnMainSync {}
    }

    private fun drag(x: Float, y: Float, dx: Float, dy: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf(x to y))
        for (i in 1..30) {
            event(down, MotionEvent.ACTION_MOVE, listOf(x + dx * i / 30 to y + dy * i / 30))
            Thread.sleep(16)
        }
        event(down, MotionEvent.ACTION_UP, listOf(x + dx to y + dy))
        instrumentation.runOnMainSync {}
    }

    private fun centre(card: ModelContainerView): Pair<Float, Float> {
        val p = IntArray(2)
        card.getLocationOnScreen(p)
        return (p[0] + card.width / 2f) to (p[1] + card.height * 0.72f)
    }

    @Test
    fun fiveModelsGesturesLabelsCleanupAndIdleRendering() {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("workspace", 0)
        val previous = prefs.getString("cards", null)
        prefs.edit().clear().commit()
        instrumentation.startActivitySync(
            android.content
                .Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val timingThread = HandlerThread("frame-metrics").apply { start() }
        val timings = Collections.synchronizedList(mutableListOf<Long>())
        try {
            onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            ModelCatalog.entries.forEachIndexed { index, entry ->
                onActivity { it.addModel(entry) }
                waitFor() {
                    cards(it).size == index + 1 && it.findViewById<View>(R.id.fabAddModel).isEnabled
                }
            }
            onActivity { a ->
                assertEquals(5, a.engineHost.modelCount)
                assertEquals(5, a.engineHost.ownedLightCount)
                assertTrue(cards(a).all { !it.modelInstance.isInteractionMode && !it.isMaximized })
                a.window.addOnFrameMetricsAvailableListener(
                    { _, metrics, _ ->
                        timings.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                    },
                    Handler(timingThread.looper),
                )
            }
            waitFor() { it.window.decorView.hasWindowFocus() }
            Thread.sleep(400) // Let the final card layout and texture upload settle.
            var frames = 0L
            onActivity { frames = it.engineHost.submittedFrames }
            Thread.sleep(1200)
            onActivity {
                assertTrue(
                    "Idle renderer kept submitting frames",
                    it.engineHost.submittedFrames - frames <= 1,
                )
            }

            var resizePoint = 0f to 0f
            var resizeWidth = 0
            var resizeCamera = FloatArray(3)
            onActivity {
                val card = cards(it).last()
                resizePoint = centre(card)
                resizeWidth = card.width
                resizeCamera = card.modelInstance.cameraPosition()
            }
            pinch(resizePoint.first, resizePoint.second, 100f, 82f)
            onActivity {
                val card = cards(it).last()
                assertTrue("Normal pinch did not resize the card", card.width < resizeWidth)
                assertArrayEquals(resizeCamera, card.modelInstance.cameraPosition(), 0.001f)
                it.arrangeCards()
            }
            instrumentation.runOnMainSync {}
            Thread.sleep(500)
            onActivity { assertEquals(5, cards(it).count { c -> c.modelInstance.renderVisible }) }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "five-models.png").outputStream().use {
                    output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
            }
            val fiveModelPss = android.os.Debug.getPss()
            var point = 0f to 0f
            var beforeEye = FloatArray(3)
            var originalBounds = IntArray(4)
            onActivity {
                val c = cards(it).last()
                button(c, R.id.control_interact).performClick()
                button(c, R.id.control_labels).performClick()
                beforeEye = c.modelInstance.cameraPosition()
                point = centre(c)
                originalBounds = intArrayOf(c.x.toInt(), c.y.toInt(), c.width, c.height)
            }
            drag(point.first, point.second, 90f, -45f)
            onActivity {
                val c = cards(it).last()
                assertFalse(
                    "Orbit did not change camera",
                    beforeEye.contentEquals(c.modelInstance.cameraPosition()),
                )
                assertArrayEquals(
                    originalBounds,
                    intArrayOf(c.x.toInt(), c.y.toInt(), c.width, c.height),
                )
                beforeEye = c.modelInstance.cameraPosition()
            }
            pinch(point.first, point.second, 70f, 140f)
            onActivity {
                val c = cards(it).last()
                val centre = c.modelInstance.asset.boundingBox.getCenter()
                fun distance(p: FloatArray) =
                    (0..2).sumOf { i -> ((p[i] - centre[i]) * (p[i] - centre[i])).toDouble() }
                assertTrue(
                    "Spread must zoom in",
                    distance(c.modelInstance.cameraPosition()) < distance(beforeEye),
                )
                assertArrayEquals(
                    originalBounds,
                    intArrayOf(c.x.toInt(), c.y.toInt(), c.width, c.height),
                )
                button(c, R.id.control_interact).performClick()
                button(c, R.id.control_fullscreen).performClick()
                assertTrue("Fullscreen must enable interaction", c.modelInstance.isInteractionMode)
                listOf(R.id.control_interact, R.id.control_labels, R.id.control_close, R.id.control_fullscreen).forEach { id ->
                    val control = button(c, id)
                    assertEquals(View.VISIBLE, control.visibility)
                    assertFalse("Icon needs an accessible name", control.contentDescription.isNullOrBlank())
                }
            }
            Thread.sleep(100)
            instrumentation.runOnMainSync {}
            onActivity {
                point = centre(cards(it).last())
                assertTrue(cards(it).last().isMaximized)
            }
            pinch(point.first, point.second, 60f, 100f)
            onActivity { assertTrue("Spreading exited fullscreen", cards(it).last().isMaximized) }
            pinch(point.first, point.second, 120f, 70f)
            onActivity {
                val card = cards(it).last()
                assertTrue("Interactive inward pinch should zoom, not leave fullscreen", card.isMaximized)
                button(card, R.id.control_fullscreen).performClick()
                assertFalse(card.isMaximized)
                assertFalse("Restoring should return to move/resize mode", card.modelInstance.isInteractionMode)
                button(card, R.id.control_fullscreen).performClick()
                assertTrue(card.modelInstance.isInteractionMode)
                button(card, R.id.control_interact).performClick()
            }
            Thread.sleep(100)
            // Explicitly turning interaction off still permits the normal-mode restore gesture.
            pinch(point.first, point.second, 120f, 70f)
            onActivity { assertFalse("Normal inward pinch did not restore", cards(it).last().isMaximized) }
            onActivity { point = centre(cards(it).last()) }
            drag(point.first, point.second, 180f, 120f)
            onActivity { a ->
                val c = cards(a).last()
                val parent = c.parent as View
                assertTrue(
                    c.x >= 0 &&
                        c.y >= 0 &&
                        c.x + c.width <= parent.width + 1 &&
                        c.y + c.height <= parent.height + 1
                )
                c.maximize()
            }
            instrumentation.runOnMainSync {}
            // Verify labels against the actual camera and world transform after aspect change.
            onActivity { a ->
                val c = cards(a).last()
                val model = c.modelInstance
                model.onFrame()
                val vm = DoubleArray(16)
                val pm = DoubleArray(16)
                model.camera.getViewMatrix(vm)
                model.camera.getProjectionMatrix(pm)
                val tm = model.asset.engine.transformManager
                var verified = 0
                for (e in model.asset.entities) {
                    val raw = model.asset.getExtras(e) ?: continue
                    val text =
                        com.iftl.threedee.viewer.data.ExtrasLabelParser.labelText(raw) ?: continue
                    val m = FloatArray(16)
                    tm.getWorldTransform(tm.getInstance(e), m)
                    val out = FloatArray(2)
                    if (
                        LabelProjector.project(
                            vm,
                            pm,
                            model.lastViewportWidth,
                            model.lastViewportHeight,
                            m[12],
                            m[13],
                            m[14],
                            out,
                        )
                    ) {
                        val label = model.labelPositionsPx.first { it.text == text }
                        assertTrue(label.visible)
                        assertEquals(out[0], label.x, 0.1f)
                        assertEquals(out[1], label.y, 0.1f)
                        verified++
                    }
                }
                assertTrue("No labels checked", verified > 0)
                c.restoreFromMaximized()
            }
            onActivity {
                it.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            waitFor() {
                val v = it.findViewById<View>(R.id.containerLayer)
                v.width > v.height
            }
            onActivity {
                assertEquals(5, it.engineHost.modelCount)
                assertEquals(5, it.engineHost.ownedLightCount)
            }
            onActivity {
                it.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            waitFor() {
                val v = it.findViewById<View>(R.id.containerLayer)
                v.height > v.width
            }
            repeat(3) {
                onActivity { it.addModel(ModelCatalog.entries.first()) }
                waitFor() {
                    cards(it).size == 6 && it.findViewById<View>(R.id.fabAddModel).isEnabled
                }
                onActivity {
                    val cameraId = cards(it).last().modelInstance.camera.entity
                    button(cards(it).last(), R.id.control_close).performClick()
                    assertEquals(5, it.engineHost.ownedLightCount)
                    assertFalse(com.google.android.filament.EntityManager.get().isAlive(cameraId))
                }
            }
            val afterCyclesPss = android.os.Debug.getPss()
            // App re-entry keeps the workspace but resets fullscreen and interaction.
            onActivity {
                val card = cards(it).last()
                button(card, R.id.control_interact).performClick()
                button(card, R.id.control_fullscreen).performClick()
                assertTrue("An already interactive card must also expand", card.isMaximized)
            }
            var originalActivity: MainActivity? = null
            onActivity {
                originalActivity = it
                assertTrue(it.moveTaskToBack(true))
            }
            Thread.sleep(500)
            context.startActivity(
                android.content.Intent(context, MainActivity::class.java).addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
            waitFor { it === originalActivity }
            onActivity {
                assertTrue(cards(it).all { card ->
                    !card.isMaximized && !card.modelInstance.isInteractionMode &&
                        !button(card, R.id.control_interact).isSelected
                })
                button(cards(it).last(), R.id.control_fullscreen).performClick()
                assertTrue(cards(it).last().modelInstance.isInteractionMode)
            }
            onActivity { it.recreate() }
            Thread.sleep(300)
            waitFor() { cards(it).size == 5 && it.findViewById<View>(R.id.fabAddModel).isEnabled }
            onActivity {
                assertFalse(cards(it).last().isMaximized)
                assertFalse(cards(it).last().modelInstance.isInteractionMode)
                assertFalse(button(cards(it).last(), R.id.control_interact).isSelected)
                assertTrue(cards(it).last().modelInstance.labelsVisible)
                repeat(5) { _ -> button(cards(it).last(), R.id.control_close).performClick() }
                assertEquals(0, it.engineHost.modelCount)
                assertEquals(0, it.engineHost.ownedLightCount)
            }
            val sorted = synchronized(timings) { timings.sorted() }
            val report =
                "device=${android.os.Build.MODEL}; fiveModelPssKb=$fiveModelPss; afterCyclesPssKb=$afterCyclesPss; samples=${sorted.size}; uiFrameP50Ms=${sorted.getOrElse(sorted.size/2){0}/1e6}; uiFrameP95Ms=${sorted.getOrElse((sorted.size*0.95).toInt().coerceAtMost(sorted.lastIndex.coerceAtLeast(0))){0}/1e6}; over33ms=${sorted.count { it>33_333_333 }}"
            File(context.getExternalFilesDir(null), "verification.txt").writeText(report)
            android.util.Log.i("STUDIO_TEST", report)
        } catch (error: Throwable) {
            File(context.getExternalFilesDir(null), "test-failure.txt")
                .writeText(error.stackTraceToString())
            throw error
        } finally {
            onActivity { it.finish() }
            Thread.sleep(500)
            timingThread.quitSafely()
            prefs.edit().putString("cards", previous).commit()
        }
    }

    @Test
    fun immersiveFullscreenRestoresChromeAndGeometry() {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("workspace", 0)
        val previous = prefs.getString("cards", null)
        prefs.edit().clear().commit()
        instrumentation.startActivitySync(
            android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        fun settled(fullscreen: Boolean) {
            waitFor { a ->
                val root = a.findViewById<View>(R.id.main)
                val workspace = a.findViewById<View>(R.id.workspace)
                val card = cards(a).singleOrNull()
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(root)
                val statusVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                val navigationVisible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                if (fullscreen) card != null && card.isMaximized &&
                    card.height == workspace.height && workspace.height == root.height - root.paddingTop - root.paddingBottom &&
                    statusVisible == false && navigationVisible == false
                else a.findViewById<View>(R.id.workspaceHeader).isShown &&
                    a.findViewById<View>(R.id.workspaceFooter).isShown && statusVisible == true && navigationVisible == true &&
                    workspace.height < root.height - root.paddingTop - root.paddingBottom &&
                    (card == null || !card.isMaximized)
            }
        }
        try {
            onActivity { it.addModel(ModelCatalog.entries.first()) }
            waitFor { cards(it).size == 1 && it.findViewById<View>(R.id.fabAddModel).isEnabled }
            settled(false)
            var normal = IntArray(4)
            onActivity {
                val c = cards(it).single()
                normal = intArrayOf(c.x.toInt(), c.y.toInt(), c.width, c.height)
                button(c, R.id.control_fullscreen).performClick()
            }
            settled(true)
            onActivity {
                assertFalse(it.findViewById<View>(R.id.workspaceHeader).isShown)
                assertFalse(it.findViewById<View>(R.id.workspaceFooter).isShown)
                assertTrue(cards(it).single().modelInstance.isInteractionMode)
                it.onBackPressedDispatcher.onBackPressed()
            }
            settled(false)
            onActivity {
                val c = cards(it).single()
                val restored = intArrayOf(c.x.toInt(), c.y.toInt(), c.width, c.height)
                normal.indices.forEach { index -> assertTrue(kotlin.math.abs(normal[index] - restored[index]) <= 1) }
                assertFalse(c.modelInstance.isInteractionMode)
                c.maximize()
            }
            settled(true)
            onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            waitFor { it.findViewById<View>(R.id.workspace).width > it.findViewById<View>(R.id.workspace).height }
            settled(true)
            onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitFor { it.findViewById<View>(R.id.workspace).height > it.findViewById<View>(R.id.workspace).width }
            settled(true)
            onActivity { button(cards(it).single(), R.id.control_fullscreen).performClick() }
            settled(false)
            onActivity { cards(it).single().maximize() }
            settled(true)
            onActivity { button(cards(it).single(), R.id.control_close).performClick() }
            settled(false)
            onActivity {
                assertEquals(0, it.engineHost.modelCount)
                assertTrue(it.findViewById<View>(R.id.emptyState).isShown)
            }
        } finally {
            onActivity { it.finish() }
            Thread.sleep(500)
            prefs.edit().putString("cards", previous).commit()
        }
    }

    @Test
    fun workspaceCodecRejectsBrokenDataAndRoundTripsState() {
        assertTrue(WorkspaceStore.decode("broken").isEmpty())
        val state =
            com.iftl.threedee.viewer.state.CardState(
                "models/Bulb.glb",
                0.4f,
                0.6f,
                280f,
                300f,
                true,
                true,
                true,
                listOf(1f, 2f, 3f),
            )
        assertEquals(listOf(state), WorkspaceStore.decode(WorkspaceStore.encode(listOf(state))))
    }
}
