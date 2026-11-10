package app.girassol.core.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream

object SafeFiles {
    fun resolve(root: File, relative: String): File {
        require(relative.isNotBlank() && relative.length <= 320) { "Caminho inválido" }
        require(!relative.startsWith('/') && !relative.contains(':') && !relative.contains('\u0000')) { "Caminho absoluto não permitido" }
        val base = root.canonicalFile
        val target = File(base, relative).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Caminho fora do workspace" }
        return target
    }

    fun copyBounded(input: InputStream, output: OutputStream, maximumBytes: Long, onBytes: (Long) -> Unit = {}): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximumBytes) { "Arquivo excede ${maximumBytes / 1024 / 1024} MiB" }
            output.write(buffer, 0, count)
            onBytes(total)
        }
        return total
    }

    fun writeAtomic(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }
}
