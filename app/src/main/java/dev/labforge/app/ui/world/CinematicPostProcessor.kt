package dev.labforge.app.ui.world

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One-pass 70mm film finish for the existing GLES 3 world renderer.
 *
 * The scene is rendered into a bounded off-screen target and then composited with
 * a subtle film curve, fine grain, restrained warm halation and horizontal flare.
 * If FBO allocation or shader compilation fails, callers keep rendering directly
 * to the display surface; this class never makes the scene unavailable.
 */
internal class CinematicPostProcessor {
    private var program = 0
    private var framebuffer = 0
    private var colorTexture = 0
    private var depthBuffer = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var outputWidth = 1
    private var outputHeight = 1
    private var active = false
    private var positionLocation = -1
    private var uvLocation = -1
    private var sceneLocation = -1
    private var resolutionLocation = -1
    private var timeLocation = -1
    private var grainLocation = -1
    private var halationLocation = -1
    private var flareLocation = -1
    private var frameTime = 0f

    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f,
            ))
            position(0)
        }

    fun create() {
        release()
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) GLES30.glDeleteShader(vertex)
            if (fragment != 0) GLES30.glDeleteShader(fragment)
            return
        }
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Post-process shader link failed: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            program = 0
            return
        }
        positionLocation = GLES30.glGetAttribLocation(program, "aPosition")
        uvLocation = GLES30.glGetAttribLocation(program, "aUv")
        sceneLocation = GLES30.glGetUniformLocation(program, "uScene")
        resolutionLocation = GLES30.glGetUniformLocation(program, "uResolution")
        timeLocation = GLES30.glGetUniformLocation(program, "uTime")
        grainLocation = GLES30.glGetUniformLocation(program, "uGrain")
        halationLocation = GLES30.glGetUniformLocation(program, "uHalation")
        flareLocation = GLES30.glGetUniformLocation(program, "uFlare")
    }

    fun resize(width: Int, height: Int) {
        outputWidth = width.coerceAtLeast(1)
        outputHeight = height.coerceAtLeast(1)
        // Bound GPU memory and bandwidth on mid-range phones. Aspect ratio is preserved.
        val budget = CinematicRenderBudget.targetSize(outputWidth, outputHeight)
        val nextWidth = budget.width
        val nextHeight = budget.height
        if (nextWidth == targetWidth && nextHeight == targetHeight && framebuffer != 0) return
        releaseTarget()
        targetWidth = nextWidth
        targetHeight = nextHeight
        if (program == 0) return

        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glGenTextures(1, ids, 0)
        colorTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, targetWidth, targetHeight,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )

        GLES30.glGenRenderbuffers(1, ids, 0)
        depthBuffer = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthBuffer)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, targetWidth, targetHeight)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorTexture, 0,
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthBuffer,
        )
        val complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        if (!complete) {
            Log.w(TAG, "Off-screen target unavailable; using direct GLES rendering")
            releaseTarget()
        }
    }

    fun beginFrame(): Boolean {
        active = framebuffer != 0 && program != 0
        if (active) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glViewport(0, 0, targetWidth, targetHeight)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, outputWidth, outputHeight)
        }
        return active
    }

    fun endFrame() {
        if (!active) return
        active = false
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outputWidth, outputHeight)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glUniform1i(sceneLocation, 0)
        GLES30.glUniform2f(resolutionLocation, targetWidth.toFloat(), targetHeight.toFloat())
        frameTime += 1f / 60f
        GLES30.glUniform1f(timeLocation, frameTime)
        // Keep the 70mm finish restrained; users can tune the full look in the later UI phase.
        GLES30.glUniform1f(grainLocation, 0.018f)
        GLES30.glUniform1f(halationLocation, 0.22f)
        GLES30.glUniform1f(flareLocation, 0.10f)

        vertices.position(0)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glEnableVertexAttribArray(uvLocation)
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES30.glVertexAttribPointer(uvLocation, 2, GLES30.GL_FLOAT, false, 16, vertices)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(positionLocation)
        GLES30.glDisableVertexAttribArray(uvLocation)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glUseProgram(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun release() {
        releaseTarget()
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
        active = false
    }

    private fun releaseTarget() {
        val ids = intArrayOf(framebuffer, colorTexture, depthBuffer)
        if (framebuffer != 0) GLES30.glDeleteFramebuffers(1, ids, 0)
        if (colorTexture != 0) GLES30.glDeleteTextures(1, ids, 1)
        if (depthBuffer != 0) GLES30.glDeleteRenderbuffers(1, ids, 2)
        framebuffer = 0
        colorTexture = 0
        depthBuffer = 0
        targetWidth = 0
        targetHeight = 0
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Post-process shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private companion object {
        const val TAG = "GirassolFilm"

        const val VERTEX_SHADER = """
            #version 300 es
            in vec2 aPosition;
            in vec2 aUv;
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uScene;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform float uGrain;
            uniform float uHalation;
            uniform float uFlare;
            out vec4 fragColor;

            float noise(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7)) + uTime * 17.13) * 43758.5453);
            }

            void main() {
                vec2 texel = 1.0 / max(uResolution, vec2(1.0));
                vec3 base = texture(uScene, vUv).rgb;
                float luminance = dot(base, vec3(0.2126, 0.7152, 0.0722));
                float highlight = max(luminance - 0.70, 0.0);

                vec3 redFringe = texture(uScene, clamp(vUv + vec2(texel.x * 0.85, 0.0), 0.0, 1.0)).rgb;
                float warmHalo = highlight * uHalation;
                vec3 haloColor = vec3(warmHalo * 1.12, warmHalo * 0.42, warmHalo * 0.16);

                float horizontalEnergy = max(highlight, texture(uScene, clamp(vUv + vec2(texel.x * 2.0, 0.0), 0.0, 1.0)).r - 0.72);
                float flare = smoothstep(0.02, 0.28, horizontalEnergy) * uFlare * (1.0 - abs(vUv.y - 0.5) * 1.6);
                vec3 film = mix(base, vec3(redFringe.r, base.g, base.b), 0.08);
                film += haloColor + vec3(flare * 0.28, flare * 0.19, flare * 0.12);

                vec2 centered = vUv * 2.0 - 1.0;
                float vignette = smoothstep(1.45, 0.35, dot(centered * vec2(0.82, 1.0), centered * vec2(0.82, 1.0)));
                film *= mix(0.82, 1.0, vignette);
                film = film / (film + vec3(0.22));
                film = pow(max(film, vec3(0.0)), vec3(0.94));
                film += (noise(gl_FragCoord.xy) - 0.5) * uGrain;
                fragColor = vec4(clamp(film, 0.0, 1.0), 1.0);
            }
        """
    }
}
