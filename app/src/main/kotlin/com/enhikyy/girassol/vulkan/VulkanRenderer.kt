package com.enhikyy.girassol.vulkan

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.enhikyy.girassol.data.WorldState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Ponte Kotlin para o renderizador Vulkan nativo.
 *
 * Esta classe é defensiva por desenho: a biblioteca nativa pode não estar
 * presente (por exemplo, num build sem NDK). Nesse caso [isAvailable] fica
 * falso, o loop de render nunca arranca e a UI cai para o mundo desenhado em
 * Canvas — em vez de lançar `UnsatisfiedLinkError`.
 */
class VulkanRenderer private constructor(
    private val textureView: TextureView,
) : TextureView.SurfaceTextureListener, LifecycleEventObserver {

    companion object {
        private const val TAG = "VulkanRenderer"

        private val libraryLoaded: Boolean by lazy {
            runCatching { System.loadLibrary("girassol-vulkan") }
                .onFailure { Log.w(TAG, "Biblioteca nativa indisponível: ${it.message}") }
                .isSuccess
        }

        private var instance: VulkanRenderer? = null

        /** Verdadeiro quando o binário nativo foi carregado com sucesso. */
        fun isAvailable(): Boolean = libraryLoaded

        fun getInstance(): VulkanRenderer? = instance

        fun create(textureView: TextureView, owner: LifecycleOwner): VulkanRenderer? {
            if (!libraryLoaded) return null
            if (instance == null) {
                instance = VulkanRenderer(textureView).also {
                    owner.lifecycle.addObserver(it)
                    it.attach()
                }
            }
            return instance
        }

        fun destroy() {
            instance?.shutdown()
            instance = null
        }
    }

    // ---- Métodos nativos -------------------------------------------------
    private external fun nativeInitialize(
        surface: Surface,
        width: Int,
        height: Int,
        enableRayTracing: Boolean,
        enablePostProcess: Boolean,
        renderScale: Float,
    ): Boolean

    private external fun nativeShutdown()
    private external fun nativeRenderFrame(deltaTime: Float)
    private external fun nativeOnSurfaceChanged(width: Int, height: Int)
    private external fun nativeSetHeadlessMode(headless: Boolean)

    private external fun nativeSetKodakParams(
        grainAmount: Float,
        halationAmount: Float,
        bloomThreshold: Float,
        bloomAmount: Float,
        aberrationAmount: Float,
        vignetteAmount: Float,
        intensity: Float,
        time: Float,
    )

    private external fun nativeUpdateAgents(
        xArray: FloatArray,
        yArray: FloatArray,
        zArray: FloatArray,
        energyArray: FloatArray,
        socialArray: FloatArray,
        focusArray: FloatArray,
        innieArray: BooleanArray,
        count: Int,
    )

    // ---- Estado ----------------------------------------------------------
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var renderJob: Job? = null
    private val initialized = AtomicBoolean(false)
    private val headless = AtomicBoolean(false)
    private var lastFrameNanos = System.nanoTime()
    private var kodakTime = 0f

    private fun attach() {
        textureView.surfaceTextureListener = this
        if (textureView.isAvailable) {
            initialize(textureView.surfaceTexture, textureView.width, textureView.height)
        }
    }

    private fun initialize(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        surfaceTexture ?: return
        if (initialized.get() || width <= 0 || height <= 0) return

        val surface = runCatching { Surface(surfaceTexture) }.getOrNull() ?: return
        val ok = runCatching {
            nativeInitialize(
                surface = surface,
                width = width,
                height = height,
                enableRayTracing = false,
                enablePostProcess = true,
                renderScale = 0.5f,
            )
        }.getOrElse {
            Log.e(TAG, "nativeInitialize falhou", it)
            false
        }

        if (ok) {
            initialized.set(true)
            updateKodakParams()
            startRenderLoop()
        }
        surface.release()
    }

    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = scope.launch {
            while (isActive && initialized.get()) {
                if (headless.get()) {
                    // Headless: mantém o loop lógico, sem submeter frames.
                    delay(500)
                    continue
                }
                val now = System.nanoTime()
                val dt = (now - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = now
                kodakTime += dt.coerceIn(0f, 0.1f)

                runCatching {
                    pushAgents()
                    updateKodakParams()
                    nativeRenderFrame(dt)
                }
                delay(16)
            }
        }
    }

    /** Envia o estado atual do mundo para o renderizador. */
    fun pushAgentUpdate() {
        if (!initialized.get()) return
        runCatching { pushAgents() }
    }

    private fun pushAgents() {
        val agents = WorldState.agents.value
        if (agents.isEmpty()) return
        val count = agents.size
        val x = FloatArray(count) { agents[it].x }
        val y = FloatArray(count) { agents[it].drives.focus }
        val z = FloatArray(count) { agents[it].z }
        val energy = FloatArray(count) { agents[it].drives.energy }
        val social = FloatArray(count) { agents[it].drives.social }
        val focus = FloatArray(count) { agents[it].drives.focus }
        val innie = BooleanArray(count) { agents[it].isInnie }
        nativeUpdateAgents(x, y, z, energy, social, focus, innie, count)
    }

    private fun updateKodakParams() {
        nativeSetKodakParams(
            grainAmount = 0.08f,
            halationAmount = 0.15f,
            bloomThreshold = 0.85f,
            bloomAmount = 0.25f,
            aberrationAmount = 2.0f,
            vignetteAmount = 0.4f,
            intensity = 0.8f,
            time = kodakTime,
        )
    }

    /** Liga/desliga o pipeline gráfico quando a tela apaga/acede. */
    fun setHeadlessMode(on: Boolean) {
        if (!initialized.get()) return
        headless.set(on)
        runCatching { nativeSetHeadlessMode(on) }
    }

    fun onSurfaceResized(width: Int, height: Int) {
        if (!initialized.get() || width <= 0 || height <= 0) return
        runCatching { nativeOnSurfaceChanged(width, height) }
    }

    fun shutdown() {
        initialized.set(false)
        renderJob?.cancel()
        runCatching { nativeShutdown() }
    }

    // ---- TextureView -----------------------------------------------------
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        initialize(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        onSurfaceResized(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        shutdown()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    // ---- Lifecycle -------------------------------------------------------
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> setHeadlessMode(true)
            Lifecycle.Event.ON_RESUME -> setHeadlessMode(false)
            else -> Unit
        }
    }

    /** Estado de exibição do mundo, para a UI decidir se mostra o overlay. */
    val renderingNow: Boolean get() = initialized.get() && !headless.get()
}
