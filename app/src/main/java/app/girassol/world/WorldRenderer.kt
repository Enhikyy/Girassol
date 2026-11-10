package app.girassol.world

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import app.girassol.core.activity.ActivityHub
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private class Mesh(vertices: FloatArray, indices: ShortArray) {
    private var vao = 0
    private var vertexBuffer = 0
    private var indexBuffer = 0
    private var indexCount = 0

    init {
        val v = IntArray(1); val buffers = IntArray(2)
        GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glGenBuffers(2, buffers, 0); vertexBuffer = buffers[0]; indexBuffer = buffers[1]
        indexCount = indices.size
        val vertexData: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertices); position(0)
        }
        val indexData: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(indices); position(0)
        }
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexData, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexData, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }
}

class WorldRenderer(private val activities: ActivityHub) : GLSurfaceView.Renderer {
    private lateinit var cube: Mesh
    private lateinit var cylinder: Mesh
    private var program = 0
    private var mvpLocation = 0
    private var modelLocation = 0
    private var colorLocation = 0
    private var cameraLocation = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val camera = FloatArray(3)
    private var aspect = 1f
    private var previousFrame = 0L
    private var elapsed = 0f
    private var agentX = 0f
    private var agentZ = 2f
    private var agentYaw = 0f
    private var agentMoving = false
    private var focusX = 0f
    private var focusZ = 0f
    private var desiredFocusX = 0f
    private var desiredFocusZ = 0f
    private var distance = 27f

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.055f, 0.075f, 0.09f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        modelLocation = GLES30.glGetUniformLocation(program, "uModel")
        colorLocation = GLES30.glGetUniformLocation(program, "uColor")
        cameraLocation = GLES30.glGetUniformLocation(program, "uCamera")
        cube = Mesh(cubeVertices(), cubeIndices())
        cylinder = Mesh(cylinderVertices(16), cylinderIndices(16))
        previousFrame = System.nanoTime()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 42f, aspect, 0.1f, 140f)
    }

    override fun onDrawFrame(unused: GL10?) {
        val now = System.nanoTime()
        val delta = ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
        previousFrame = now; elapsed += delta
        val snapshot = activities.state.value
        val primary = snapshot.primary
        val recent = snapshot.recentEvents.lastOrNull { System.currentTimeMillis() - it.at in 0..12_000L }
        val zone = WorldZones.byId(primary?.kind?.zoneId ?: recent?.kind?.zoneId ?: "atrium")
        updateAgent(zone, delta)
        val smoothing = min(1f, delta * 4f)
        focusX += (desiredFocusX - focusX) * smoothing
        focusZ += (desiredFocusZ - focusZ) * smoothing
        val eyeX = focusX + distance * 0.72f
        val eyeY = distance * 0.82f
        val eyeZ = focusZ + distance * 0.72f
        camera[0] = eyeX; camera[1] = eyeY; camera[2] = eyeZ
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, focusX, 0f, focusZ, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniform3f(cameraLocation, eyeX, eyeY, eyeZ)
        drawCampus()
        drawAgent(primary != null, zone.color)
    }

    fun focusZone(id: String) {
        val zone = WorldZones.byId(id)
        desiredFocusX = zone.x; desiredFocusZ = zone.z
        distance = 19f
    }

    fun pan(dx: Float, dz: Float) {
        desiredFocusX = (desiredFocusX + dx).coerceIn(-12f, 12f)
        desiredFocusZ = (desiredFocusZ + dz).coerceIn(-13f, 11f)
    }

    fun zoom(factor: Float) { distance = (distance / factor).coerceIn(13f, 43f) }

    private fun updateAgent(zone: WorldZone, delta: Float) {
        val targetX = zone.x + if (zone.id == "atrium") 0f else 0.4f
        val targetZ = zone.z + if (zone.id == "atrium") 1.8f else 2.6f
        val dx = targetX - agentX; val dz = targetZ - agentZ
        val length = sqrt(dx * dx + dz * dz)
        agentMoving = length > 0.05f
        if (agentMoving) {
            val step = min(length, delta * 2.8f)
            agentX += dx / length * step; agentZ += dz / length * step
            agentYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        }
    }

    private fun drawCampus() {
        cube(0f, -0.35f, 0f, 28f, 0.6f, 28f, 0xFF263C3A)
        for (line in -12..12 step 2) {
            cube(line.toFloat(), -0.035f, 0f, 0.025f, 0.015f, 26f, 0xFF34504B)
            cube(0f, -0.03f, line.toFloat(), 26f, 0.015f, 0.025f, 0xFF34504B)
        }
        WorldZones.all.filter { it.id != "atrium" }.forEach { path(WorldZones.atrium, it) }
        drawAtrium(WorldZones.atrium)
        drawLibrary(WorldZones.library)
        drawUniversity(WorldZones.university)
        drawHome(WorldZones.home)
        drawClinic(WorldZones.clinic)
        drawStudio(WorldZones.studio)
        for (index in 0 until 22) {
            val angle = index * (Math.PI * 2 / 22)
            val radius = 11.5f + (index % 3) * 0.45f
            val x = cos(angle).toFloat() * radius
            val z = sin(angle).toFloat() * radius
            cylinder(x, 0.45f, z, 0.22f, 0.9f, 0.22f, 0xFF806B55)
            cylinder(x, 1.45f, z, 1.05f, 1.7f, 1.05f, if (index % 2 == 0) 0xFF3F7665 else 0xFF4D846E)
        }
    }

    private fun path(from: WorldZone, to: WorldZone) {
        val dx = to.x - from.x; val dz = to.z - from.z
        val length = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        cube(dx / 2, 0.015f, dz / 2, 1.15f, 0.08f, length, 0xFF64716D, yaw)
        cube(dx / 2, 0.061f, dz / 2, 0.055f, 0.012f, length * 0.94f, 0xFFA8B5AC, yaw)
    }

    private fun drawAtrium(zone: WorldZone) {
        cylinder(zone.x, 0.1f, zone.z, 4.4f, 0.2f, 4.4f, 0xFF899D93)
        cylinder(zone.x, 0.24f, zone.z, 2.5f, 0.22f, 2.5f, 0xFF334D49)
        cylinder(zone.x, 0.48f, zone.z, 1.15f, 0.45f, 1.15f, zone.color)
        cylinder(zone.x, 1.3f, zone.z, 0.34f, 1.5f, 0.34f, 0xFFC8D6CC)
        for (index in 0 until 6) {
            val angle = index * Math.PI / 3
            val x = zone.x + cos(angle).toFloat() * 2.8f
            val z = zone.z + sin(angle).toFloat() * 2.8f
            cube(x, 0.42f, z, 0.9f, 0.16f, 0.34f, 0xFF7D8D86, -Math.toDegrees(angle).toFloat())
        }
    }

    private fun drawLibrary(zone: WorldZone) {
        cube(zone.x, 1.15f, zone.z, 4.8f, 2.3f, 3.6f, 0xFFD0D6CA)
        cube(zone.x, 2.45f, zone.z, 5.15f, 0.3f, 3.95f, zone.color)
        cube(zone.x, 0.16f, zone.z + 2.25f, 4.7f, 0.28f, 1.1f, 0xFF83958C)
        for (column in -2..2) {
            cube(zone.x + column * 0.9f, 1.05f, zone.z + 1.95f, 0.18f, 1.8f, 0.18f, 0xFFF0EBDC)
        }
        for (row in 0 until 2) for (column in 0 until 5) {
            cube(zone.x - 1.6f + column * 0.8f, 0.85f + row * 0.85f, zone.z + 1.815f, 0.45f, 0.46f, 0.06f, 0xFF284C58)
        }
        for (book in 0 until 5) cube(zone.x - 1.6f + book * 0.23f, 2.78f, zone.z, 0.15f, 0.48f + (book % 2) * 0.16f, 0.65f, if (book % 2 == 0) 0xFFE0A56D else zone.color)
    }

    private fun drawUniversity(zone: WorldZone) {
        cube(zone.x, 1.15f, zone.z, 4.3f, 2.3f, 3.2f, 0xFFC9CED8)
        cube(zone.x - 2.25f, 1.65f, zone.z, 0.9f, 3.3f, 2.7f, 0xFFABB5CA)
        cube(zone.x + 2.25f, 1.65f, zone.z, 0.9f, 3.3f, 2.7f, 0xFFABB5CA)
        cube(zone.x, 2.55f, zone.z, 4.8f, 0.32f, 3.6f, zone.color)
        cube(zone.x, 3.3f, zone.z, 1.1f, 1.4f, 1.1f, 0xFFD9DCE4)
        cylinder(zone.x, 4.12f, zone.z, 1.25f, 0.25f, 1.25f, zone.color)
        for (column in -2..2) cube(zone.x + column * 0.72f, 1.25f, zone.z + 1.63f, 0.36f, 0.72f, 0.07f, 0xFF35456F)
        cube(zone.x, 0.12f, zone.z + 2.05f, 5.3f, 0.22f, 0.85f, 0xFF87949E)
    }

    private fun drawHome(zone: WorldZone) {
        cube(zone.x, 0.95f, zone.z, 3.8f, 1.9f, 3.3f, 0xFFD8D4C2)
        cube(zone.x - 0.92f, 2.15f, zone.z, 2.35f, 0.28f, 3.75f, zone.color, rz = 27f)
        cube(zone.x + 0.92f, 2.15f, zone.z, 2.35f, 0.28f, 3.75f, zone.color, rz = -27f)
        cube(zone.x, 0.85f, zone.z + 1.68f, 0.72f, 1.5f, 0.09f, 0xFF65766C)
        cube(zone.x - 1.15f, 1.05f, zone.z + 1.68f, 0.72f, 0.72f, 0.08f, 0xFF8CC4C1)
        cube(zone.x + 1.15f, 1.05f, zone.z + 1.68f, 0.72f, 0.72f, 0.08f, 0xFF8CC4C1)
        cylinder(zone.x, 0.42f, zone.z, 0.55f, 0.35f, 0.55f, 0xFFB8D9C0)
    }

    private fun drawClinic(zone: WorldZone) {
        cube(zone.x, 1.2f, zone.z, 4.2f, 2.4f, 3.4f, 0xFFE0E2DC)
        cube(zone.x, 2.5f, zone.z, 4.5f, 0.25f, 3.7f, zone.color)
        cube(zone.x, 1.45f, zone.z + 1.73f, 0.34f, 1.35f, 0.08f, 0xFFD56462)
        cube(zone.x, 1.45f, zone.z + 1.74f, 1.35f, 0.34f, 0.08f, 0xFFD56462)
        for (side in listOf(-1f, 1f)) {
            cube(zone.x + side * 1.25f, 0.95f, zone.z + 1.73f, 0.85f, 0.75f, 0.07f, 0xFF6D8E96)
        }
        cube(zone.x, 0.15f, zone.z + 2f, 2.6f, 0.25f, 0.65f, 0xFF9BA9A2)
    }

    private fun drawStudio(zone: WorldZone) {
        cube(zone.x, 1.35f, zone.z, 4.4f, 2.7f, 3.5f, 0xFF4A4B60)
        cube(zone.x, 2.85f, zone.z, 4.75f, 0.3f, 3.85f, zone.color)
        cube(zone.x, 1.45f, zone.z + 1.78f, 2.65f, 1.4f, 0.08f, 0xFF172A36)
        cube(zone.x, 1.45f, zone.z + 1.83f, 2.25f, 1.05f, 0.035f, 0xFF67C5D1)
        for (line in 0 until 3) cube(zone.x - 0.45f + line * 0.15f, 1.72f - line * 0.28f, zone.z + 1.86f, 1.25f - line * 0.18f, 0.07f, 0.025f, 0xFFD6E5DF)
        cylinder(zone.x + 1.45f, 3.75f, zone.z, 0.12f, 1.55f, 0.12f, 0xFFAAB3C5)
        cylinder(zone.x + 1.45f, 4.5f, zone.z, 0.42f, 0.18f, 0.42f, zone.color)
    }

    private fun drawAgent(active: Boolean, accent: Long) {
        val swing = if (agentMoving || active) sin(elapsed * 8f) * 0.18f else 0f
        if (active) {
            val pulse = 1.2f + sin(elapsed * 3f) * 0.12f
            cylinder(agentX, 0.035f, agentZ, pulse, 0.06f, pulse, accent)
        } else cylinder(agentX, 0.025f, agentZ, 0.8f, 0.04f, 0.8f, 0xFF1D2929)
        val y = 0.25f
        cube(agentX - 0.18f, y + 0.34f, agentZ + swing, 0.22f, 0.68f, 0.24f, 0xFF303C51, agentYaw)
        cube(agentX + 0.18f, y + 0.34f, agentZ - swing, 0.22f, 0.68f, 0.24f, 0xFF303C51, agentYaw)
        cube(agentX, y + 1.02f, agentZ, 0.72f, 0.85f, 0.42f, 0xFF657DE8, agentYaw)
        cube(agentX - 0.47f, y + 1.02f, agentZ - swing, 0.16f, 0.72f, 0.18f, 0xFFD6B79E, agentYaw)
        cube(agentX + 0.47f, y + 1.02f, agentZ + swing, 0.16f, 0.72f, 0.18f, 0xFFD6B79E, agentYaw)
        cylinder(agentX, y + 1.72f, agentZ, 0.58f, 0.62f, 0.58f, 0xFFDDBEA4)
        cylinder(agentX, y + 2.02f, agentZ, 0.61f, 0.18f, 0.61f, 0xFF28343C)
    }

    private fun cube(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long, ry: Float = 0f, rz: Float = 0f) {
        transform(x, y, z, sx, sy, sz, ry, rz)
        draw(cube, color)
    }

    private fun cylinder(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long) {
        transform(x, y, z, sx, sy, sz, 0f, 0f)
        draw(cylinder, color)
    }

    private fun transform(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, ry: Float, rz: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (ry != 0f) Matrix.rotateM(model, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(model, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
    }

    private fun draw(mesh: Mesh, color: Long) {
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(modelLocation, 1, false, model, 0)
        GLES30.glUniform4f(colorLocation,
            ((color shr 16) and 0xFF).toFloat() / 255f,
            ((color shr 8) and 0xFF).toFloat() / 255f,
            (color and 0xFF).toFloat() / 255f, 1f)
        mesh.draw()
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source); GLES30.glCompileShader(shader)
            val status = IntArray(1); GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "Falha no shader: ${GLES30.glGetShaderInfoLog(shader)}" }
            return shader
        }
        val result = GLES30.glCreateProgram()
        GLES30.glAttachShader(result, compile(GLES30.GL_VERTEX_SHADER, vertex))
        GLES30.glAttachShader(result, compile(GLES30.GL_FRAGMENT_SHADER, fragment))
        GLES30.glBindAttribLocation(result, 0, "aPosition")
        GLES30.glBindAttribLocation(result, 1, "aNormal")
        GLES30.glLinkProgram(result)
        val status = IntArray(1); GLES30.glGetProgramiv(result, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Falha no programa OpenGL: ${GLES30.glGetProgramInfoLog(result)}" }
        return result
    }

    private companion object {
        const val VERTEX_SHADER = """#version 300 es
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec3 aNormal;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec3 vWorld;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vWorld = world.xyz;
                vNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        const val FRAGMENT_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vNormal;
            in vec3 vWorld;
            uniform vec4 uColor;
            uniform vec3 uCamera;
            out vec4 fragColor;
            void main() {
                vec3 light = normalize(vec3(-0.45, 0.85, 0.3));
                float diffuse = max(dot(normalize(vNormal), light), 0.0);
                float value = 0.48 + diffuse * 0.52;
                vec3 lit = uColor.rgb * value;
                float fog = smoothstep(28.0, 68.0, distance(vWorld, uCamera));
                fragColor = vec4(mix(lit, vec3(0.055, 0.075, 0.09), fog), 1.0);
            }
        """
    }
}

private fun cubeVertices(): FloatArray {
    val vertices = mutableListOf<Float>()
    val faces = listOf(
        floatArrayOf(0f, 0f, 1f) to arrayOf(floatArrayOf(-.5f,-.5f,.5f), floatArrayOf(.5f,-.5f,.5f), floatArrayOf(.5f,.5f,.5f), floatArrayOf(-.5f,.5f,.5f)),
        floatArrayOf(0f, 0f, -1f) to arrayOf(floatArrayOf(.5f,-.5f,-.5f), floatArrayOf(-.5f,-.5f,-.5f), floatArrayOf(-.5f,.5f,-.5f), floatArrayOf(.5f,.5f,-.5f)),
        floatArrayOf(1f, 0f, 0f) to arrayOf(floatArrayOf(.5f,-.5f,.5f), floatArrayOf(.5f,-.5f,-.5f), floatArrayOf(.5f,.5f,-.5f), floatArrayOf(.5f,.5f,.5f)),
        floatArrayOf(-1f, 0f, 0f) to arrayOf(floatArrayOf(-.5f,-.5f,-.5f), floatArrayOf(-.5f,-.5f,.5f), floatArrayOf(-.5f,.5f,.5f), floatArrayOf(-.5f,.5f,-.5f)),
        floatArrayOf(0f, 1f, 0f) to arrayOf(floatArrayOf(-.5f,.5f,.5f), floatArrayOf(.5f,.5f,.5f), floatArrayOf(.5f,.5f,-.5f), floatArrayOf(-.5f,.5f,-.5f)),
        floatArrayOf(0f, -1f, 0f) to arrayOf(floatArrayOf(-.5f,-.5f,-.5f), floatArrayOf(.5f,-.5f,-.5f), floatArrayOf(.5f,-.5f,.5f), floatArrayOf(-.5f,-.5f,.5f)),
    )
    faces.forEach { (normal, corners) -> corners.forEach { corner -> vertices.addAll(corner.toList()); vertices.addAll(normal.toList()) } }
    return vertices.toFloatArray()
}

private fun cubeIndices(): ShortArray {
    val indices = mutableListOf<Short>()
    for (face in 0 until 6) {
        val base = (face * 4).toShort()
        listOf(0, 1, 2, 0, 2, 3).forEach { indices.add((base + it).toShort()) }
    }
    return indices.toShortArray()
}

private fun cylinderVertices(segments: Int): FloatArray {
    val vertices = mutableListOf<Float>()
    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        vertices.addAll(listOf(x, y, z, nx, ny, nz))
    }
    for (index in 0 until segments) {
        val a = index * Math.PI * 2 / segments; val b = (index + 1) * Math.PI * 2 / segments
        val ax = cos(a).toFloat() * .5f; val az = sin(a).toFloat() * .5f
        val bx = cos(b).toFloat() * .5f; val bz = sin(b).toFloat() * .5f
        val anx = cos(a).toFloat(); val anz = sin(a).toFloat()
        val bnx = cos(b).toFloat(); val bnz = sin(b).toFloat()
        vertex(ax,-.5f,az,anx,0f,anz); vertex(bx,-.5f,bz,bnx,0f,bnz); vertex(bx,.5f,bz,bnx,0f,bnz)
        vertex(ax,-.5f,az,anx,0f,anz); vertex(bx,.5f,bz,bnx,0f,bnz); vertex(ax,.5f,az,anx,0f,anz)
        vertex(0f,.5f,0f,0f,1f,0f); vertex(bx,.5f,bz,0f,1f,0f); vertex(ax,.5f,az,0f,1f,0f)
        vertex(0f,-.5f,0f,0f,-1f,0f); vertex(ax,-.5f,az,0f,-1f,0f); vertex(bx,-.5f,bz,0f,-1f,0f)
    }
    return vertices.toFloatArray()
}

private fun cylinderIndices(segments: Int): ShortArray = ShortArray(segments * 12) { it.toShort() }
