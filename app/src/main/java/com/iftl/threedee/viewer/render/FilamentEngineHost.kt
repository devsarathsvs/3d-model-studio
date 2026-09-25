package com.iftl.threedee.viewer.render

import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import com.iftl.threedee.viewer.data.ExtrasLabelParser
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One main-thread engine and swapchain. Idle workspaces schedule no rendering callbacks. */
class FilamentEngineHost(textureView: TextureView) {
    companion object {
        init {
            Utils.init()
        }
    }

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val entities = EntityManager.get()
    private val materials = UbershaderProvider(engine)
    private val assets = AssetLoader(engine, materials, entities)
    private val resources = ResourceLoader(engine)
    private val models = mutableListOf<ModelInstance>()
    private var loadingAsset: FilamentAsset? = null
    private var destroyed = false
    private var running = false
    private var scheduled = false
    private var dirty = true
    private var swapChain: SwapChain? = null
    private var canvasHeight = 1
    private val backgroundScene = engine.createScene()
    private val backgroundCameraEntity = entities.create()
    private val backgroundCamera = engine.createCamera(backgroundCameraEntity)
    private val backgroundView =
        engine.createView().apply {
            scene = backgroundScene
            camera = backgroundCamera
            setPostProcessingEnabled(false)
            setShadowingEnabled(false)
            antiAliasing = View.AntiAliasing.NONE
        }
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    var onFrameRendered: (() -> Unit)? = null
    var onCanvasResized: ((Int, Int) -> Unit)? = null
    // Diagnostic counters are read by instrumented tests; they are not an FPS claim.
    var submittedFrames = 0L
        private set

    val modelCount
        get() = models.size

    val ownedLightCount
        get() = engine.lightManager.componentCount

    private val frameCallback =
        Choreographer.FrameCallback { timestamp ->
            scheduled = false
            if (running && !destroyed && dirty) {
                if (uiHelper.isReadyToRender && swapChain != null) {
                    models.forEach { if (it.renderVisible) it.onFrame() }
                    if (renderer.beginFrame(swapChain!!, timestamp)) {
                        renderer.render(backgroundView)
                        models.forEach { if (it.renderVisible) renderer.render(it.view) }
                        renderer.endFrame()
                        submittedFrames++
                        dirty = false
                        onFrameRendered?.invoke()
                    }
                    if (dirty)
                        requestRender() // Filament may decline a frame while GPU work is pending.
                }
            }
        }

    init {
        renderer.setClearOptions(
            Renderer.ClearOptions().apply {
                clearColor = doubleArrayOf(0.028, 0.032, 0.043, 1.0)
                clear = true
                discard = false
            }
        )
        uiHelper.setRenderCallback(
            object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { engine.destroySwapChain(it) }
                    swapChain = engine.createSwapChain(surface)
                    requestRender()
                }

                override fun onDetachedFromSurface() {
                    swapChain?.let {
                        engine.destroySwapChain(it)
                        engine.flushAndWait()
                    }
                    swapChain = null
                }

                override fun onResized(width: Int, height: Int) {
                    canvasHeight = height
                    backgroundView.viewport = Viewport(0, 0, width, height)
                    onCanvasResized?.invoke(width, height)
                    requestRender()
                }
            }
        )
        uiHelper.attachTo(textureView)
    }

    fun canvasHeightPx() = canvasHeight

    /** Loads serially. Native asset creation stays on main; texture decoding is asynchronous. */
    suspend fun loadAssetBytes(bytes: ByteArray, onProgress: (Int) -> Unit): ModelInstance {
        check(!destroyed && loadingAsset == null)
        val asset =
            requireNotNull(assets.createAsset(ByteBuffer.wrap(bytes))) {
                "The model could not be opened."
            }
        loadingAsset = asset
        try {
            check(resources.asyncBeginLoad(asset)) { "The model resources could not be loaded." }
            withTimeout(60_000) {
                do {
                    resources.asyncUpdateLoad()
                    val progress = resources.asyncGetLoadProgress()
                    onProgress((progress * 100).toInt())
                    if (progress < 1f) delay(16)
                } while (progress < 1f)
            }
            val extras = asset.entities.map { it to asset.getExtras(it) }
            val labels =
                withContext(Dispatchers.Default) {
                    extras
                        .mapNotNull { (entity, json) ->
                            ExtrasLabelParser.labelText(json)?.let { entity to it }
                        }
                        .toMap()
                }
            asset.releaseSourceData()
            resources.evictResourceData()
            val model = ModelInstance(engine, asset, labels, ::requestRender)
            loadingAsset = null
            return model
        } catch (error: Throwable) {
            if (!destroyed && loadingAsset === asset) {
                resources.asyncCancelLoad()
                resources.evictResourceData()
                assets.destroyAsset(asset)
                loadingAsset = null
            }
            throw error
        }
    }

    fun addModel(model: ModelInstance) {
        models.add(model)
        requestRender()
    }

    fun removeModel(model: ModelInstance) {
        if (models.remove(model)) model.destroy(assets)
        requestRender()
    }

    fun bringToFront(model: ModelInstance) {
        if (models.remove(model)) models.add(model)
        requestRender()
    }

    fun requestRender() {
        if (destroyed) return
        dirty = true
        if (running && !scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun start() {
        running = true
        requestRender()
    }

    fun stop() {
        running = false
        scheduled = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        uiHelper.detach()
        loadingAsset?.let {
            resources.asyncCancelLoad()
            assets.destroyAsset(it)
            loadingAsset = null
        }
        models.forEach { it.destroy(assets) }
        models.clear()
        engine.destroyView(backgroundView)
        engine.destroyScene(backgroundScene)
        engine.destroyCameraComponent(backgroundCameraEntity)
        entities.destroy(backgroundCameraEntity)
        resources.destroy()
        assets.destroy()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroyRenderer(renderer)
        engine.destroy()
        onFrameRendered = null
        onCanvasResized = null
    }
}
