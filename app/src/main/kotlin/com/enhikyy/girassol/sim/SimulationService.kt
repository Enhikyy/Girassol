package com.enhikyy.girassol.sim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.core.AutonomyController
import com.enhikyy.girassol.core.RunState
import com.enhikyy.girassol.data.WorldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que mantém a simulação viva com a tela apagada.
 *
 * **Tipo `specialUse`**, não `dataSync`: no Android 15 o `dataSync` tem um
 * limite de tempo (≈6h em 24h) e o sistema encerra o serviço no meio de um
 * treino. `specialUse` não tem esse timeout — é o tipo correto para uma
 * simulação de IA local que corre de forma contínua.
 *
 * O serviço detém um WakeLock parcial e reage ao botão PAUSAR da notificação,
 * que comanda o mesmo [AutonomyController] usado pela AppBar.
 */
class SimulationService : Service() {

    companion object {
        const val ACTION_PAUSE = "girassol.PAUSE"
        const val ACTION_RESUME = "girassol.RESUME"
        const val ACTION_TOGGLE = "girassol.TOGGLE"

        private const val CHANNEL_ID = "girassol_simulation"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 100L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SimulationService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var tickJob: Job? = null

    /** Instante da última iteração, para recuperar após o processo ser morto. */
    private val wallClock = object : WallClockStore {
        private val prefs by lazy {
            getSharedPreferences("girassol", Context.MODE_PRIVATE)
        }

        override fun load(): Long? = prefs.getLong("last_wall_ms", 0L).takeIf { it > 0L }

        override fun save(wallMs: Long) {
            prefs.edit().putLong("last_wall_ms", wallMs).apply()
        }
    }

    private val loop = GameLoop(WorldState.simulation, store = wallClock)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
        registerScreenReceiver()
        AppLogger.log("INFO", "SERVICE", "SimService criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> AutonomyController.pause("notificação")
            ACTION_RESUME -> AutonomyController.resume("notificação")
            ACTION_TOGGLE -> AutonomyController.toggle("notificação")
        }
        startForegroundCompat()
        // Reagir a mudanças de pausa: a notificação reflete sempre o estado real.
        observeAutonomy()
        if (tickJob?.isActive != true) {
            loop.reset()
            startTicking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                AppLogger.log("WARN", "SERVICE", "Receiver já não estava registado", e)
            }
        }
        screenReceiver = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        AppLogger.log("INFO", "SERVICE", "SimService destruído")
        super.onDestroy()
    }

    private fun startTicking() {
        tickJob = scope.launch {
            AppLogger.log("INFO", "LOOP", "GameLoop iniciado (tick=${TICK_MS}ms)")
            while (isActive) {
                // Nenhum passo acontece enquanto a autonomia estiver pausada.
                if (AutonomyController.state.value == RunState.PAUSED) {
                    AutonomyController.awaitRunning()
                    // O tempo em pausa não conta como tempo simulado.
                    loop.reset(System.currentTimeMillis())
                    AppLogger.log("INFO", "LOOP", "GameLoop retomado após pausa")
                }
                loop.advance(System.currentTimeMillis())
                delay(TICK_MS)
            }
        }
    }

    /** Mantém a notificação sincronizada com o [AutonomyController]. */
    private fun observeAutonomy() {
        scope.launch {
            AutonomyController.state.collect { state ->
                val nm = getSystemService(NotificationManager::class.java)
                runCatching { nm.notify(NOTIFICATION_ID, buildNotification(state)) }
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Girassol:Simulation").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> HeadlessState.onScreenOff()
                    Intent.ACTION_SCREEN_ON -> HeadlessState.onScreenOn()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(AutonomyController.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // O tipo `specialUse` só existe a partir do Android 14.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            // Em API 31–33 os tipos declarados no manifesto são aplicados.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("kt.usePropertyAccess")
    private fun buildNotification(state: RunState): Notification {
        fun action(label: String, act: String, code: Int): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this,
                code,
                Intent(this, SimulationService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }

        val paused = state == RunState.PAUSED
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Girassol")
            .setContentText(if (paused) "Pausado" else "Simulação e treino em andamento")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (paused) action("Retomar", ACTION_RESUME, 2)
                else action("Pausar", ACTION_PAUSE, 1),
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Girassol em execução",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Simulação e treino de IA local em segundo plano"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
