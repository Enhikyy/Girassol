package com.enhikyy.girassol.sim

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de exibição do mundo. Quando a tela apaga, [renderingActive] vira false
 * e o renderizador Vulkan deve pausar. A lógica continua rodando na CPU.
 */
object HeadlessState {
    private val _renderingActive = MutableStateFlow(true)
    val renderingActive: StateFlow<Boolean> = _renderingActive.asStateFlow()

    fun onScreenOn() {
        _renderingActive.value = true
    }

    fun onScreenOff() {
        _renderingActive.value = false
    }
}
