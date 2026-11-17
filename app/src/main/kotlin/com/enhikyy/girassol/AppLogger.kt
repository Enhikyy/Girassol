package com.enhikyy.girassol

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.*
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Sistema de log do Girassol.
 *
 * Regras que tornam este logger seguro para a UI:
 *
 * 1. **Nunca escreve em disco na thread que chama.** O chamador só formata uma
 *    entrada e a entrega a uma fila limitada; uma única thread de escrita é
 *    dona do ficheiro, que fica aberto e só é esvaziado periodicamente.
 * 2. **Fila limitada.** Se o produtor for mais rápido do que o disco (por
 *    exemplo, um log dentro de um lambda de desenho), as entradas excedentes
 *    são contadas e resumidas — nunca cresce sem limite, nunca mata o processo.
 * 3. **Deduplicação.** A mesma mensagem repetida dentro de [DEDUPE_WINDOW_MS]
 *    é contabilizada em vez de escrita; quando a janela expira, o resumo
 *    ("repetido Nx") acompanha a linha seguinte. É isto que impede que um log
 *    por frame inunde o ficheiro.
 * 4. **Nada de trabalho por frame.** Para eventos raros existe [logOnce]: o
 *    mesmo par (chave) só é registado uma vez por processo.
 *
 * Ficheiros em `Android/data/com.enhikyy.girassol/files/logs`:
 * - `log_<stamp>.txt` — log estruturado do app.
 * - `logcat_<stamp>.txt` — saída bruta do logcat filtrada pelo pid, que inclui
 *   crashes nativos, RenderThread, AGSL e libc.
 */
object AppLogger {

    /** Tag fixa do diagnóstico (filtro usado no terminal). */
    const val TAG = "GIRASSOL_DEBUG"

    private const val QUEUE_CAPACITY = 1_024
    private const val PRE_BOOT_LIMIT = 64
    private const val POLL_MS = 500L
    private const val DEDUPE_WINDOW_MS = 4_000L
    private const val DEDUPE_MAP_LIMIT = 512
    private const val MAX_FILE_BYTES = 4L * 1024L * 1024L
    private const val WATCHDOG_PERIOD_MS = 3_000L
    private const val WATCHDOG_STALL_MS = 3_000L
    private const val MEMORY_PERIOD_MS = 15_000L

    private class Entry(
        val level: String,
        val tag: String,
        val msg: String,
        val throwable: Throwable?,
        val wallMs: Long,
        val threadName: String,
    )

    /** Estado de deduplicação de uma mensagem. */
    private class Dedupe(var lastMs: Long, var repeats: Int)

    private val queue = ArrayBlockingQueue<Entry>(QUEUE_CAPACITY)
    private val dropped = AtomicLong()
    private val dedupe = ConcurrentHashMap<String, Dedupe>()
    private val onceKeys = ConcurrentHashMap.newKeySet<String>()

    /** Linhas registadas antes de [init] (quando ainda não há ficheiro). */
    private val preBoot = arrayOfNulls<Entry>(PRE_BOOT_LIMIT)
    private var preBootIndex = 0

    /** Formatadores são usados apenas pela thread de escrita (não são thread-safe). */
    private val writerFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val syncFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private lateinit var logDir: File

    @Volatile
    private var currentFile: File? = null

    @Volatile
    private var logcatFile: File? = null

    @Volatile
    private var booted = false

    /** Handler global para coroutines: toda falha assíncrona fica registada. */
    lateinit var globalCoroutineExceptionHandler: CoroutineExceptionHandler
        private set

    // ------------------------------------------------------------------ ciclo de vida

    fun init(app: Application) {
        if (booted) return
        booted = true

        val dir = File(app.getExternalFilesDir(null), "logs").apply { mkdirs() }
        logDir = dir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        currentFile = File(dir, "log_$stamp.txt")
        logcatFile = File(dir, "logcat_$stamp.txt")

        startWriter()
        startLogcatCapture()
        startMainThreadWatchdog()
        startMemoryMonitor(app)

        // Descarrega o que foi registado antes de o logger ter ficheiro.
        val buffered = preBootIndex.coerceAtMost(PRE_BOOT_LIMIT)
        for (i in 0 until buffered) {
            preBoot[i]?.let { enqueue(it) }
        }

        log(
            "INFO",
            "BOOT",
            "Device=${Build.MANUFACTURER} ${Build.MODEL} | Android=${Build.VERSION.SDK_INT} " +
                "(${Build.VERSION.RELEASE}) | SDK=${Build.VERSION.SDK_INT} | " +
                "ABI=${Build.SUPPORTED_ABIS.joinToString()} | RAM livre=${freeMb(app)}MB | " +
                "logs=${currentFile?.absolutePath}",
        )

        // Crashes não tratados vão para o ficheiro de forma síncrona.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            logSync("FATAL", "CRASH", "Thread=${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }

        globalCoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
            log("ERROR", "COROUTINE", "Falha em coroutine", error)
        }

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private fun life(activity: Activity, what: String) =
                log("INFO", "LIFECYCLE", "${activity.javaClass.simpleName} $what")

            override fun onActivityCreated(activity: Activity, state: Bundle?) =
                life(activity, "onCreate")

            override fun onActivityStarted(activity: Activity) = life(activity, "onStart")
            override fun onActivityResumed(activity: Activity) = life(activity, "onResume")
            override fun onActivityPaused(activity: Activity) = life(activity, "onPause")
            override fun onActivityStopped(activity: Activity) = life(activity, "onStop")
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) = life(activity, "onDestroy")
        })
    }

    // ------------------------------------------------------------------ API pública

    /**
     * Regista uma linha. Barato e não bloqueante: formata e entrega à fila.
     *
     * Pode ser chamado de qualquer thread, incluindo a principal — mas por
     * higiene não deve ser chamado dentro de um lambda de desenho, já que
     * aconteceria a cada frame. Ver nota de deduplicação na classe.
     */
    fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        if (!booted) {
            rememberPreBoot(level, tag, msg, t)
            return
        }

        val now = SystemClock.uptimeMillis()
        val withSuffix = applyDedupe(level, tag, msg, now)
        if (withSuffix == null) return

        enqueue(
            Entry(
                level = level,
                tag = tag,
                msg = withSuffix,
                throwable = t,
                wallMs = System.currentTimeMillis(),
                threadName = Thread.currentThread().name,
            ),
        )
    }

    private fun enqueue(entry: Entry) {
        if (!queue.offer(entry)) {
            // Disco mais lento do que o produtor: conta e segue. Nunca bloqueia.
            dropped.incrementAndGet()
        }
    }

    /**
     * Antes de [init] não há ficheiro: guarda as primeiras linhas em memória
     * para não as perder (é o caso dos logs de inicialização das flags).
     */
    private fun rememberPreBoot(level: String, tag: String, msg: String, t: Throwable?) {
        val i = preBootIndex
        if (i >= PRE_BOOT_LIMIT) return
        preBoot[i] = Entry(
            level = level,
            tag = tag,
            msg = msg,
            throwable = t,
            wallMs = System.currentTimeMillis(),
            threadName = Thread.currentThread().name,
        )
        preBootIndex++
    }

    /**
     * Regista apenas uma vez por processo para a mesma [key].
     *
     * Usado em logs de composição (que correriam a cada recomposição) e em
     * eventos de inicialização de subsistemas.
     */
    fun logOnce(key: String, level: String, tag: String, msg: String, t: Throwable? = null) {
        if (onceKeys.add(key)) log(level, tag, msg, t)
    }

    /** Verdadeiro se a chave ainda não foi registada (útil para decidir trabalho). */
    fun firstTime(key: String): Boolean = onceKeys.add(key)

    /** Caminho do log estruturado atual, para mostrar na interface. */
    fun currentLogPath(): String? = currentFile?.absolutePath

    /** Caminho da captura de logcat atual. */
    fun currentLogcatPath(): String? = logcatFile?.absolutePath

    // ------------------------------------------------------------------ deduplicação

    /**
     * Aplica a janela de deduplicação.
     * @return a mensagem a escrever (possivelmente com sufixo "repetido Nx"),
     *         ou `null` se esta ocorrência foi absorvida pela anterior.
     */
    private fun applyDedupe(level: String, tag: String, msg: String, now: Long): String? {
        val key = "$level/$tag/$msg"
        var suppress = false
        var repeats = 0

        dedupe.compute(key) { _, state ->
            when {
                state == null -> Dedupe(now, 0)
                now - state.lastMs < DEDUPE_WINDOW_MS -> {
                    state.repeats++
                    repeats = state.repeats
                    suppress = true
                    state
                }
                else -> {
                    repeats = state.repeats
                    state.lastMs = now
                    state.repeats = 0
                    state
                }
            }
        }

        if (dedupe.size > DEDUPE_MAP_LIMIT) dedupe.clear()

        if (suppress) return null
        return if (repeats > 0) "$msg [repetido ${repeats}x]" else msg
    }

    // ------------------------------------------------------------------ escrita

    /**
     * Uma única thread escreve no ficheiro, em lotes.
     *
     * Escrever em lotes (em vez de uma linha por vez) mantém o custo de I/O
     * baixo mesmo com muitos produtores, e a fila limitada garante que um
     * produtor patológico nunca faça crescer a memória.
     */
    private fun startWriter() {
        Thread({
            val batch = StringBuilder(8 * 1024)

            while (true) {
                val first = runCatching { queue.poll(POLL_MS, TimeUnit.MILLISECONDS) }.getOrNull()

                if (first == null) continue

                batch.setLength(0)
                batch.append(format(first, writerFmt))
                emitLogcat(first)

                // Drenar tudo o que já está pronto, num só acesso ao disco.
                while (true) {
                    val next = queue.poll() ?: break
                    if (batch.length > 60 * 1024) {
                        flushBatch(batch)
                        batch.setLength(0)
                    }
                    batch.append(format(next, writerFmt))
                    emitLogcat(next)
                }

                val droppedCount = dropped.get()
                if (droppedCount > 0 && dropped.compareAndSet(droppedCount, 0L)) {
                    batch.append(writerFmt.format(Date()))
                        .append(" [writer] WARN/LOGGER: fila cheia, ")
                        .append(droppedCount)
                        .append(" linha(s) descartada(s)\n")
                }

                flushBatch(batch)

                runCatching {
                    if ((currentFile?.length() ?: 0L) > MAX_FILE_BYTES) rotate()
                }
            }
        }, "app-logger").apply { isDaemon = true }.start()
    }

    /** Anexa o lote ao ficheiro atual. Só esta thread chama este método. */
    private fun flushBatch(batch: StringBuilder) {
        if (batch.isEmpty()) return
        val file = currentFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(batch.toString())
        }
    }

    private fun rotate() {
        val file = currentFile ?: return
        val previous = File(file.parentFile, file.nameWithoutExtension + ".old.txt")
        runCatching { file.copyTo(previous, overwrite = true) }
        runCatching { file.delete() }
    }

    private fun format(entry: Entry, formatter: SimpleDateFormat): String = buildString {
        append(formatter.format(Date(entry.wallMs)))
        append(" [").append(entry.threadName).append("] ")
        append(entry.level).append('/').append(entry.tag).append(": ").append(entry.msg).append('\n')
        entry.throwable?.let { append(Log.getStackTraceString(it)).append('\n') }
    }

    /** Escrita síncrona, só para o handler de crash (o processo está a terminar). */
    private fun logSync(level: String, tag: String, msg: String, t: Throwable?) {
        val file = currentFile ?: return
        val entry = Entry(level, tag, msg, t, System.currentTimeMillis(), Thread.currentThread().name)
        runCatching { file.appendText(format(entry, syncFmt)) }
        runCatching { Log.println(Log.ERROR, tag, msg) }
    }

    private fun emitLogcat(entry: Entry) {
        val priority = when (entry.level) {
            "FATAL", "ERROR" -> Log.ERROR
            "WARN" -> Log.WARN
            "DEBUG" -> Log.DEBUG
            else -> Log.INFO
        }
        val text = if (entry.throwable == null) {
            entry.msg
        } else {
            entry.msg + "\n" + Log.getStackTraceString(entry.throwable)
        }
        runCatching { Log.println(priority, entry.tag, text) }
    }

    // ------------------------------------------------------------------ diagnósticos

    /**
     * Captura do logcat do próprio processo (inclui erros nativos, libc, AGSL,
     * RenderThread e FATAL, que não passam pelo logger em Kotlin).
     */
    private fun startLogcatCapture() {
        val target = logcatFile ?: return
        Thread({
            runCatching {
                val process = Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-v", "threadtime", "--pid=${Process.myPid()}"))
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        runCatching { target.appendText(line + "\n") }
                    }
                }
            }
        }, "logcat-capture").apply { isDaemon = true }.start()
    }

    /**
     * Detecta a thread principal bloqueada — a assinatura clássica de "tela
     * preta" (o primeiro frame nunca chega a ser desenhado).
     */
    private fun startMainThreadWatchdog() {
        val main = Handler(Looper.getMainLooper())
        val heartbeat = AtomicLong(SystemClock.uptimeMillis())

        main.post(object : Runnable {
            override fun run() {
                heartbeat.set(SystemClock.uptimeMillis())
                main.postDelayed(this, 1_000L)
            }
        })

        Thread({
            while (true) {
                runCatching { Thread.sleep(WATCHDOG_PERIOD_MS) }
                val lag = SystemClock.uptimeMillis() - heartbeat.get()
                if (lag > WATCHDOG_STALL_MS) {
                    val trace = Looper.getMainLooper().thread.stackTrace
                        .joinToString("\n  at ") { "${it.className}.${it.methodName}(${it.lineNumber})" }
                    log("WARN", "WATCHDOG", "Thread principal travada há ${lag}ms:\n  at $trace")
                }
            }
        }, "watchdog").apply { isDaemon = true }.start()
    }

    private fun startMemoryMonitor(context: Context) {
        Thread({
            while (true) {
                runCatching { Thread.sleep(MEMORY_PERIOD_MS) }
                runCatching {
                    val runtime = Runtime.getRuntime()
                    log(
                        "INFO",
                        "MEM",
                        "livre=${freeMb(context)}MB | heap usado=${(runtime.totalMemory() - runtime.freeMemory()) / 1048576}MB" +
                            " | heap max=${runtime.maxMemory() / 1048576}MB",
                    )
                }
            }
        }, "mem-monitor").apply { isDaemon = true }.start()
    }

    private fun freeMb(context: Context): Long {
        val info = android.app.ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        manager.getMemoryInfo(info)
        return info.availMem / 1048576
    }
}
