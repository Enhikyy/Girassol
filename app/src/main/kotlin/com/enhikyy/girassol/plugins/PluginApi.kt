package com.enhikyy.girassol.plugins

import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.data.WorldState

/**
 * API segura exposta aos plugins Lua/JS.
 *
 * É o único ponto de contacto entre o código do agente e o app: leitura do
 * estado do mundo, log e emissão de intenções. Os plugins não alteram WorldState.
 */
class PluginApi internal constructor(
    private val pluginName: String,
    private val sink: (String) -> Unit,
    private val budget: Budget,
) {
    internal class Budget(val maxActions: Int = 64) {
        var actions: Int = 0
        fun spend(): Boolean = ++actions <= maxActions
    }

    internal var deadlineNanos: Long = Long.MAX_VALUE

    internal fun checkDeadline() {
        if (System.nanoTime() > deadlineNanos) {
            throw IllegalStateException("tempo limite de ${PluginRunner.TIMEOUT_MS} ms excedido")
        }
    }

    fun log(message: String) {
        checkDeadline()
        val text = message.take(MAX_LINE)
        sink(text)
        AppLogger.log("INFO", "CODER", "[$pluginName] $text")
    }

    fun warn(message: String) {
        checkDeadline()
        val text = message.take(MAX_LINE)
        sink("WARN: $text")
        AppLogger.log("WARN", "CODER", "[$pluginName] $text")
    }

    fun population(): Int { checkDeadline(); return WorldState.agents.value.size }
    fun tick(): Long { checkDeadline(); return WorldState.tick.value }
    fun generation(): Int { checkDeadline(); return WorldState.generation.value }
    fun food(): Float { checkDeadline(); return WorldState.commons.value.food }
    fun materials(): Float { checkDeadline(); return WorldState.commons.value.materials }
    fun knowledge(): Float { checkDeadline(); return WorldState.commons.value.knowledge }
    fun cohesion(): Float { checkDeadline(); return WorldState.commons.value.cohesion }
    fun housing(): Int { checkDeadline(); return WorldState.commons.value.housing }

    fun roles(): String {
        checkDeadline()
        return WorldState.agents.value.joinToString(",") { it.role }
    }

    fun worldLine(): String {
        checkDeadline()
        val commons = WorldState.commons.value
        return "pop=${WorldState.agents.value.size} tick=${WorldState.tick.value} " +
            "ger=${WorldState.generation.value} " +
            "comida=%.1f materiais=%.1f saber=%.1f coesao=%.2f moradia=%d".format(
                commons.food, commons.materials, commons.knowledge, commons.cohesion, commons.housing
            )
    }

    /** Emite apenas uma intenção auditável; nunca executa mutações diretas. */
    fun emit(action: String): Boolean {
        checkDeadline()
        val text = action.trim().take(MAX_LINE)
        if (text.isEmpty()) return false
        if (!budget.spend()) {
            sink("orçamento de ações esgotado; '$text' descartada")
            return false
        }
        sink("AÇÃO: $text")
        AppLogger.log("INFO", "CODER", "[$pluginName] ação pedida: $text")
        return true
    }

    fun hash(text: String): Int {
        checkDeadline()
        return text.fold(7) { acc, c -> acc * 31 + c.code }
    }

    fun worldJson(): String {
        checkDeadline()
        return org.json.JSONObject()
            .put("population", population())
            .put("tick", tick())
            .put("generation", generation())
            .put("food", food().toDouble())
            .put("materials", materials().toDouble())
            .put("knowledge", knowledge().toDouble())
            .put("cohesion", cohesion().toDouble())
            .put("housing", housing())
            .toString()
    }

    private companion object {
        const val MAX_LINE = 4_096
    }
}
