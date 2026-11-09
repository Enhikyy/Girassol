package dev.labforge.app.core.term

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val command: String,
    val exitCode: Int,
    val output: String,
    val durationMs: Long,
    val timedOut: Boolean,
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * Terminal confined to the app sandbox. Android has no bash/python/git binaries,
 * so we run /system/bin/sh (toybox) and expose JGit for version control.
 */
class ShellSession(private val workspace: File) {

    private val maxOutput = 160_000

    suspend fun run(command: String, timeoutSec: Long = 60): ShellResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (command.isBlank()) {
            return@withContext ShellResult(command, 0, "(comando vazio)", 0, false)
        }
        workspace.mkdirs()
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workspace)
            .redirectErrorStream(true)
        val env = pb.environment()
        env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
        env["HOME"] = workspace.absolutePath
        env["TMPDIR"] = workspace.absolutePath
        env["TERM"] = "dumb"

        val process = try {
            pb.start()
        } catch (t: Throwable) {
            return@withContext ShellResult(command, 127, "falha ao iniciar shell: ${t.message}", 0, false)
        }

        val sb = StringBuilder()
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { process.destroyForcibly() }
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (sb.length < maxOutput) sb.append(line).append('\n')
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            // Close the pipe so a grandchild that inherited stdout cannot keep the
            // reader thread blocked forever (a leaked thread per timed-out command).
            runCatching { process.inputStream.close() }
            reader.join(1200)
            return@withContext ShellResult(
                command, 124,
                sb.toString() + "\n[timeout após ${timeoutSec}s — processo encerrado]",
                System.currentTimeMillis() - started, true,
            )
        }
        reader.join(1500)
        runCatching { process.inputStream.close() }
        val text = sb.toString().let {
            if (it.length > maxOutput) it.take(maxOutput) + "\n[saída truncada]"
            else if (it.isBlank()) "(sem saída)" else it
        }
        ShellResult(command, process.exitValue(), text, System.currentTimeMillis() - started, false)
    }
}
