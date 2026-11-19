package com.enhikyy.girassol.plugins

import android.content.Context
import com.enhikyy.girassol.AppLogger
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Plugins Lua/JS, editor, versões locais e execução serializada. */
object PluginStore {
    private const val FOLDER = "plugins"
    private const val HISTORY_FOLDER = ".history"
    private const val SEED_MARKER = ".seeded"

    data class PluginFile(val name: String, val extension: String, val lastModified: Long) {
        val id: String get() = PluginRules.pluginId(name, extension)
    }

    data class PluginVersion(val fileName: String, val timestamp: Long, val sizeBytes: Long)

    private val _plugins = MutableStateFlow<List<PluginFile>>(emptyList())
    val plugins: StateFlow<List<PluginFile>> = _plugins.asStateFlow()
    private val _running = MutableStateFlow<String?>(null)
    val running: StateFlow<String?> = _running.asStateFlow()
    private val _lastResult = MutableStateFlow<PluginRunner.Result?>(null)
    val lastResult: StateFlow<PluginRunner.Result?> = _lastResult.asStateFlow()

    private val runLock = Mutex()
    private val storageLock = Mutex()
    private val historySequence = AtomicLong(0L)

    @Volatile
    private var folder: File? = null

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        storageLock.withLock {
            val dir = File(context.filesDir, FOLDER)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Não foi possível criar a pasta de plugins")
            }
            if (!dir.isDirectory) throw IllegalStateException("O caminho de plugins não é uma pasta")
            folder = dir

            val marker = File(dir, SEED_MARKER)
            if (!marker.exists()) {
                EXAMPLES.forEach { example ->
                    val target = File(dir, "${example.name}.${example.extension}")
                    if (!target.exists()) writeAtomically(target, example.source)
                }
                if (!marker.createNewFile() && !marker.exists()) {
                    throw IllegalStateException("Não foi possível finalizar a instalação dos exemplos")
                }
                AppLogger.log("INFO", "CODER", "Exemplos de plugins criados em ${dir.path}")
            }
            reload(dir)
        }
    }

    suspend fun create(name: String, extension: String): Boolean = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!PluginRules.isValidName(name) || !PluginRules.isValidExtension(extension)) {
                return@withLock false
            }
            val dir = requireFolder()
            val target = File(dir, PluginRules.pluginId(name, extension))
            if (target.exists()) return@withLock false
            writeAtomically(target, template(extension))
            AppLogger.log("INFO", "CODER", "Plugin criado: ${target.name}")
            reload(dir)
            true
        }
    }

    suspend fun delete(plugin: PluginFile): Boolean = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!isValidPlugin(plugin) || _running.value == plugin.id) return@withLock false
            val dir = requireFolder()
            val target = pluginFile(dir, plugin)
            if (!target.isFile || !target.delete()) return@withLock false
            historyDirectory(dir, plugin).deleteRecursively()
            AppLogger.log("INFO", "CODER", "Plugin excluído: ${plugin.id}")
            reload(dir)
            true
        }
    }

    suspend fun readSource(plugin: PluginFile): String? = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!isValidPlugin(plugin)) return@withLock null
            val target = pluginFile(requireFolder(), plugin)
            if (!target.isFile || target.length() > PluginRules.MAX_SOURCE_CHARS * 4L) {
                return@withLock null
            }
            target.readText(Charsets.UTF_8).takeIf(PluginRules::isValidSource)
        }
    }

    /** Salva atomicamente e guarda a versão anterior antes de substituir o arquivo. */
    suspend fun save(plugin: PluginFile, source: String): Boolean = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!isValidPlugin(plugin) || _running.value == plugin.id || !PluginRules.isValidSource(source)) {
                return@withLock false
            }
            val dir = requireFolder()
            val target = pluginFile(dir, plugin)
            if (!target.isFile || target.length() > PluginRules.MAX_SOURCE_CHARS * 4L) {
                return@withLock false
            }
            val previous = target.readText(Charsets.UTF_8)
            if (!PluginRules.isValidSource(previous)) return@withLock false
            if (previous == source) return@withLock true
            saveSnapshot(dir, plugin, previous)
            writeAtomically(target, source)
            AppLogger.log("INFO", "CODER", "Plugin salvo: ${plugin.id}")
            reload(dir)
            true
        }
    }

    suspend fun history(plugin: PluginFile): List<PluginVersion> = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!isValidPlugin(plugin)) return@withLock emptyList()
            historyDirectory(requireFolder(), plugin).listFiles().orEmpty()
                .filter { it.isFile && PluginRules.isValidVersionFileName(it.name) }
                .mapNotNull { file ->
                    val timestamp = file.name.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
                    PluginVersion(file.name, timestamp, file.length())
                }
                .sortedWith(compareByDescending<PluginVersion> { it.timestamp }.thenByDescending { it.fileName })
        }
    }

    /** Restaura uma versão como conteúdo atual; a versão ativa também é preservada. */
    suspend fun restore(plugin: PluginFile, version: PluginVersion): Boolean = withContext(Dispatchers.IO) {
        storageLock.withLock {
            if (!isValidPlugin(plugin) || _running.value == plugin.id ||
                !PluginRules.isValidVersionFileName(version.fileName)
            ) return@withLock false

            val dir = requireFolder()
            val sourceFile = File(historyDirectory(dir, plugin), version.fileName)
            if (!sourceFile.isFile || sourceFile.length() > PluginRules.MAX_SOURCE_CHARS * 4L) {
                return@withLock false
            }
            val source = sourceFile.readText(Charsets.UTF_8)
            if (!PluginRules.isValidSource(source)) return@withLock false

            val target = pluginFile(dir, plugin)
            if (!target.isFile || target.length() > PluginRules.MAX_SOURCE_CHARS * 4L) {
                return@withLock false
            }
            val current = target.readText(Charsets.UTF_8)
            if (!PluginRules.isValidSource(current)) return@withLock false
            if (current != source) {
                saveSnapshot(dir, plugin, current)
                writeAtomically(target, source)
                AppLogger.log("INFO", "CODER", "Versão restaurada: ${plugin.id} <- ${version.fileName}")
                reload(dir)
            }
            true
        }
    }

    /** Executa uma fotografia do código salvo; alterações posteriores não mudam a execução em curso. */
    suspend fun run(plugin: PluginFile) {
        runLock.withLock {
            _running.value = plugin.id
            try {
                val source = withContext(Dispatchers.IO) {
                    storageLock.withLock {
                        if (!isValidPlugin(plugin)) throw IllegalArgumentException("Plugin inválido")
                        val file = pluginFile(requireFolder(), plugin)
                        if (!file.isFile) throw IllegalStateException("O arquivo ${plugin.id} não existe")
                        if (file.length() > PluginRules.MAX_SOURCE_CHARS * 4L) {
                            throw IllegalStateException("Código excede o limite de leitura")
                        }
                        file.readText(Charsets.UTF_8).also {
                            if (!PluginRules.isValidSource(it)) {
                                throw IllegalStateException("Código inválido ou grande demais")
                            }
                        }
                    }
                }
                _lastResult.value = PluginRunner.run(plugin.id, plugin.extension, source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                AppLogger.log("ERROR", "CODER", "Falha ao preparar ${plugin.id}: $message")
                _lastResult.value = PluginRunner.Result(plugin.id, false, emptyList(), message, 0L)
            } finally {
                _running.value = null
            }
        }
    }

    private fun isValidPlugin(plugin: PluginFile): Boolean =
        PluginRules.isValidName(plugin.name) && PluginRules.isValidExtension(plugin.extension)

    private fun pluginFile(dir: File, plugin: PluginFile): File =
        File(dir, PluginRules.pluginId(plugin.name, plugin.extension))

    private fun historyDirectory(dir: File, plugin: PluginFile): File =
        File(File(dir, HISTORY_FOLDER), plugin.id)

    private fun saveSnapshot(dir: File, plugin: PluginFile, source: String) {
        if (!PluginRules.isValidSource(source)) return
        val history = historyDirectory(dir, plugin)
        if (!history.exists() && !history.mkdirs()) {
            throw IllegalStateException("Não foi possível criar o histórico do plugin")
        }
        val timestamp = System.currentTimeMillis()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(12)
        val sequence = historySequence.getAndIncrement().toString().padStart(6, '0')
        val snapshot = File(history, "$timestamp-$sequence-$digest.src")
        if (!snapshot.exists()) writeAtomically(snapshot, source)

        history.listFiles().orEmpty()
            .filter { it.isFile && PluginRules.isValidVersionFileName(it.name) }
            .sortedByDescending { it.name }
            .drop(PluginRules.HISTORY_LIMIT)
            .forEach { it.delete() }
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: throw IllegalStateException("Destino sem pasta")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Não foi possível criar ${parent.path}")
        }
        val temporary = File(parent, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: FileAlreadyExistsException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun requireFolder(): File = folder
        ?: throw IllegalStateException("PluginStore ainda não foi iniciado")

    private fun reload(dir: File) {
        _plugins.value = dir.listFiles().orEmpty()
            .filter {
                it.isFile && it.extension in setOf("lua", "js") &&
                    PluginRules.isValidName(it.nameWithoutExtension)
            }
            .map { PluginFile(it.nameWithoutExtension, it.extension, it.lastModified()) }
            .sortedWith(compareBy<PluginFile> { it.name }.thenBy { it.extension })
    }

    private fun template(extension: String): String = when (extension) {
        "lua" -> "-- Plugin Lua\ngirassol.log(girassol.worldLine())\n"
        else -> "// Plugin JS\ngirassol.log(girassol.worldLine());\n"
    }

    private data class Example(val name: String, val extension: String, val source: String)

    private val EXAMPLES = listOf(
        Example("hello_world", "lua", "girassol.log(\"olá do plugin Lua\")\ngirassol.log(girassol.worldLine())\n"),
        Example("agent_logger", "js", "girassol.log(\"habitantes: \" + girassol.population());\ngirassol.log(girassol.worldJson());\n"),
        Example(
            "auto_trainer",
            "lua",
            "local pop = girassol.population()\nif pop > 0 then\n  girassol.emit(\"treinar: \" .. girassol.roles())\nend\ngirassol.log(\"papéis: \" .. girassol.roles())\n"
        ),
    )
}
