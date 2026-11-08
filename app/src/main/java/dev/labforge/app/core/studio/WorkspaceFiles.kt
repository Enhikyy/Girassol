package dev.labforge.app.core.studio

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Private workspace path and ZIP import helpers.
 * Paths are canonicalized under the workspace root. Extraction is streamed and bounded.
 */
object WorkspaceFiles {
    const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
    const val MAX_FILE_BYTES = 16L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
    const val MAX_ENTRIES = 1_000

    fun resolve(root: File, relativePath: String): File {
        require(relativePath.isNotBlank() && relativePath.length <= 240) { "Caminho vazio ou longo demais" }
        val normalized = relativePath.replace('\\', '/')
        require(!normalized.startsWith('/') && !normalized.contains(':')) { "Caminho absoluto não permitido" }
        val segments = normalized.split('/')
        require(segments.size <= 20 && segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Caminho relativo inválido"
        }
        require(segments.all { it.matches(Regex("[A-Za-z0-9_. -]{1,100}")) }) {
            "Nome de arquivo contém caracteres não permitidos"
        }
        val base = root.canonicalFile
        val target = File(base, normalized).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Caminho fora do workspace" }
        return target
    }

    fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        require(maxBytes >= 0) { "Limite de cópia inválido" }
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Arquivo excede o limite permitido de ${maxBytes / (1024 * 1024)} MiB" }
            output.write(buffer, 0, count)
        }
        return total
    }

    /** Extracts an archive into a new imports subfolder and returns its directory. */
    fun importZip(input: InputStream, workspaceRoot: File): File {
        val root = workspaceRoot.canonicalFile
        val target = resolve(root, "imports/project-${System.nanoTime()}")
        require(!target.exists()) { "Destino de importação já existe" }
        target.mkdirs()
        var entries = 0
        var totalBytes = 0L
        val seen = HashSet<String>()
        var extractedFiles = 0
        try {
            ZipInputStream(BoundedInputStream(input, MAX_ARCHIVE_BYTES).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ENTRIES) { "ZIP excede o limite de $MAX_ENTRIES entradas" }
                    val name = entry.name
                    require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') && !name.contains(':')) {
                        "Entrada ZIP com caminho inválido"
                    }
                    val trimmed = name.removeSuffix("/")
                    require(trimmed.isNotBlank()) { "Entrada ZIP vazia" }
                    val destination = resolve(target, trimmed)
                    val key = trimmed.lowercase()
                    require(seen.add(key)) { "ZIP contém caminhos duplicados" }
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        require(entry.size < 0 || entry.size <= MAX_FILE_BYTES) { "Arquivo ZIP individual excede 16 MiB" }
                        destination.parentFile?.mkdirs()
                        var fileBytes = 0L
                        destination.outputStream().buffered().use { out ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                require(fileBytes <= MAX_FILE_BYTES) { "Arquivo ZIP individual excede 16 MiB" }
                                require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "ZIP descompactado excede 64 MiB" }
                                out.write(buffer, 0, count)
                            }
                        }
                        extractedFiles++
                    }
                    zip.closeEntry()
                }
            }
            require(extractedFiles > 0) { "ZIP não contém arquivos" }
            return target
        } catch (failure: Throwable) {
            target.deleteRecursively()
            throw failure
        }
    }

    private class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var consumed = 0L
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) add(it) }
        private fun add(size: Int) {
            consumed += size
            require(consumed <= limit) { "ZIP compactado excede ${limit / (1024 * 1024)} MiB" }
        }
    }

    fun findSourceFile(root: File): String? {
        val files = root.walkTopDown().filter { it.isFile && !it.isHidden }.take(MAX_ENTRIES + 1).toList()
        require(files.size <= MAX_ENTRIES) { "Projeto importado tem arquivos demais para indexar" }
        val selected = files.firstOrNull { it.name == "MainActivity.kt" }
            ?: files.firstOrNull { it.name.endsWith(".kt") }
            ?: files.firstOrNull { it.name.endsWith(".java") || it.name.endsWith(".cpp") || it.name.endsWith(".h") }
            ?: files.firstOrNull { it.name.endsWith(".gradle.kts") }
            ?: files.firstOrNull()
            ?: return null
        return selected.relativeTo(root).invariantSeparatorsPath
    }
}
