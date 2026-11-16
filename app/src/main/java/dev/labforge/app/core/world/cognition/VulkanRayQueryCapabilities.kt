package dev.labforge.app.core.world.cognition

enum class WorldRenderPath { VULKAN_RAY_QUERY, OPENGL_ES_FALLBACK }
data class VulkanRayQueryStatus(val supported:Boolean,val reason:String) {
    val renderPath:WorldRenderPath get()=if(supported)WorldRenderPath.VULKAN_RAY_QUERY else WorldRenderPath.OPENGL_ES_FALLBACK
}

/** Checks physical-device extension and feature bits; a Vulkan API version alone is insufficient. */
object VulkanRayQueryCapabilities {
    @Volatile private var cached:VulkanRayQueryStatus?=null
    fun detect(forceRefresh:Boolean=false):VulkanRayQueryStatus {
        if(!forceRefresh)cached?.let{return it}
        val result=runCatching {
            System.loadLibrary("labforge")
            if(nativeRayQuerySupported())VulkanRayQueryStatus(true,"Ray query and acceleration structures are supported.")
            else VulkanRayQueryStatus(false,"Required ray-query extensions/features unavailable; OpenGL ES fallback selected.")
        }.getOrElse { VulkanRayQueryStatus(false,"Native Vulkan probe unavailable: "+it.javaClass.simpleName) }
        cached=result
        return result
    }
    private external fun nativeRayQuerySupported():Boolean
}
