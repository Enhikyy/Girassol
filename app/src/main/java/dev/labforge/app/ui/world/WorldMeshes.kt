package dev.labforge.app.ui.world

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

internal class GLMesh(vertices: FloatArray, indices: ShortArray) {
    private val vao: Int
    private val vbo: Int
    private val ibo: Int
    private val indexCount = indices.size

    init {
        val arrays = IntArray(1)
        GLES30.glGenVertexArrays(1, arrays, 0)
        vao = arrays[0]
        GLES30.glGenBuffers(1, arrays, 0)
        vbo = arrays[0]
        GLES30.glGenBuffers(1, arrays, 0)
        ibo = arrays[0]
        val vertexBuffer = vertices.toDirectFloatBuffer()
        val indexBuffer = indices.toDirectShortBuffer()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 6 * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 6 * 4, 3 * 4)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
    }

    companion object {
        fun box(): GLMesh {
            val vertices = mutableListOf<Float>()
            val indices = mutableListOf<Short>()
            fun face(normal: Triple<Float, Float, Float>, corners: List<Triple<Float, Float, Float>>) {
                val start = vertices.size / 6
                corners.forEach { p ->
                    vertices += listOf(p.first, p.second, p.third, normal.first, normal.second, normal.third)
                }
                listOf(0, 1, 2, 0, 2, 3).forEach { indices += (start + it).toShort() }
            }
            face(Triple(0f, 0f, 1f), listOf(Triple(-.5f, -.5f, .5f), Triple(.5f, -.5f, .5f), Triple(.5f, .5f, .5f), Triple(-.5f, .5f, .5f)))
            face(Triple(0f, 0f, -1f), listOf(Triple(.5f, -.5f, -.5f), Triple(-.5f, -.5f, -.5f), Triple(-.5f, .5f, -.5f), Triple(.5f, .5f, -.5f)))
            face(Triple(1f, 0f, 0f), listOf(Triple(.5f, -.5f, .5f), Triple(.5f, -.5f, -.5f), Triple(.5f, .5f, -.5f), Triple(.5f, .5f, .5f)))
            face(Triple(-1f, 0f, 0f), listOf(Triple(-.5f, -.5f, -.5f), Triple(-.5f, -.5f, .5f), Triple(-.5f, .5f, .5f), Triple(-.5f, .5f, -.5f)))
            face(Triple(0f, 1f, 0f), listOf(Triple(-.5f, .5f, .5f), Triple(.5f, .5f, .5f), Triple(.5f, .5f, -.5f), Triple(-.5f, .5f, -.5f)))
            face(Triple(0f, -1f, 0f), listOf(Triple(.5f, -.5f, -.5f), Triple(-.5f, -.5f, .5f), Triple(-.5f, .5f, .5f), Triple(.5f, -.5f, .5f)))
            return GLMesh(vertices.toFloatArray(), indices.toShortArray())
        }

        fun cylinder(segments: Int = 14): GLMesh {
            val vertices = mutableListOf<Float>()
            val indices = mutableListOf<Short>()
            for (i in 0 until segments) {
                val a = i.toFloat() / segments * (Math.PI * 2).toFloat()
                val b = (i + 1).toFloat() / segments * (Math.PI * 2).toFloat()
                val ax = cos(a); val az = sin(a); val bx = cos(b); val bz = sin(b)
                val start = vertices.size / 6
                vertices += listOf(ax * .5f, -.5f, az * .5f, ax, 0f, az)
                vertices += listOf(bx * .5f, -.5f, bz * .5f, bx, 0f, bz)
                vertices += listOf(bx * .5f, .5f, bz * .5f, bx, 0f, bz)
                vertices += listOf(ax * .5f, .5f, az * .5f, ax, 0f, az)
                listOf(0, 1, 2, 0, 2, 3).forEach { indices += (start + it).toShort() }
            }
            for (top in listOf(false, true)) {
                val y = if (top) .5f else -.5f
                val normal = if (top) 1f else -1f
                for (i in 0 until segments) {
                    val a = i.toFloat() / segments * (Math.PI * 2).toFloat()
                    val b = (i + 1).toFloat() / segments * (Math.PI * 2).toFloat()
                    val start = vertices.size / 6
                    vertices += listOf(0f, y, 0f, 0f, normal, 0f)
                    vertices += listOf(cos(a) * .5f, y, sin(a) * .5f, 0f, normal, 0f)
                    vertices += listOf(cos(b) * .5f, y, sin(b) * .5f, 0f, normal, 0f)
                    if (top) listOf(0, 1, 2).forEach { indices += (start + it).toShort() }
                    else listOf(0, 2, 1).forEach { indices += (start + it).toShort() }
                }
            }
            return GLMesh(vertices.toFloatArray(), indices.toShortArray())
        }

        fun sphere(latitudes: Int = 7, longitudes: Int = 12): GLMesh {
            val vertices = mutableListOf<Float>()
            val indices = mutableListOf<Short>()
            for (lat in 0..latitudes) {
                val phi = lat.toFloat() / latitudes * Math.PI.toFloat()
                for (lon in 0..longitudes) {
                    val theta = lon.toFloat() / longitudes * (Math.PI * 2).toFloat()
                    val x = sin(phi) * cos(theta)
                    val y = cos(phi)
                    val z = sin(phi) * sin(theta)
                    vertices += listOf(x * .5f, y * .5f, z * .5f, x, y, z)
                }
            }
            for (lat in 0 until latitudes) {
                for (lon in 0 until longitudes) {
                    val first = (lat * (longitudes + 1) + lon).toShort()
                    val second = (first + longitudes + 1).toShort()
                    indices += listOf(first, second, (first + 1).toShort(), second, (second + 1).toShort(), (first + 1).toShort())
                }
            }
            return GLMesh(vertices.toFloatArray(), indices.toShortArray())
        }
    }
}

internal class WorldShader {
    val program: Int
    private val mvp: Int
    private val model: Int
    private val color: Int
    private val camera: Int
    private val lightDirection: Int
    private val lightColor: Int
    private val fogColor: Int
    private val emissive: Int

    init {
        program = GLES30.glCreateProgram()
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glBindAttribLocation(program, 1, "aNormal")
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) error("Falha ao vincular shaders: ${GLES30.glGetProgramInfoLog(program)}")
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        mvp = GLES30.glGetUniformLocation(program, "uMvp")
        model = GLES30.glGetUniformLocation(program, "uModel")
        color = GLES30.glGetUniformLocation(program, "uColor")
        camera = GLES30.glGetUniformLocation(program, "uCamera")
        lightDirection = GLES30.glGetUniformLocation(program, "uLightDirection")
        lightColor = GLES30.glGetUniformLocation(program, "uLightColor")
        fogColor = GLES30.glGetUniformLocation(program, "uFogColor")
        emissive = GLES30.glGetUniformLocation(program, "uEmissive")
    }

    fun use() = GLES30.glUseProgram(program)

    fun setFrame(camera: WorldVec3, light: WorldVec3, lightRgb: FloatArray, fogRgb: FloatArray) {
        GLES30.glUniform3f(this.camera, camera.x, camera.y, camera.z)
        GLES30.glUniform3f(lightDirection, light.x, light.y, light.z)
        GLES30.glUniform3fv(lightColor, 1, lightRgb, 0)
        GLES30.glUniform3fv(fogColor, 1, fogRgb, 0)
    }

    fun draw(mesh: GLMesh, modelMatrix: FloatArray, mvpMatrix: FloatArray, rgba: FloatArray, glow: Float) {
        GLES30.glUniformMatrix4fv(model, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvp, 1, false, mvpMatrix, 0)
        GLES30.glUniform4fv(color, 1, rgba, 0)
        GLES30.glUniform1f(emissive, glow)
        mesh.draw()
    }

    fun release() = GLES30.glDeleteProgram(program)

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Falha no shader: $log")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SOURCE = """#version 300 es
precision highp float;
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
}"""
        private const val FRAGMENT_SOURCE = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
uniform vec4 uColor;
uniform vec3 uCamera;
uniform vec3 uLightDirection;
uniform vec3 uLightColor;
uniform vec3 uFogColor;
uniform float uEmissive;
out vec4 fragColor;
void main() {
    vec3 normal = normalize(vNormal);
    vec3 light = normalize(-uLightDirection);
    vec3 viewDir = normalize(uCamera - vWorld);
    vec3 halfDir = normalize(light + viewDir);
    float diffuse = max(dot(normal, light), 0.0);
    float specular = pow(max(dot(normal, halfDir), 0.0), 34.0) * 0.22;
    float rim = pow(1.0 - max(dot(normal, viewDir), 0.0), 3.0) * 0.08;
    vec3 base = uColor.rgb;
    vec3 lit = base * (0.30 + diffuse * 0.78) * uLightColor + vec3(specular + rim) + base * uEmissive;
    float distanceToCamera = length(uCamera - vWorld);
    float fog = smoothstep(68.0, 145.0, distanceToCamera);
    fragColor = vec4(mix(lit, uFogColor, fog), uColor.a);
}"""
    }
}

private fun FloatArray.toDirectFloatBuffer(): FloatBuffer = ByteBuffer
    .allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(this@toDirectFloatBuffer); position(0)
    }

private fun ShortArray.toDirectShortBuffer(): ShortBuffer = ByteBuffer
    .allocateDirect(size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
        put(this@toDirectShortBuffer); position(0)
    }
