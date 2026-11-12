package dev.labforge.app.ui.world

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.LongSparseArray
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.core.world.AgentState
import dev.labforge.app.core.world.BuildingKind
import dev.labforge.app.core.world.BuildingState
import dev.labforge.app.core.world.WorldState
import dev.labforge.app.core.world.minuteOfDay
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class WorldRenderedFrame(
    val world: WorldState,
    val agents: List<AgentState>,
)

class WorldRenderer(
    private val onReady: () -> Unit,
    private val onFailure: (String) -> Unit,
    private val onVisibleAgents: (Set<String>) -> Unit,
    private val onFrameStats: (Int, Int) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile
    var world: WorldState? = null

    @Volatile
    var selectedId: String? = null

    @Volatile
    internal var lastRenderedFrame: WorldRenderedFrame? = null
        private set

    val camera = OrbitCamera()

    private var shader: WorldShader? = null
    private var box: GLMesh? = null
    private var cylinder: GLMesh? = null
    private var sphere: GLMesh? = null
    private var failed = false
    private var width = 1
    private var height = 1
    private var frame = 0L
    private var lastFpsFrame = 0L
    private var lastFpsTime = 0L
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val highQuality = Runtime.getRuntime().availableProcessors() >= 6
    private val agentSortBuffer = mutableListOf<AgentState>()
    private val colorCache = LongSparseArray<FloatArray>()
    private var lastAgentWorld: WorldState? = null
    private var lastAgentTargetX = Float.NaN
    private var lastAgentTargetZ = Float.NaN
    private var lastVisibleAgents: List<AgentState> = emptyList()
    private var agentSortX = 0f
    private var agentSortZ = 0f
    private val agentDistanceComparator = Comparator<AgentState> { first, second ->
        val firstDx = first.position.x - agentSortX
        val firstDz = first.position.z - agentSortZ
        val secondDx = second.position.x - agentSortX
        val secondDz = second.position.z - agentSortZ
        compareValues(firstDx * firstDx + firstDz * firstDz, secondDx * secondDx + secondDz * secondDz)
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        failed = false
        try {
            shader = WorldShader()
            box = GLMesh.box()
            cylinder = GLMesh.cylinder()
            sphere = GLMesh.sphere()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            // Em OpenGL ES, MSAA pertence à configuração da superfície EGL;
            // não existe a capacidade GL_MULTISAMPLE para glEnable.
            onReady()
        } catch (error: Throwable) {
            failed = true
            onFailure(error.message ?: "Não foi possível iniciar o OpenGL ES 3.")
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
        Matrix.perspectiveM(projection, 0, 45f, this.width.toFloat() / this.height, 0.15f, 240f)
    }

    override fun onDrawFrame(unused: GL10?) {
        if (failed) return
        try {
            renderFrame()
        } catch (error: Throwable) {
            failed = true
            onFailure("Falha na renderização do mundo: ${error.message}")
        }
    }

    fun releaseResources() {
        runCatching { box?.release() }
        runCatching { cylinder?.release() }
        runCatching { sphere?.release() }
        runCatching { shader?.release() }
        box = null; cylinder = null; sphere = null; shader = null
    }

    private fun renderFrame() {
        val state = world ?: return
        val activeShader = shader ?: return
        val activeBox = box ?: return
        val activeCylinder = cylinder ?: return
        val activeSphere = sphere ?: return
        camera.animate()
        val cameraSnapshot = camera.snapshot()
        val eye = cameraSnapshot.eye
        Matrix.setLookAtM(
            view, 0, eye.x, eye.y, eye.z,
            cameraSnapshot.targetX, cameraSnapshot.targetY, cameraSnapshot.targetZ,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        val hour = state.minuteOfDay / 60f
        val sunAngle = (hour - 6f) / 24f * (Math.PI * 2).toFloat()
        val sunHeight = sin(sunAngle)
        val daylight = (sunHeight * 1.3f + 0.34f).coerceIn(0.12f, 1f)
        val fog = blendColor(NIGHT_SKY, DAY_SKY, daylight)
        val lightColor = blendColor(floatArrayOf(0.42f, 0.50f, 0.72f), floatArrayOf(1f, 0.91f, 0.73f), daylight)
        GLES30.glClearColor(fog[0], fog[1], fog[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        activeShader.use()
        activeShader.setFrame(
            eye,
            WorldVec3(-cos(sunAngle) * 0.65f, -max(0.28f, abs(sunHeight)), -0.4f).normalized(),
            lightColor,
            fog,
        )

        drawEnvironment(activeBox, activeCylinder, activeSphere, state, daylight)
        drawBuildings(activeBox, activeCylinder, activeSphere, state, daylight)
        drawAgents(activeBox, activeCylinder, activeSphere, state, cameraSnapshot)
        drawIntegration(activeBox, activeCylinder, state)
        GLES30.glBindVertexArray(0)

        frame++
        if (frame % 45L == 0L) {
            val visible = state.agents.filter { agent ->
                val dx = agent.position.x - cameraSnapshot.targetX
                val dz = agent.position.z - cameraSnapshot.targetZ
                dx * dx + dz * dz < 72f * 72f
            }.take(160).map { it.id }.toSet()
            onVisibleAgents(visible)
        }
        val now = System.nanoTime()
        if (lastFpsTime == 0L) {
            lastFpsTime = now
            lastFpsFrame = frame
        } else if (now - lastFpsTime > 2_000_000_000L) {
            val fps = ((frame - lastFpsFrame) * 1_000_000_000.0 / (now - lastFpsTime)).toInt()
            onFrameStats(fps, lastRenderedFrame?.agents?.size ?: 0)
            lastFpsTime = now
            lastFpsFrame = frame
        }
    }

    private fun drawEnvironment(box: GLMesh, cylinder: GLMesh, sphere: GLMesh, state: WorldState, daylight: Float) {
        draw(box, 0f, -0.22f, 0f, 175f, 0.4f, 175f, color(0xFF202B2BL), 0f)
        val roadColor = blendColor(floatArrayOf(0.055f, 0.065f, 0.07f), floatArrayOf(0.16f, 0.18f, 0.18f), daylight)
        for (coordinate in listOf(-38f, -18f, 0f, 20f, 40f)) {
            draw(box, coordinate, 0.005f, 0f, 3.4f, 0.025f, 135f, roadColor, 0f)
            draw(box, 0f, 0.008f, coordinate, 135f, 0.025f, 3.4f, roadColor, 0f)
        }
        val lineColor = floatArrayOf(0.5f, 0.47f, 0.25f, 1f)
        for (coordinate in listOf(-38f, 0f, 40f)) {
            for (offset in -58..58 step 8) draw(box, coordinate, 0.025f, offset.toFloat(), 0.08f, 0.018f, 3.2f, lineColor, 0.12f)
        }
        // Low perimeter lights describe circulation without a texture or static backdrop.
        for (i in 0 until 28) {
            val angle = i.toFloat() / 28f * (Math.PI * 2).toFloat()
            val x = cos(angle) * 61f
            val z = sin(angle) * 61f
            draw(cylinder, x, 0.55f, z, 0.09f, 1.1f, 0.09f, color(0xFF586361L), 0f)
            draw(sphere, x, 1.12f, z, 0.18f, 0.18f, 0.18f, color(0xFFF2C14EL), 1.1f - daylight * 0.65f)
        }
    }

    private fun drawBuildings(box: GLMesh, cylinder: GLMesh, sphere: GLMesh, state: WorldState, daylight: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        state.buildings.forEach { building ->
            val width = building.footprintX * 1.05f
            val depth = building.footprintZ * 1.05f
            draw(box, building.position.x + 1.2f, 0.018f, building.position.z + 1.1f,
                width, 0.02f, depth, BUILDING_SHADOW, 0f)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        state.buildings.forEach { building ->
            when (building.kind) {
                BuildingKind.PUBLIC_SQUARE -> drawSquare(box, cylinder, sphere, building, daylight)
                else -> drawBuilding(box, cylinder, building, daylight)
            }
            if (selectedId == building.id || selectedId == building.companyId) {
                draw(cylinder, building.position.x, 0.08f, building.position.z,
                    maxOf(building.footprintX, building.footprintZ) * 1.35f, 0.06f,
                    maxOf(building.footprintX, building.footprintZ) * 1.35f,
                    color(building.accentColor, 0.7f), 0.65f)
            }
        }
    }

    private fun drawBuilding(box: GLMesh, cylinder: GLMesh, building: BuildingState, daylight: Float) {
        val x = building.position.x
        val z = building.position.z
        val w = building.footprintX
        val d = building.footprintZ
        val h = building.height
        val base = when (building.kind) {
            BuildingKind.RESIDENCE -> 0xFF514A45L
            BuildingKind.RESEARCH_LAB, BuildingKind.UNIVERSITY -> 0xFF344543L
            BuildingKind.DATA_CENTER -> 0xFF26363EL
            BuildingKind.CAFE -> 0xFF5B4937L
            else -> 0xFF343D42L
        }
        draw(box, x, h * 0.38f, z, w, h * 0.76f, d, color(base), 0f)
        draw(box, x - w * 0.07f, h * 0.86f, z + d * 0.04f, w * 0.74f, h * 0.2f, d * 0.78f, color(0xFF2B3539L), 0f)
        draw(box, x, 0.22f, z, w * 1.12f, 0.42f, d * 1.12f, color(0xFF59615FL), 0f)
        val accent = color(building.accentColor)
        draw(box, x - w * 0.43f, h * 0.49f, z + d * 0.505f, 0.13f, h * 0.86f, 0.12f, accent, 0.22f)
        draw(box, x + w * 0.43f, h * 0.49f, z + d * 0.505f, 0.13f, h * 0.86f, 0.12f, accent, 0.22f)
        draw(box, x, h + 0.08f, z, w * 0.55f, 0.08f, d * 0.5f, accent, 0.55f)
        val rows = if (highQuality) 6 else 3
        val windowGlow = (0.8f - daylight * 0.7f).coerceAtLeast(0.08f)
        val windows = if (building.kind == BuildingKind.RESIDENCE) color(0xFFFFD8A3L) else color(0xFF9ADCE0L)
        for (row in 1..rows) {
            val y = h * (0.12f + row * 0.72f / (rows + 1))
            draw(box, x, y, z + d * 0.503f, w * 0.7f, 0.09f, 0.035f, windows, windowGlow)
            draw(box, x, y, z - d * 0.503f, w * 0.7f, 0.09f, 0.035f, windows, windowGlow)
        }
        if (building.kind == BuildingKind.RESEARCH_LAB || building.kind == BuildingKind.DATA_CENTER) {
            draw(cylinder, x, h + 1.1f, z, 0.16f, 2.2f, 0.16f, color(0xFF708C8BL), 0f)
            draw(cylinder, x, h + 2.2f, z, 0.52f, 0.09f, 0.52f, accent, 0.9f)
        }
    }

    private fun drawSquare(box: GLMesh, cylinder: GLMesh, sphere: GLMesh, building: BuildingState, daylight: Float) {
        val p = building.position
        draw(cylinder, p.x, 0.1f, p.z, building.footprintX, 0.2f, building.footprintZ, color(0xFF65716AL), 0f)
        draw(cylinder, p.x, 1.6f, p.z, 0.35f, 3.2f, 0.35f, color(0xFF8A7540L), 0.12f)
        for (i in 0 until 10) {
            val angle = i.toFloat() / 10f * (Math.PI * 2).toFloat()
            draw(sphere, p.x + cos(angle) * 0.8f, 3.35f + sin(angle) * 0.8f, p.z,
                0.55f, 0.9f, 0.25f, color(0xFFF2C14EL), 0.32f)
        }
        draw(sphere, p.x, 3.35f, p.z, 0.85f, 0.85f, 0.45f, color(0xFF70532BL), 0.1f)
    }

    private fun drawAgents(
        box: GLMesh,
        cylinder: GLMesh,
        sphere: GLMesh,
        state: WorldState,
        cameraSnapshot: OrbitCameraSnapshot,
    ) {
        val agents = selectRenderedAgents(state, cameraSnapshot)
        if (lastRenderedFrame?.world !== state || lastRenderedFrame?.agents !== agents) {
            lastRenderedFrame = WorldRenderedFrame(state, agents)
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        agents.forEach { agent ->
            draw(cylinder, agent.position.x + 0.25f, 0.025f, agent.position.z + 0.2f,
                0.7f, 0.025f, 0.7f, AGENT_SHADOW, 0f)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        agents.forEachIndexed { index, agent -> drawAgent(box, cylinder, sphere, agent, state, index) }
    }

    private fun selectRenderedAgents(state: WorldState, camera: OrbitCameraSnapshot): List<AgentState> {
        if (lastAgentWorld === state && lastAgentTargetX == camera.targetX && lastAgentTargetZ == camera.targetZ) {
            return lastVisibleAgents
        }
        val visibleLimit = if (highQuality) 96 else 64
        agentSortX = camera.targetX
        agentSortZ = camera.targetZ
        agentSortBuffer.clear()
        agentSortBuffer.addAll(state.agents)
        agentSortBuffer.sortWith(agentDistanceComparator)
        val selected = ArrayList<AgentState>(minOf(visibleLimit, agentSortBuffer.size))
        for (index in 0 until minOf(visibleLimit, agentSortBuffer.size)) selected.add(agentSortBuffer[index])
        lastAgentWorld = state
        lastAgentTargetX = camera.targetX
        lastAgentTargetZ = camera.targetZ
        lastVisibleAgents = selected
        return selected
    }

    private fun drawAgent(box: GLMesh, cylinder: GLMesh, sphere: GLMesh, agent: AgentState, state: WorldState, index: Int) {
        val height = agent.appearance.height
        val walking = agent.action?.let { action ->
            val dx = action.target.x - agent.position.x
            val dz = action.target.z - agent.position.z
            dx * dx + dz * dz > 1.2f
        } == true
        val bob = if (walking) abs(sin(state.tick * 0.7f + index)) * 0.055f else 0f
        val x = agent.position.x
        val z = agent.position.z
        val bodyColor = color(agent.appearance.primaryColor)
        val skin = color(skinTones[agent.appearance.skinTone.coerceIn(0, skinTones.lastIndex)])
        draw(cylinder, x, 0.55f * height + bob, z, 0.46f + agent.appearance.build * 0.16f,
            0.85f * height, 0.42f + agent.appearance.build * 0.12f, bodyColor, 0.03f)
        draw(sphere, x, 1.18f * height + bob, z, 0.44f * height, 0.48f * height, 0.44f * height, skin, 0f)
        draw(box, x, 1.39f * height + bob, z - 0.02f, 0.39f * height, 0.1f, 0.4f * height,
            color(0xFF262326L), 0f)
        val legSwing = if (walking) sin(state.tick * 0.7f + index) * 0.12f else 0f
        draw(box, x - 0.13f, 0.16f + bob, z + legSwing, 0.16f, 0.32f, 0.16f, color(0xFF242D32L), 0f)
        draw(box, x + 0.13f, 0.16f + bob, z - legSwing, 0.16f, 0.32f, 0.16f, color(0xFF242D32L), 0f)
        if (agent.action?.type == ActionType.RESEARCH || agent.action?.type == ActionType.LEARN) {
            draw(sphere, x, 1.65f * height + bob, z, 0.13f, 0.13f, 0.13f, color(0xFF84D98AL), 0.8f)
        }
        if (selectedId == agent.id) {
            draw(cylinder, x, 0.06f, z, 1.25f, 0.06f, 1.25f, color(0xFFF2C14EL), 0.9f)
            draw(sphere, x, 1.75f * height, z, 0.17f, 0.17f, 0.17f, color(0xFFF2C14EL), 1f)
        }
    }

    private fun drawIntegration(box: GLMesh, cylinder: GLMesh, state: WorldState) {
        val integration = state.integration
        if (integration.activeJobProgress <= 0f || integration.activeJobKind.isBlank()) return
        val building = state.buildings.firstOrNull { it.id == "building_data" } ?: return
        val progress = integration.activeJobProgress.coerceIn(0.02f, 1f)
        val rotation = (state.tick % 360).toFloat()
        draw(cylinder, building.position.x, building.height + 3.2f, building.position.z,
            2.4f + progress * 2f, 0.08f, 2.4f + progress * 2f,
            color(0xFF6FD6DFL, 0.85f), 0.95f, rotation)
        draw(box, building.position.x, building.height + 3.2f, building.position.z,
            0.18f, 3.2f * progress, 0.18f, color(0xFFF2C14EL), 1.1f, rotation)
    }

    private fun draw(
        mesh: GLMesh,
        x: Float,
        y: Float,
        z: Float,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        rgba: FloatArray,
        glow: Float,
        rotationY: Float = 0f,
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotationY != 0f) Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, scaleX, scaleY, scaleZ)
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        shader?.draw(mesh, model, mvp, rgba, glow)
    }

    private fun color(value: Long, alpha: Float = 1f): FloatArray {
        val key = (value and 0xFFFF_FFFFL) or (alpha.toRawBits().toLong() shl 32)
        colorCache.get(key)?.let { return it }
        val rgba = floatArrayOf(
            ((value shr 16) and 0xFF) / 255f,
            ((value shr 8) and 0xFF) / 255f,
            (value and 0xFF) / 255f,
            alpha,
        )
        colorCache.put(key, rgba)
        return rgba
    }

    companion object {
        private val NIGHT_SKY = floatArrayOf(0.018f, 0.026f, 0.045f)
        private val DAY_SKY = floatArrayOf(0.34f, 0.46f, 0.52f)
        private val AGENT_SHADOW = floatArrayOf(0.005f, 0.008f, 0.01f, 0.38f)
        private val BUILDING_SHADOW = floatArrayOf(0.008f, 0.012f, 0.015f, 0.34f)
        private val skinTones = listOf(0xFF6E4632L, 0xFF8B5D43L, 0xFFAD7957L, 0xFFD1A17BL, 0xFFE0BFA2L)

        private fun blendColor(a: FloatArray, b: FloatArray, fraction: Float): FloatArray {
            val t = fraction.coerceIn(0f, 1f)
            return floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
        }
    }
}
