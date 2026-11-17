package com.enhikyy.girassol.ai

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.MyApplication
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado do modelo, observável pela UI de Configurações e pelo chat. */
sealed class ModelStatus {
    object Idle : ModelStatus()
    data class Copying(val progress: Float) : ModelStatus()
    object Loading : ModelStatus()
    data class Ready(val name: String) : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

/**
 * Copia o .gguf escolhido para filesDir/models e o carrega no [LlmEngine].
 * Cópia e carga rodam fora da thread principal; o estado é publicado em [status].
 */
object ModelManager {

    private const val TAG = "MODEL_LOADER"
    private const val MB = 1024L * 1024L

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    /** Importa o arquivo escolhido pelo usuário e carrega o modelo. */
    fun importAndLoad(app: MyApplication, uri: Uri) {
        CoroutineScope(Dispatchers.IO + AppLogger.globalCoroutineExceptionHandler).launch {
            try {
                val resolver = app.contentResolver
                val (rawName, sizeBytes) = queryNameAndSize(uri, resolver)
                val name = File(rawName).name
                if (!name.endsWith(".gguf", ignoreCase = true)) {
                    throw IllegalArgumentException("O arquivo precisa ser .gguf (recebido: $name)")
                }

                val modelsDir = File(app.filesDir, "models").apply { mkdirs() }
                val target = File(modelsDir, name)

                AppLogger.log("INFO", TAG, "Copiando $name (${sizeBytes / MB} MB) para ${target.absolutePath}")
                _status.value = ModelStatus.Copying(0f)
                val copyStart = System.nanoTime()
                copy(uri, target, resolver, sizeBytes)
                val copyMs = (System.nanoTime() - copyStart) / 1_000_000
                AppLogger.log("INFO", TAG, "Cópia concluída: ${target.length() / MB} MB em $copyMs ms")

                _status.value = ModelStatus.Loading
                app.ensureRealEngine()
                val engine = app.llmEngine
                if (engine.isLoaded) engine.close()
                // NativeLlmEngine.load registra tamanho e tempo de carga no AppLogger.
                val ok = engine.load(target.absolutePath)
                if (!ok) throw ModelLoadException("O motor não confirmou a carga de $name")

                _status.value = ModelStatus.Ready(name)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                AppLogger.log("ERROR", TAG, "Falha ao processar modelo: $msg", e)
                _status.value = ModelStatus.Error(msg)
            }
        }
    }

    /** Descarrega o modelo atual. */
    fun unload() {
        MyApplication.getInstance().llmEngine.close()
        _status.value = ModelStatus.Idle
    }

    private fun queryNameAndSize(uri: Uri, resolver: ContentResolver): Pair<String, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    return Pair(name ?: "modelo.gguf", size)
                }
            }
        return Pair("modelo.gguf", 0L)
    }

    /** Copia o stream em blocos de 1 MB, publicando o progresso. */
    private suspend fun copy(uri: Uri, target: File, resolver: ContentResolver, total: Long) =
        withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("Não foi possível abrir o arquivo escolhido")
            input.use { src ->
                target.outputStream().use { dst ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = src.read(buffer)
                        if (n == -1) break
                        dst.write(buffer, 0, n)
                        copied += n
                        if (total > 0) {
                            _status.value = ModelStatus.Copying((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
}
