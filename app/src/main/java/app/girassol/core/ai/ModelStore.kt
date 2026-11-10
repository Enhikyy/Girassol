package app.girassol.core.ai

import android.content.Context
import android.net.Uri
import app.girassol.core.data.SafeFiles
import java.io.File

data class LocalModel(val name: String, val path: String, val sizeBytes: Long)

class ModelStore(context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }

    fun list(): List<LocalModel> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("gguf", true) && it.length() > 8 }
        .map { LocalModel(it.name, it.absolutePath, it.length()) }
        .sortedByDescending { it.name }

    suspend fun import(context: Context, uri: Uri, onProgress: (Long) -> Unit): LocalModel {
        val rawName = Uri.decode(uri.lastPathSegment ?: "model.gguf").substringAfterLast('/')
        val safeName = rawName.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(100).removeSuffix(".")
        require(safeName.endsWith(".gguf", true)) { "Selecione um modelo .gguf" }
        val destination = File(directory, safeName)
        val temporary = File(directory, "$safeName.import")
        temporary.delete()
        try {
            val available = (directory.usableSpace - 512L * 1024L * 1024L).coerceAtLeast(1L)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use { output ->
                    SafeFiles.copyBounded(input, output, available, onProgress)
                }
            } ?: error("Não foi possível abrir o modelo")
            val header = temporary.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
            require(header.contentEquals(byteArrayOf(71, 71, 85, 70))) { "O arquivo não possui cabeçalho GGUF válido" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true); temporary.delete()
            }
            return LocalModel(destination.name, destination.absolutePath, destination.length())
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    fun delete(model: LocalModel) {
        val safe = File(directory, model.name).canonicalFile
        require(safe.parentFile == directory.canonicalFile) { "Modelo fora do diretório privado" }
        if (safe.exists()) safe.delete()
    }
}
