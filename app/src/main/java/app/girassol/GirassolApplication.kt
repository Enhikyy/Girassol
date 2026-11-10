package app.girassol

import android.app.Application
import app.girassol.core.learning.SleepScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GirassolApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            val enabled = runCatching { container.settings.settings.first().automaticSleep }.getOrDefault(true)
            SleepScheduler.schedule(this@GirassolApplication, enabled)
        }
    }
}
