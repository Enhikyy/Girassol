package com.enhikyy.girassol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.sim.SimulationService

/**
 * Ponto de entrada do Girassol.
 *
 * Arranca a simulação (in-process) e o serviço em primeiro plano que a mantém
 * viva com a tela apagada.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.log("INFO", AppLogger.TAG, "MainActivity.onCreate iniciado")

        // Usando AppLogger para captura de crashes

        enableEdgeToEdge()

        WorldState.initialize()
        // Uma falha ao arrancar o serviço não pode levar a app consigo.
        runCatching { startSimulationService() }
            .onFailure { android.util.Log.e("Girassol", "serviço não arrancou", it) }
        requestNotificationPermission()

        setContent {
            // O fundo do tema só existe dentro de GirassolApp (MaterialTheme);
            // a raiz aqui é só uma caixa de tela cheia.
            GirassolApp()
        }
        AppLogger.log("INFO", AppLogger.TAG, "onCreate finalizado")
    }

    private fun startSimulationService() {
        val intent = Intent(this, SimulationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
