package dev.labforge.app.ui.world

import kotlin.math.sqrt

internal data class RenderTargetSize(val width: Int, val height: Int, val estimatedBytes: Long)

/** Bounds RGBA8 + DEPTH_COMPONENT16 framebuffer allocations on mobile GPUs. */
internal object CinematicRenderBudget {
    private const val BYTES_PER_PIXEL = 6L
    const val DEFAULT_MAX_EDGE = 1600
    const val DEFAULT_MAX_BYTES = 12L * 1024L * 1024L

    fun targetSize(
        width: Int,
        height: Int,
        maxEdge: Int = DEFAULT_MAX_EDGE,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): RenderTargetSize {
        val sourceWidth = width.coerceAtLeast(1)
        val sourceHeight = height.coerceAtLeast(1)
        val safeEdge = maxEdge.coerceAtLeast(1)
        val safeBytes = maxBytes.coerceAtLeast(BYTES_PER_PIXEL)
        val edgeScale = minOf(1.0, safeEdge.toDouble() / maxOf(sourceWidth, sourceHeight))
        val memoryScale = sqrt(
            safeBytes.toDouble() /
                (sourceWidth.toDouble() * sourceHeight.toDouble() * BYTES_PER_PIXEL.toDouble())
        )
        val scale = minOf(1.0, edgeScale, memoryScale)
        val targetWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        return RenderTargetSize(
            width = targetWidth,
            height = targetHeight,
            estimatedBytes = targetWidth.toLong() * targetHeight.toLong() * BYTES_PER_PIXEL,
        )
    }
}
