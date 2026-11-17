package com.enhikyy.girassol.vulkan

import android.graphics.Color as AndroidColor
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enhikyy.girassol.sim.HeadlessState

/**
 * Superfície Vulkan opcional para o mundo.
 *
 * Se a biblioteca nativa não estiver disponível, compõe apenas um fundo escuro
 * — a tela MUNDO desenha o mundo em Canvas por cima, e nada falha.
 */
@Composable
fun VulkanView(
    modifier: Modifier = Modifier,
    onRendererReady: (VulkanRenderer) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderingActive by HeadlessState.renderingActive.collectAsState()

    if (!VulkanRenderer.isAvailable()) {
        Box(modifier.fillMaxSize())
        return
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { textureView ->
            val renderer = VulkanRenderer.create(
                textureView = textureView,
                owner = lifecycleOwner,
            )
            if (renderer != null) {
                renderer.setHeadlessMode(!renderingActive)
                onRendererReady(renderer)
            }
        },
    )
}
