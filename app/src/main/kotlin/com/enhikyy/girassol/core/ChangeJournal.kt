package com.enhikyy.girassol.core

import com.enhikyy.girassol.AppLogger
import org.json.JSONObject
import java.io.File

/**
 * Diário append-only de todas as auto-modificações.
 *
 * Cada linha é um objeto JSON com quem alterou, o quê, o valor anterior e o
 * novo. O ficheiro nunca é reescrito: serve de auditoria e de fonte para
 * reconstruir o histórico de uma decisão autónoma.
 */
class ChangeJournal(file: File) {
    private val f: File = file.also { it.parentFile?.mkdirs() }

    @Synchronized
    fun record(author: String, target: String, before: String, after: String) {
        val line = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("author", author)
            .put("target", target)
            .put("before", before)
            .put("after", after)
            .toString()
        f.appendText(line + "\n")
        AppLogger.log("INFO", "JOURNAL", "$author alterou $target")
    }

    /** Lê o diário completo (linha a linha). Útil no ecrã de diagnóstico. */
    fun readAll(): List<JSONObject> {
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }
    }
}
