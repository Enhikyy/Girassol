package dev.labforge.app.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rotating app-private diagnostic log shared by UI, native engine and agent. */
class DiagnosticLogStore(
    private val directory: File,
    private val maxBytes: Long = 2L * 1024L * 1024L,
) {
    private val lock = Any()
    private val active = File(directory, "girassol.log")
    private val previous = File(directory, "girassol.log.1")
    private val older = File(directory, "girassol.log.2")
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun append(level: String, component: String, message: String): String = synchronized(lock) {
        directory.mkdirs()
        val line = "[" + formatter.format(Date()) + "][" + level.uppercase(Locale.ROOT).take(8) +
            "][" + component.take(32) + "][" + Thread.currentThread().name + "] " +
            message.replace('\u0000', ' ').take(12_000)
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (active.exists() && active.length() + bytes.size > maxBytes) {
            older.delete()
            if (previous.exists()) previous.renameTo(older)
            if (active.exists()) active.renameTo(previous)
        }
        active.appendBytes(bytes)
        line
    }

    fun tail(maxLines: Int = 1200): String = synchronized(lock) {
        listOf(older, previous, active)
            .filter { it.isFile }
            .flatMap { file -> runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList()) }
            .takeLast(maxLines.coerceIn(1, 10_000))
            .joinToString("\n")
    }

    fun clear() = synchronized(lock) {
        active.delete()
        previous.delete()
        older.delete()
    }
}

object AppLog {
    @Volatile private var store: DiagnosticLogStore? = null

    fun initialize(context: Context) {
        if (store == null) synchronized(this) {
            if (store == null) store = DiagnosticLogStore(File(context.filesDir, "logs"))
        }
    }

    fun write(level: String, component: String, message: String) {
        store?.append(level, component, message)
        val priority = when (level.uppercase(Locale.ROOT)) {
            "ERROR", "FATAL" -> Log.ERROR
            "WARN", "WARNING" -> Log.WARN
            "DEBUG" -> Log.DEBUG
            else -> Log.INFO
        }
        Log.println(priority, "Girassol/" + component.take(32), message.take(3000))
    }

    fun info(component: String, message: String) = write("INFO", component, message)
    fun warn(component: String, message: String) = write("WARN", component, message)
    fun error(component: String, message: String, error: Throwable? = null) {
        write("ERROR", component, if (error == null) message else message + "\n" + Log.getStackTraceString(error))
    }

    fun tail(maxLines: Int = 1200): String = store?.tail(maxLines).orEmpty()
    fun clear() = store?.clear()
}
