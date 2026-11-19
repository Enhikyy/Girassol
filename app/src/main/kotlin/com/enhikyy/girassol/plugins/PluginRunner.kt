package com.enhikyy.girassol.plugins

import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.core.AutonomyController
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaThread
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.ZeroArgFunction
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Executa Lua/JS isolado: sem acesso a classes Java, ficheiros ou rede,
 * com limite de instruções, prazo de execução e orçamento de ações.
 */
object PluginRunner {
    const val TIMEOUT_MS = 5_000L
    private const val LUA_HOOK_INTERVAL = 10_000
    private const val LUA_MAX_INSTRUCTIONS = 5_000_000

    data class Result(
        val name: String,
        val ok: Boolean,
        val lines: List<String>,
        val error: String?,
        val millis: Long,
    ) {
        fun render(): String = buildString {
            append("[$name] ")
            append(if (ok) "concluído" else "falhou")
            append(" em $millis ms")
            lines.forEach { append('\n').append(it) }
            if (error != null) append('\n').append("erro: ").append(error)
        }
    }

    suspend fun run(name: String, extension: String, source: String): Result =
        withContext(Dispatchers.Default) {
            AutonomyController.awaitRunning()
            val lines = ArrayList<String>()
            val started = System.nanoTime()
            val api = PluginApi(
                pluginName = name,
                sink = { line -> synchronized(lines) { lines += line } },
                budget = PluginApi.Budget(),
            )
            api.deadlineNanos = started + TIMEOUT_MS * 1_000_000L
            AppLogger.log("INFO", "CODER", "Plugin iniciado: $name ($extension)")

            val failure = runCatching {
                when (extension) {
                    "lua" -> runLua(source, api)
                    "js" -> runJs(source, api)
                    else -> throw IllegalArgumentException("extensão desconhecida: $extension")
                }
            }.exceptionOrNull()

            val millis = (System.nanoTime() - started) / 1_000_000L
            val message = failure?.let { it.message ?: it.javaClass.simpleName }
            if (failure != null) {
                AppLogger.log("ERROR", "CODER", "Plugin $name falhou após $millis ms: $message")
            } else {
                AppLogger.log("INFO", "CODER", "Plugin $name concluído em $millis ms")
            }
            Result(name, failure == null, synchronized(lines) { lines.toList() }, message, millis)
        }

    private fun runLua(source: String, api: PluginApi) {
        val globals = Globals()
        LoadState.install(globals)
        LuaC.install(globals)
        globals.load(BaseLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(MathLib())
        globals.load(DebugLib())

        val setHook = globals.get("debug").get("sethook")
        globals.set("debug", LuaValue.NIL)
        globals.set("dofile", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)
        globals.set("require", LuaValue.NIL)
        globals.set("io", LuaValue.NIL)
        globals.set("os", LuaValue.NIL)
        globals.set("package", LuaValue.NIL)
        globals.set("luajava", LuaValue.NIL)
        globals.set("girassol", luaApi(api))

        val chunk = globals.load(StringReader(source), "plugin.lua")
        val thread = LuaThread(globals, chunk)
        var instructions = 0
        val hook = object : ZeroArgFunction() {
            override fun call(): LuaValue {
                instructions += LUA_HOOK_INTERVAL
                if (instructions > LUA_MAX_INSTRUCTIONS) {
                    throw LuaExecutionLimitError("limite de $LUA_MAX_INSTRUCTIONS instruções Lua excedido")
                }
                try {
                    api.checkDeadline()
                } catch (error: IllegalStateException) {
                    throw LuaExecutionLimitError(error.message ?: "tempo limite Lua excedido")
                }
                return LuaValue.NIL
            }
        }
        setHook.invoke(
            LuaValue.varargsOf(
                arrayOf(
                    thread,
                    hook,
                    LuaValue.valueOf(""),
                    LuaValue.valueOf(LUA_HOOK_INTERVAL),
                ),
            ),
        )

        val outcome = thread.resume(LuaValue.NIL)
        if (!outcome.arg1().toboolean()) {
            throw IllegalStateException(outcome.arg(2).tojstring().ifBlank { "execução Lua interrompida" })
        }
    }

    private class LuaExecutionLimitError(message: String) : Error(message)

    private fun luaApi(api: PluginApi): LuaTable {
        val table = LuaTable()
        table.fn1("log") { api.log(it.tojstring()); LuaValue.NIL }
        table.fn1("warn") { api.warn(it.tojstring()); LuaValue.NIL }
        table.fn1("emit") { LuaValue.valueOf(api.emit(it.tojstring())) }
        table.fn1("hash") { LuaValue.valueOf(api.hash(it.tojstring()).toDouble()) }
        table.fn0("worldLine") { LuaValue.valueOf(api.worldLine()) }
        table.fn0("worldJson") { LuaValue.valueOf(api.worldJson()) }
        table.fn0("roles") { LuaValue.valueOf(api.roles()) }
        table.fn0("population") { LuaValue.valueOf(api.population().toDouble()) }
        table.fn0("tick") { LuaValue.valueOf(api.tick().toDouble()) }
        table.fn0("generation") { LuaValue.valueOf(api.generation().toDouble()) }
        table.fn0("food") { LuaValue.valueOf(api.food().toDouble()) }
        table.fn0("materials") { LuaValue.valueOf(api.materials().toDouble()) }
        table.fn0("knowledge") { LuaValue.valueOf(api.knowledge().toDouble()) }
        table.fn0("cohesion") { LuaValue.valueOf(api.cohesion().toDouble()) }
        table.fn0("housing") { LuaValue.valueOf(api.housing().toDouble()) }
        return table
    }

    private fun LuaTable.fn0(name: String, body: () -> LuaValue) {
        set(name, object : ZeroArgFunction() {
            override fun call(): LuaValue = body()
        })
    }

    private fun LuaTable.fn1(name: String, body: (LuaValue) -> LuaValue) {
        set(name, object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = body(arg)
        })
    }

    private fun runJs(source: String, api: PluginApi) {
        val factory = object : ContextFactory() {
            override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                api.checkDeadline()
            }
        }
        val context = factory.enterContext()
        try {
            context.optimizationLevel = -1
            context.setClassShutter { false }
            context.instructionObserverThreshold = 10_000
            val scope = context.initSafeStandardObjects()
            val girassol = context.newObject(scope)
            jsFn(girassol, "log") { args -> api.log(arg(args, 0)); Undefined.instance }
            jsFn(girassol, "warn") { args -> api.warn(arg(args, 0)); Undefined.instance }
            jsFn(girassol, "emit") { args -> api.emit(arg(args, 0)) }
            jsFn(girassol, "hash") { args -> api.hash(arg(args, 0)).toDouble() }
            jsFn(girassol, "worldLine") { _ -> api.worldLine() }
            jsFn(girassol, "worldJson") { _ -> api.worldJson() }
            jsFn(girassol, "roles") { _ -> api.roles() }
            jsFn(girassol, "population") { _ -> api.population().toDouble() }
            jsFn(girassol, "tick") { _ -> api.tick().toDouble() }
            jsFn(girassol, "generation") { _ -> api.generation().toDouble() }
            jsFn(girassol, "food") { _ -> api.food().toDouble() }
            jsFn(girassol, "materials") { _ -> api.materials().toDouble() }
            jsFn(girassol, "knowledge") { _ -> api.knowledge().toDouble() }
            jsFn(girassol, "cohesion") { _ -> api.cohesion().toDouble() }
            jsFn(girassol, "housing") { _ -> api.housing().toDouble() }
            ScriptableObject.putProperty(scope, "girassol", girassol)
            context.evaluateString(scope, source, "plugin.js", 1, null)
        } finally {
            Context.exit()
        }
    }

    private fun jsFn(target: Scriptable, name: String, body: (Array<Any?>) -> Any?) {
        ScriptableObject.putProperty(target, name, JsFunction(body))
    }

    private fun arg(args: Array<Any?>, index: Int): String =
        args.getOrNull(index)?.let { Context.toString(it) } ?: ""

    private class JsFunction(private val body: (Array<Any?>) -> Any?) : BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<Any?>
        ): Any? = body(args)
    }
}
