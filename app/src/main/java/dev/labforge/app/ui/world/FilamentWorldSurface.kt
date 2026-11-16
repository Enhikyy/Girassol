package dev.labforge.app.ui.world

import android.content.Context
import android.util.Base64
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import com.google.android.filament.Engine
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Android SurfaceView backed by Filament. Vulkan is attempted first;
 * the OpenGL ES backend is used only when Vulkan initialization fails.
 */
class FilamentWorldSurface(context: Context) : SurfaceView(context) {
    private var viewer: ModelViewer? = null
    private var frameActive = false
    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val activeViewer = viewer
            if (!frameActive || activeViewer == null || windowVisibility != VISIBLE) return
            runCatching { activeViewer.render(frameTimeNanos) }
                .onFailure { Log.e(TAG, "Falha no frame do Mundo", it) }
            choreographer.postFrameCallback(this)
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.rgb(7, 9, 8))
        setOnTouchListener { _, event: MotionEvent ->
            viewer?.onTouchEvent(event)
            true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initializeViewer()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        runCatching { viewer?.destroy() }
            .onFailure { Log.w(TAG, "Falha ao liberar renderer", it) }
        viewer = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startFrames() else stopFrames()
    }

    private fun initializeViewer() {
        if (viewer != null) return
        try {
            Utils.init()
            val engine = runCatching { Engine.create(Engine.Backend.VULKAN) }
                .getOrNull() ?: Engine.create(Engine.Backend.OPENGL)
            val created = ModelViewer(this, engine)
            viewer = created
            created.loadModelGltf(WorldModelFactory.modelBuffer()) { uri ->
                WorldModelFactory.resourceBuffer(uri)
            }
            created.transformToUnitCube()
            created.view.antiAliasing = View.AntiAliasing.FXAA
            created.view.dynamicResolutionOptions = created.view.dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.MEDIUM
            }
            startFrames()
        } catch (error: Throwable) {
            Log.e(TAG, "Não foi possível iniciar o Mundo 3D", error)
            runCatching { viewer?.destroy() }
            viewer = null
        }
    }

    private fun startFrames() {
        if (frameActive || viewer == null || windowVisibility != VISIBLE) return
        frameActive = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        frameActive = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private companion object { const val TAG = "GirassolWorld3D" }
}

/** Creates an offline glTF campus at runtime; no remote model or asset download is required. */
private object WorldModelFactory {
    private val positions = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val colors = ArrayList<Float>()
    private val indices = ArrayList<Short>()

    fun modelBuffer(): ByteBuffer {
        positions.clear()
        normals.clear()
        colors.clear()
        indices.clear()

        addBox(0f,-0.15f,0f,20f,0.3f,20f,floatArrayOf(0.10f,0.13f,0.12f,1f))
        addBox(0f,0.02f,0f,5.5f,0.06f,5.5f,floatArrayOf(0.18f,0.29f,0.18f,1f))
        addBox(-4f,1.6f,-4f,2.2f,3.2f,2.2f,floatArrayOf(0.40f,0.52f,0.50f,1f))
        addBox(4f,1.1f,-4f,2.8f,2.2f,2.5f,floatArrayOf(0.58f,0.55f,0.43f,1f))
        addBox(-4f,1.2f,4f,3.4f,2.4f,2.6f,floatArrayOf(0.55f,0.42f,0.30f,1f))
        addBox(4f,1.35f,4f,2.5f,2.7f,2.4f,floatArrayOf(0.62f,0.68f,0.65f,1f))
        addBox(-1.3f,1.3f,-7f,1.6f,2.6f,1.5f,floatArrayOf(0.34f,0.43f,0.39f,1f))
        addBox(1.4f,0.9f,-7f,1.5f,1.8f,1.5f,floatArrayOf(0.46f,0.32f,0.25f,1f))
        addBox(-7f,1f,-1f,1.5f,2f,1.5f,floatArrayOf(0.37f,0.43f,0.34f,1f))
        addBox(7f,0.9f,-1f,1.5f,1.8f,1.5f,floatArrayOf(0.29f,0.40f,0.44f,1f))

        val vertexCount = positions.size / 3
        val indexOffset = positions.size * 4 + normals.size * 4 + colors.size * 4
        val binary = ByteBuffer.allocate(indexOffset + indices.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach(binary::putFloat)
        normals.forEach(binary::putFloat)
        colors.forEach(binary::putFloat)
        val indexStart = binary.position()
        indices.forEach(binary::putShort)
        val bytes = ByteArray(binary.position())
        binary.flip()
        binary.get(bytes)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val bufferViews = JSONArray()
            .put(view(0,positions.size*4,34962))
            .put(view(positions.size*4,normals.size*4,34962))
            .put(view(positions.size*4+normals.size*4,colors.size*4,34962))
            .put(view(indexStart,indices.size*2,34963))
        val accessors = JSONArray()
            .put(accessor(0,5126,vertexCount,"VEC3").put("min",JSONArray().put(-10).put(-0.3).put(-10)).put("max",JSONArray().put(10).put(3.2).put(10)))
            .put(accessor(1,5126,vertexCount,"VEC3"))
            .put(accessor(2,5126,vertexCount,"VEC4"))
            .put(accessor(3,5123,indices.size,"SCALAR"))
        val gltf = JSONObject()
            .put("asset",JSONObject().put("version","2.0").put("generator","Girassol Native World"))
            .put("scene",0)
            .put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("nodes",JSONArray().put(JSONObject().put("name","Girassol_Campus").put("mesh",0)))
            .put("meshes",JSONArray().put(JSONObject().put("name","Campus").put("primitives",JSONArray().put(
                JSONObject()
                    .put("attributes",JSONObject().put("POSITION",0).put("NORMAL",1).put("COLOR_0",2))
                    .put("indices",3).put("mode",4)
            ))))
            .put("materials",JSONArray().put(JSONObject().put("name","Campus_FilmStock").put(
                "pbrMetallicRoughness",
                JSONObject().put("baseColorFactor",JSONArray().put(1).put(1).put(1).put(1))
                    .put("metallicFactor",0.12).put("roughnessFactor",0.58)
            )))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",bytes.size)
                .put("uri","data:application/octet-stream;base64,"+encoded)))
            .put("bufferViews",bufferViews)
            .put("accessors",accessors)
        return ByteBuffer.wrap(gltf.toString().toByteArray(Charsets.UTF_8))
    }

    fun resourceBuffer(uri: String): ByteBuffer? {
        if (!uri.startsWith("data:") || !uri.contains("base64,")) return null
        return ByteBuffer.wrap(Base64.decode(uri.substringAfter("base64,"),Base64.DEFAULT))
    }

    private fun view(offset: Int,length: Int,target: Int) =
        JSONObject().put("buffer",0).put("byteOffset",offset).put("byteLength",length).put("target",target)

    private fun accessor(viewIndex: Int,component: Int,count: Int,type: String) =
        JSONObject().put("bufferView",viewIndex).put("componentType",component).put("count",count).put("type",type)

    private fun addBox(cx: Float,cy: Float,cz: Float,sx: Float,sy: Float,sz: Float,color: FloatArray) {
        val x=sx/2f; val y=sy/2f; val z=sz/2f
        val faces=listOf(
            Face(listOf(floatArrayOf(-x,-y,z),floatArrayOf(x,-y,z),floatArrayOf(x,y,z),floatArrayOf(-x,y,z)),floatArrayOf(0f,0f,1f)),
            Face(listOf(floatArrayOf(x,-y,-z),floatArrayOf(-x,-y,-z),floatArrayOf(-x,y,-z),floatArrayOf(x,y,-z)),floatArrayOf(0f,0f,-1f)),
            Face(listOf(floatArrayOf(x,-y,z),floatArrayOf(x,-y,-z),floatArrayOf(x,y,-z),floatArrayOf(x,y,z)),floatArrayOf(1f,0f,0f)),
            Face(listOf(floatArrayOf(-x,-y,-z),floatArrayOf(-x,-y,z),floatArrayOf(-x,y,z),floatArrayOf(-x,y,-z)),floatArrayOf(-1f,0f,0f)),
            Face(listOf(floatArrayOf(-x,y,z),floatArrayOf(x,y,z),floatArrayOf(x,y,-z),floatArrayOf(-x,y,-z)),floatArrayOf(0f,1f,0f)),
            Face(listOf(floatArrayOf(-x,-y,-z),floatArrayOf(x,-y,-z),floatArrayOf(x,-y,z),floatArrayOf(-x,-y,z)),floatArrayOf(0f,-1f,0f)),
        )
        faces.forEach { face ->
            val base=positions.size/3
            face.corners.forEach { p ->
                positions.add(p[0]+cx); positions.add(p[1]+cy); positions.add(p[2]+cz)
                normals.add(face.normal[0]); normals.add(face.normal[1]); normals.add(face.normal[2])
                colors.add(color[0]); colors.add(color[1]); colors.add(color[2]); colors.add(color[3])
            }
            indices.add(base.toShort()); indices.add((base+1).toShort()); indices.add((base+2).toShort())
            indices.add(base.toShort()); indices.add((base+2).toShort()); indices.add((base+3).toShort())
        }
    }

    private data class Face(val corners: List<FloatArray>,val normal: FloatArray)
}
