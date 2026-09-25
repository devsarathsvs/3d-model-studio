package com.iftl.threedee.viewer.render

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.Manipulator
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class LabelPoint(
    val text: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var visible: Boolean = false,
)

/** Owns one asset's scene, camera, light and labels. All native calls stay on the main thread. */
class ModelInstance(
    private val engine: Engine,
    val asset: FilamentAsset,
    labelTexts: Map<Int, String>,
    private val invalidate: () -> Unit,
) {
    private val entities = EntityManager.get()
    val scene = engine.createScene()
    private val skybox = Skybox.Builder().color(0.09f, 0.12f, 0.16f, 1f).build(engine)
    // Constant diffuse irradiance lifts dark surfaces without allocating an environment cubemap.
    private val ambient =
        IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.7f, 0.78f, 0.9f))
            .intensity(45_000f)
            .build(engine)
    val view = engine.createView()
    private val cameraEntity = entities.create()
    val camera = engine.createCamera(cameraEntity)
    private val light = entities.create()
    private val labelEntities = labelTexts.keys.toIntArray()
    val labelPositionsPx = labelTexts.values.map { LabelPoint(it) }
    private val worldMatrix = FloatArray(16)
    private val viewMatrix = DoubleArray(16)
    private val projectionMatrix = DoubleArray(16)
    private val point = FloatArray(2)
    private val eye = FloatArray(3)
    private val target = FloatArray(3)
    private val up = FloatArray(3)
    private val homeEye = FloatArray(3)
    private val homeTarget = asset.boundingBox.getCenter().copyOf()
    private val radius: Float
    private var manipulator: Manipulator
    private var cameraDirty = true
    private var labelsDirty = true
    private var destroyed = false
    var labelRevision = 0
        private set

    var isInteractionMode = false
        private set

    var labelsVisible = false
        private set

    var renderVisible = true
    var lastViewportLeft = 0
        private set

    var lastViewportTop = 0
        private set

    var lastViewportWidth = 1
        private set

    var lastViewportHeight = 1
        private set

    init {
        scene.skybox = skybox
        scene.indirectLight = ambient
        scene.addEntities(asset.entities)
        // One intentional key light. A second directional light is ignored by default by Filament.
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.96f, 0.90f)
            .intensity(320_000f)
            .direction(-0.5f, -1f, -0.6f)
            .castShadows(false)
            .build(engine, light)
        scene.addEntity(light)
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.OPAQUE
        view.setShadowingEnabled(false)
        view.setPostProcessingEnabled(false)
        view.antiAliasing = View.AntiAliasing.NONE
        view.setDynamicResolutionOptions(View.DynamicResolutionOptions().apply { enabled = false })
        view.setAmbientOcclusionOptions(View.AmbientOcclusionOptions().apply { enabled = false })
        view.setScreenSpaceRefractionEnabled(false)
        view.setDithering(View.Dithering.NONE)
        val half = asset.boundingBox.getHalfExtent()
        radius =
            sqrt(half[0] * half[0] + half[1] * half[1] + half[2] * half[2]).coerceAtLeast(0.05f)
        val distance = radius / sin(Math.toRadians(22.5)).toFloat() * 1.15f
        homeTarget.copyInto(homeEye)
        homeEye[2] += distance
        manipulator = makeManipulator(homeEye)
        updateCamera()
    }

    private fun makeManipulator(position: FloatArray) =
        Manipulator.Builder()
            .targetPosition(homeTarget[0], homeTarget[1], homeTarget[2])
            .orbitHomePosition(position[0], position[1], position[2])
            .upVector(0f, 1f, 0f)
            .viewport(lastViewportWidth, lastViewportHeight)
            .zoomSpeed(0.15f)
            .build(Manipulator.Mode.ORBIT)

    fun cameraPosition(): FloatArray {
        updateCamera()
        return eye.copyOf()
    }

    fun restoreCamera(position: FloatArray) {
        if (position.size != 3 || position.any { !it.isFinite() }) return
        if (position.contentEquals(homeTarget)) return
        manipulator = makeManipulator(position)
        cameraDirty = true
        invalidate()
    }

    fun setViewportPx(left: Int, top: Int, width: Int, height: Int, canvasHeight: Int) {
        if (destroyed) return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val sizeChanged = w != lastViewportWidth || h != lastViewportHeight
        lastViewportLeft = left
        lastViewportTop = top
        lastViewportWidth = w
        lastViewportHeight = h
        view.viewport = Viewport(left, canvasHeight - top - h, w, h)
        if (sizeChanged) {
            val aspect = w.toDouble() / h
            // Preserve fit along the smaller axis, including portrait fullscreen cards.
            val fov = Math.toDegrees(2 * atan(tan(Math.toRadians(22.5)) / minOf(1.0, aspect)))
            camera.setProjection(
                fov,
                aspect,
                (radius * 0.001).toDouble(),
                (radius * 1000).toDouble(),
                Camera.Fov.VERTICAL,
            )
            manipulator.setViewport(w, h)
            labelsDirty = true
        }
        invalidate()
    }

    fun setInteractionMode(enabled: Boolean) {
        manipulator.grabEnd()
        isInteractionMode = enabled
        invalidate()
    }

    fun setLabelsVisible(enabled: Boolean) {
        labelsVisible = enabled
        labelsDirty = true
        invalidate()
    }

    fun beginGrab(x: Int, y: Int) = manipulator.grabBegin(x, lastViewportHeight - y, false)

    fun updateGrab(x: Int, y: Int) {
        manipulator.grabUpdate(x, lastViewportHeight - y)
        cameraDirty = true
        invalidate()
    }

    fun endGrab() = manipulator.grabEnd()

    fun zoom(x: Int, y: Int, delta: Float) {
        manipulator.scroll(x, lastViewportHeight - y, delta)
        cameraDirty = true
        invalidate()
    }

    private fun updateCamera() {
        if (!cameraDirty) return
        manipulator.update(
            0f
        ) // ORBIT has no inertia: render only after an input/projection change.
        manipulator.getLookAt(eye, target, up)
        camera.lookAt(
            eye[0].toDouble(),
            eye[1].toDouble(),
            eye[2].toDouble(),
            target[0].toDouble(),
            target[1].toDouble(),
            target[2].toDouble(),
            up[0].toDouble(),
            up[1].toDouble(),
            up[2].toDouble(),
        )
        cameraDirty = false
        labelsDirty = true
    }

    fun onFrame() {
        if (destroyed) return
        updateCamera()
        if (!labelsVisible || !labelsDirty) return
        camera.getViewMatrix(viewMatrix)
        camera.getProjectionMatrix(projectionMatrix)
        val tm = engine.transformManager
        for (i in labelEntities.indices) {
            val label = labelPositionsPx[i]
            val instance = tm.getInstance(labelEntities[i])
            label.visible = false
            if (instance == 0) continue
            tm.getWorldTransform(instance, worldMatrix)
            label.visible =
                LabelProjector.project(
                    viewMatrix,
                    projectionMatrix,
                    lastViewportWidth,
                    lastViewportHeight,
                    worldMatrix[12],
                    worldMatrix[13],
                    worldMatrix[14],
                    point,
                )
            if (label.visible) {
                label.x = point[0]
                label.y = point[1]
            }
        }
        labelsDirty = false
        labelRevision++
    }

    fun hasLabels() = labelEntities.isNotEmpty()

    fun destroy(assetLoader: AssetLoader) {
        if (destroyed) return
        destroyed = true
        manipulator.grabEnd()
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroySkybox(skybox)
        engine.destroyIndirectLight(ambient)
        engine.destroyEntity(light)
        entities.destroy(light)
        engine.destroyCameraComponent(cameraEntity)
        entities.destroy(cameraEntity)
        assetLoader.destroyAsset(asset)
    }
}
