package com.iftl.threedee.viewer

import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.iftl.threedee.viewer.data.CatalogEntry
import com.iftl.threedee.viewer.data.ModelCatalog
import com.iftl.threedee.viewer.render.FilamentEngineHost
import com.iftl.threedee.viewer.render.ViewportCoverage
import com.iftl.threedee.viewer.state.CardState
import com.iftl.threedee.viewer.state.WorkspaceStore
import com.iftl.threedee.viewer.ui.ModelHelpDialog
import com.iftl.threedee.viewer.ui.ModelContainerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    internal lateinit var engineHost: FilamentEngineHost
        private set

    private lateinit var layer: FrameLayout
    private lateinit var addButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var store: WorkspaceStore
    private val cards = mutableListOf<ModelContainerView>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var immersive = false
    private var loading = false
    private var restoring = false
    private val pendingRestore = mutableListOf<CardState>()
    private val back =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                cards.lastOrNull { it.isMaximized }?.restoreFromMaximized()
                saveWorkspace()
                refreshWorkspace()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars =
                insets.getInsets(
                    if (immersive) WindowInsetsCompat.Type.displayCutout()
                    else WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        store = WorkspaceStore(this)
        layer = findViewById(R.id.containerLayer)
        addButton = findViewById(R.id.fabAddModel)
        progress = findViewById(R.id.loadProgress)
        engineHost = FilamentEngineHost(findViewById<TextureView>(R.id.textureView))
        engineHost.onFrameRendered = { cards.forEach { it.refreshLabels() } }
        engineHost.onCanvasResized = { w, h ->
            layer.post {
                cards.forEach { it.reflowTo(w, h) }
                refreshWorkspace()
            }
        }
        onBackPressedDispatcher.addCallback(this, back)
        addButton.setOnClickListener { showModelPicker() }
        findViewById<View>(R.id.arrangeButton).setOnClickListener { arrangeCards() }
        findViewById<View>(R.id.helpButton).setOnClickListener { ModelHelpDialog.show(this) }
        pendingRestore.addAll(
            store.load()
                .filter { saved -> ModelCatalog.entries.any { it.assetPath == saved.assetPath } }
                .map { it.copy(maximized = false, interaction = false) }
        )
        layer.post { restoreWorkspace() }
        refreshWorkspace()
    }

    private fun showModelPicker() {
        val dialog = BottomSheetDialog(this)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(28))
            }
        content.addView(
            TextView(this).apply {
                setText(R.string.picker_title)
                textSize = 22f
                setTextColor(0xffeef3f8.toInt())
                setPadding(0, dp(8), 0, dp(8))
            }
        )
        content.addView(
            TextView(this).apply {
                setText(R.string.picker_subtitle)
                textSize = 12f
                setTextColor(0xff91a2b8.toInt())
                setPadding(0, 0, 0, dp(16))
            }
        )
        val subtitles = resources.getStringArray(R.array.model_subtitles)
        ModelCatalog.entries.forEachIndexed { i, entry ->
            val row =
                MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    )
                    .apply {
                        text = getString(R.string.picker_row, entry.displayName, subtitles[i])
                        isAllCaps = false
                        textSize = 13f
                        gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                        cornerRadius = dp(12)
                        setPadding(dp(18), 0, dp(18), 0)
                        contentDescription = getString(R.string.add_named, entry.displayName)
                        setOnClickListener {
                            dialog.dismiss()
                            addModel(entry)
                        }
                    }
            content.addView(row, LinearLayout.LayoutParams(-1, dp(72)))
        }
        dialog.setContentView(content)
        dialog.show()
    }

    private fun restoreWorkspace() {
        if (pendingRestore.isEmpty()) return
        restoring = true
        scope.launch {
            setLoading(true, getString(R.string.restoring))
            while (pendingRestore.isNotEmpty()) {
                val saved = pendingRestore.first()
                val entry = ModelCatalog.entries.first { it.assetPath == saved.assetPath }
                loadCard(entry, saved)
                pendingRestore.removeAt(0)
            }
            restoring = false
            setLoading(false)
            saveWorkspace()
        }
    }

    internal fun addModel(entry: CatalogEntry) {
        if (loading) return
        scope.launch {
            setLoading(true, getString(R.string.loading_named, entry.displayName))
            loadCard(entry, null)
            setLoading(false)
            saveWorkspace()
        }
    }

    private suspend fun loadCard(entry: CatalogEntry, saved: CardState?) {
        try {
            val bytes =
                withContext(Dispatchers.IO) { assets.open(entry.assetPath).use { it.readBytes() } }
            val model =
                engineHost.loadAssetBytes(bytes) { percent ->
                    progress.setProgressCompat(percent, true)
                }
            engineHost.addModel(model)
            val card =
                ModelContainerView(
                    this,
                    entry,
                    model,
                    engineHost::canvasHeightPx,
                    onClose = ::removeCard,
                    onFocus = ::focusCard,
                    onChanged = ::refreshWorkspace,
                    onGestureEnd = ::saveWorkspace,
                )
            layer.addView(card, FrameLayout.LayoutParams(-2, -2))
            cards.add(card)
            val density = resources.displayMetrics.density
            val w = minOf((280 * density).toInt(), layer.width)
            val h = minOf((300 * density).toInt(), layer.height)
            val offset = ((cards.size - 1) % 5 * 18 * density).toInt()
            card.placeInitial(
                ((layer.width - w) / 2 + offset).coerceAtMost(layer.width - w),
                offset.coerceAtMost(layer.height - h),
                w,
                h,
            )
            if (saved != null) card.restore(saved)
            focusCard(card)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            android.util.Log.e("ModelStudio", "Unable to load ${entry.assetPath}", error)
            Snackbar.make(
                    layer,
                    getString(R.string.load_failed, entry.displayName),
                    Snackbar.LENGTH_LONG,
                )
                .setAction(R.string.retry) { addModel(entry) }
                .show()
        }
    }

    /** Fits as many cards as possible in a grid; overflowing rows remain draggable in bounds. */
    internal fun arrangeCards() {
        if (cards.isEmpty() || layer.width == 0 || layer.height == 0) return
        val d = resources.displayMetrics.density
        val gap = (4 * d).toInt()
        val minimum = (174 * d).toInt()
        val columns = ((layer.width - gap) / (minimum + gap)).coerceIn(1, cards.size)
        val rows = (cards.size + columns - 1) / columns
        val w = (layer.width - gap * (columns + 1)) / columns
        val h = ((layer.height - gap * (rows + 1)) / rows).coerceAtLeast(minimum)
        cards.forEachIndexed { index, card ->
            card.restoreFromMaximized()
            card.placeInitial(
                gap + (index % columns) * (w + gap),
                gap + (index / columns) * (h + gap),
                w,
                h,
            )
        }
        saveWorkspace()
    }

    private fun setLoading(value: Boolean, message: String = "") {
        loading = value
        addButton.isEnabled = !value
        progress.visibility = if (immersive) View.GONE else if (value) View.VISIBLE else View.INVISIBLE
        if (value) {
            progress.progress = 0
            findViewById<TextView>(R.id.statusText).text = message
        } else refreshWorkspace()
    }

    private fun removeCard(card: ModelContainerView) {
        layer.removeView(card)
        cards.remove(card)
        engineHost.removeModel(card.modelInstance)
        cards.lastOrNull()?.let(::focusCard)
        refreshWorkspace()
        saveWorkspace()
    }

    private fun focusCard(card: ModelContainerView) {
        if (!cards.contains(card)) return
        layer.bringChildToFront(card)
        cards.remove(card)
        cards.add(card)
        engineHost.bringToFront(card.modelInstance)
        cards.forEach { it.setFocused(it === card) }
        refreshWorkspace()
    }

    private fun updateFullscreenChrome() {
        val fullscreen = cards.any { it.isMaximized }
        if (immersive == fullscreen) return
        immersive = fullscreen
        val visibility = if (fullscreen) View.GONE else View.VISIBLE
        findViewById<View>(R.id.workspaceHeader).visibility = visibility
        findViewById<View>(R.id.workspaceFooter).visibility = visibility
        progress.visibility = if (fullscreen) View.GONE else if (loading) View.VISIBLE else View.INVISIBLE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
        // Keep controls clear of cutouts; transient system bars overlay without resizing the model.
        ViewCompat.requestApplyInsets(findViewById(R.id.main))
    }

    private fun refreshWorkspace() {
        if (!::engineHost.isInitialized) return
        updateFullscreenChrome()
        for (i in cards.indices) {
            val card = cards[i]
            card.updateCornerMasks()
            card.modelInstance.renderVisible =
                !ViewportCoverage.isCovered(
                    card.contentRect,
                    cards.drop(i + 1).map { it.screenRect },
                )
        }
        findViewById<View>(R.id.emptyState).visibility =
            if (cards.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.modelCount).text =
            resources.getQuantityString(R.plurals.model_count, cards.size, cards.size)
        if (!loading)
            findViewById<TextView>(R.id.statusText).text =
                if (cards.isEmpty()) getString(R.string.ready) else getString(R.string.focus_hint)
        back.isEnabled = cards.any { it.isMaximized }
        engineHost.requestRender()
    }

    private fun saveWorkspace() {
        if (!::store.isInitialized) return
        store.save(
            cards.map { it.snapshot() } + if (restoring) pendingRestore.toList() else emptyList()
        )
    }

    override fun onResume() {
        super.onResume()
        // Returning to the app always starts with movable cards and an unselected Interact icon.
        if (cards.isNotEmpty()) {
            cards.forEach { it.resetToArrangeMode() }
            saveWorkspace()
            refreshWorkspace()
        }
        engineHost.start()
    }

    override fun onPause() {
        saveWorkspace()
        engineHost.stop()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        engineHost.destroy()
        super.onDestroy()
    }
}
