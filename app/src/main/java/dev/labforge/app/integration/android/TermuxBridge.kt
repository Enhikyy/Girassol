package dev.labforge.app.integration.android

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import dev.labforge.app.core.term.ShellResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// RUN_COMMAND addresses the Termux application's prefix, not Girassol's filesDir.
@SuppressLint("SdCardPath")
object TermuxBridge {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val HOME = "/data/data/com.termux/files/home"
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Bundle>>()

    fun projectPath(value: String): String {
        val path = value.trim().ifBlank { "$HOME/girassol" }
        require(path.startsWith("/") || path.startsWith("~/")) { "Use um caminho absoluto dentro do Termux" }
        return if (path.startsWith("~/")) "$HOME/${path.removePrefix("~/")}" else path
    }

    internal fun commandIntent(workdir: String, command: String, callback: PendingIntent): Intent =
        Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }

    internal fun complete(id: String, result: Bundle) { pending[id]?.complete(result) }

    internal fun output(command: String, result: Bundle, duration: Long): ShellResult {
        val internalError = result.getInt("err", -1)
        val code = if (internalError != -1) 125 else result.getInt("exitCode", 125)
        val text = listOf(result.getString("stdout").orEmpty(), result.getString("stderr").orEmpty(),
            result.getString("errmsg").orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
        return ShellResult(command, code, text.ifBlank { "(sem saída)" }, duration, false)
    }

    suspend fun run(context: Context, workdir: String, command: String, timeoutSeconds: Long = 1800): ShellResult {
        require(command.isNotBlank()) { "Comando vazio" }
        require(command.toByteArray().size <= 64_000) { "Comando excede 64 KB" }
        require(timeoutSeconds in 1..3600) { "Timeout deve estar entre 1 e 3600 segundos" }
        val directory = projectPath(workdir)
        check(context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            "Autorize executar comandos no Termux em Ajustes. No Termux, configure allow-external-apps=true em ~/.termux/termux.properties."
        }
        val id = UUID.randomUUID().toString()
        val started = System.currentTimeMillis()
        val deferred = CompletableDeferred<Bundle>()
        val callbackIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            data = Uri.parse("girassol://termux/$id")
            putExtra("execution_id", id)
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, 0, callbackIntent, flags)
        pending[id] = deferred
        try {
            checkNotNull(context.startService(commandIntent(directory, command, callback))) { "Serviço Termux indisponível" }
            val result = withTimeoutOrNull(timeoutSeconds * 1000) { deferred.await() }
                ?: return ShellResult(command, 124, "Tempo esgotado aguardando Termux. O processo externo pode continuar em execução.",
                    System.currentTimeMillis() - started, true)
            return output(command, result, System.currentTimeMillis() - started)
        } finally {
            pending.remove(id)
            deferred.cancel()
            callback.cancel()
        }
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("execution_id") ?: return
        val result = intent.getBundleExtra("result") ?: return
        TermuxBridge.complete(id, result)
    }
}
