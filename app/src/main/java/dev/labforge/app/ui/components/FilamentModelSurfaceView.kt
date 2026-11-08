package dev.labforge.app.ui.components

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Filament-backed glTF 2.0 SurfaceView.
 * The viewer is recreated after detach because Filament's UiHelper/SwapChain are
 * tied to the lifetime of the Android surface. Model and external resource files
 * remain in the app-private cache and are bounded before being mapped.
 */
class FilamentModelSurfaceView(context: Context) : SurfaceView(context) {

    private var viewer: ModelViewer? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: kotlinx.coroutines.Job? = null
    private var currentFile: File? = null
    private var loadedPath: String? = null
    private val channels = mutableListOf<FileChannel>()
    private var mappedResourceBytes = 0L
    var onLoadError: ((String) -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val active = viewer
            if (isAttachedToWindow && active != null) {
                runCatching { active.render(frameTimeNanos) }
                    .onFailure { onLoadError?.invoke("Falha no renderizador 3D: ${it.message}") }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun show(file: File) {
        val canonical = file.canonicalFile
        if (!canonical.isFile) {
            onLoadError?.invoke("Arquivo 3D não encontrado.")
            return
        }
        currentFile = canonical
        if (loadedPath == canonical.path || viewer == null) return
        loadedPath = canonical.path
        loadJob?.cancel()
        closeChannels()
        loadJob = scope.launch {
            try {
                val model = viewer ?: return@launch
                when (canonical.extension.lowercase()) {
                    "glb" -> {
                        require(canonical.length() in 20L..MAX_MODEL_BYTES) {
                            "GLB deve ter entre 20 bytes e 64 MiB."
                        }
                        val channel = withContext(Dispatchers.IO) {
                            FileChannel.open(canonical.toPath(), StandardOpenOption.READ)
                        }
                        channels += channel
                        val buffer = withContext(Dispatchers.IO) {
                            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        }
                        model.loadModelGlb(buffer)
                    }
                    "gltf" -> {
                        require(canonical.length() in 2L..MAX_GLTf_JSON_BYTES) {
                            "Arquivo glTF JSON deve ter até 4 MiB."
                        }
                        val json = withContext(Dispatchers.IO) { canonical.readBytes() }
                        val parent = canonical.parentFile.canonicalFile
                        mappedResourceBytes = 0L
                        model.loadModelGltf(ByteBuffer.wrap(json)) { uri ->
                            val parsed = URI(uri)
                            require(parsed.scheme == null && !parsed.isAbsolute) {
                                "URI externa de glTF não permitida: $uri"
                            }
                            val relative = parsed.path ?: uri
                            val resource = File(parent, relative).canonicalFile
                            require(resource.path.startsWith(parent.path + File.separator)) {
                                "Recurso glTF tentou sair do diretório do pacote."
                            }
                            require(resource.isFile && resource.length() in 1L..MAX_RESOURCE_BYTES) {
                                "Recurso glTF ausente ou acima de 16 MiB: $relative"
                            }
                            mappedResourceBytes += resource.length()
                            require(mappedResourceBytes <= MAX_TOTAL_RESOURCE_BYTES) {
                                "Recursos externos glTF excedem 48 MiB."
                            }
                            val channel = FileChannel.open(resource.toPath(), StandardOpenOption.READ)
                            channels += channel
                            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        }
                    }
                    else -> error("Formato 3D não suportado pelo Filament: .${canonical.extension}")
                }
                if (model.asset == null) error("Filament não conseguiu interpretar o arquivo ou encontrar seus recursos.")
                model.transformToUnitCube()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                loadedPath = null
                onLoadError?.invoke(failure.message ?: "Falha ao carregar modelo glTF.")
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        Utils.init()
        viewer = ModelViewer(this).also { model -> setOnTouchListener(model) }
        loadedPath = null
        currentFile?.let(::show)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        loadJob?.cancel()
        scope.cancel()
        viewer?.let { active -> runCatching { active.destroy() } }
        viewer = null
        loadedPath = null
        closeChannels()
        super.onDetachedFromWindow()
    }

    private fun closeChannels() {
        channels.forEach { runCatching { it.close() } }
        channels.clear()
        mappedResourceBytes = 0L
    }

    companion object {
        private const val MAX_MODEL_BYTES = 64L * 1024L * 1024L
        private const val MAX_GLTf_JSON_BYTES = 4L * 1024L * 1024L
        private const val MAX_RESOURCE_BYTES = 16L * 1024L * 1024L
        private const val MAX_TOTAL_RESOURCE_BYTES = 48L * 1024L * 1024L
    }
}
