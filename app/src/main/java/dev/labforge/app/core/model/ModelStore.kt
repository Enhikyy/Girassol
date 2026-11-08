package dev.labforge.app.core.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.labforge.app.core.gguf.Gguf
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class AssetKind(val folder: String, val label: String) {
    MODEL("models", "Modelo GGUF"),
    IMATRIX("imatrix", "Importance matrix"),
    DATASET("datasets", "Dataset"),
    OUTPUT("output", "Saída"),
}

data class ModelEntry(
    val file: File,
    val meta: Gguf.Model?,
    val error: String? = null,
) {
    val name: String get() = meta?.name ?: file.nameWithoutExtension
    val sizeBytes: Long get() = file.length()
}

/**
 * App-private workspace. Everything the agent and the labs touch lives under
 * files/labforge so the terminal, git and the model engine share one sandbox.
 */
class ModelStore(private val context: Context) {

    val root: File = File(context.filesDir, "labforge").apply { mkdirs() }
    val modelsDir: File = dir(AssetKind.MODEL)
    val imatrixDir: File = dir(AssetKind.IMATRIX)
    val datasetsDir: File = dir(AssetKind.DATASET)
    val outputsDir: File = dir(AssetKind.OUTPUT)
    val agentDir: File = File(root, "agent").apply { mkdirs() }
    val runsDir: File = File(root, "runs").apply { mkdirs() }
    val researchDir: File = File(root, "research").apply { mkdirs() }

    private val cache = ConcurrentHashMap<String, Pair<Long, Gguf.Model?>>()

    private fun dir(kind: AssetKind) = File(root, kind.folder).apply { mkdirs() }

    fun uniqueFile(kind: AssetKind, displayName: String): File {
        val safe = displayName.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim().ifBlank { "asset" }
        var f = File(dir(kind), safe)
        var i = 1
        while (f.exists()) {
            val base = safe.substringBeforeLast('.', safe)
            val ext = safe.substringAfterLast('.', "")
            f = File(dir(kind), if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext")
            i++
        }
        return f
    }

    fun copyFromUri(uri: Uri, kind: AssetKind, onProgress: (Float) -> Unit = {}): File {
        val name = displayName(uri) ?: "import-${System.currentTimeMillis()}"
        val target = uniqueFile(kind, name)
        val total = sizeOf(uri)
        if (total > 0) {
            val free = target.parentFile?.usableSpace ?: 0L
            require(free > total + (64L shl 20)) {
                "espaço insuficiente: ${total / (1 shl 20)} MB necessários, ${free / (1 shl 20)} MB livres"
            }
        }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo" }
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    copied += n
                    if (total > 0) onProgress(copied.toFloat() / total)
                }
            }
        }
        onProgress(1f)
        return target
    }

    fun writeText(kind: AssetKind, name: String, text: String): File {
        val f = uniqueFile(kind, name)
        f.writeText(text)
        return f
    }

    fun models(): List<File> = modelsDir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun imatrices(): List<File> = imatrixDir.listFiles { f ->
        f.isFile && (f.extension.equals("gguf", true) || f.extension.equals("dat", true) ||
            f.name.contains("imatrix", true))
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun datasets(): List<File> = datasetsDir.listFiles { f ->
        f.isFile && f.extension.lowercase() in setOf("txt", "jsonl", "json", "md", "csv")
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun outputs(): List<File> = outputsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun readModel(file: File): ModelEntry {
        val key = file.absolutePath
        val stamp = file.lastModified()
        val cached = cache[key]
        if (cached != null && cached.first == stamp) return ModelEntry(file, cached.second)
        return try {
            val parsed = Gguf.read(file)
            cache[key] = stamp to parsed
            ModelEntry(file, parsed)
        } catch (t: Throwable) {
            cache[key] = stamp to null
            ModelEntry(file, null, t.message ?: "GGUF inválido")
        }
    }

    fun delete(file: File): Boolean {
        cache.remove(file.absolutePath)
        return file.delete()
    }

    fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    fun sizeOf(uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L } ?: 0L
    }

    fun workspace(): File = File(root, "workspace").apply { mkdirs() }
}
