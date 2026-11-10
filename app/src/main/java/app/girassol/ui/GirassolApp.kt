package app.girassol.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.girassol.AppContainer
import app.girassol.ui.screens.ChatScreen
import app.girassol.ui.screens.HomeScreen
import app.girassol.ui.screens.SettingsScreen
import app.girassol.ui.screens.StudioScreen
import app.girassol.ui.screens.StudyScreen
import app.girassol.ui.screens.WorldScreen
import app.girassol.ui.theme.GirassolTheme
import app.girassol.vm.AppViewModel

enum class AppDestination(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    WORLD("Mundo", Icons.Default.Public),
    CHAT("Chat", Icons.Default.Chat),
    STUDY("Estudo", Icons.Default.School),
    STUDIO("Studio", Icons.Default.Code),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

@Composable
fun GirassolApp(container: AppContainer) {
    val model: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val settings by model.settings.collectAsState()
    GirassolTheme(settings.themeMode) {
        var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
        val destination = AppDestination.valueOf(destinationName)
        val notice by model.notice.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(notice) {
            notice?.let { snackbar.showSnackbar(it.message); model.dismissNotice() }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 700.dp
            val destinations = AppDestination.entries.filter { it != AppDestination.SETTINGS }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (compact) NavigationBar {
                        destinations.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destinationName = item.name },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (!compact) NavigationRail {
                        destinations.forEach { item ->
                            NavigationRailItem(
                                modifier = Modifier.padding(vertical = 4.dp),
                                selected = destination == item,
                                onClick = { destinationName = item.name },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        Crossfade(
                            targetState = destination,
                            animationSpec = if (settings.animationScale == 0f) snap() else tween((320 * settings.animationScale).toInt()),
                            label = "navigation",
                        ) { target ->
                            when (target) {
                                AppDestination.HOME -> HomeScreen(model, { destinationName = it.name }, { destinationName = AppDestination.SETTINGS.name })
                                AppDestination.WORLD -> WorldScreen(model)
                                AppDestination.CHAT -> ChatScreen(model)
                                AppDestination.STUDY -> StudyScreen(model)
                                AppDestination.STUDIO -> StudioScreen(model)
                                AppDestination.SETTINGS -> SettingsScreen(model) { destinationName = AppDestination.HOME.name }
                            }
                        }
                    }
                }
            }
        }
    }
}
