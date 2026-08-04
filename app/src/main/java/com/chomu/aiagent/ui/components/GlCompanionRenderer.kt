package com.chomu.aiagent.ui.components

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.chomu.aiagent.domain.model.AgentState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GlCompanionRenderer : GLSurfaceView.Renderer {

    private val TAG = "GlCompanionRenderer"

    // State
    @Volatile var agentState: AgentState = AgentState.IDLE
    @Volatile var mesh: ObjMesh? = null
    @Volatile var autoRotate: Boolean = true
    @Volatile var userRotationY: Float = 0f

    // Handles
    private var program = 0
    private var vboIds = IntArray(3)
    private var vertexCount = 0

    // Matrix
    private val mvpMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Time
    private var startTime = System.currentTimeMillis()
    private var autoRotAngle = 0f

    // Attribute/uniform locations (cached after link)
    private var aPosition = 0
    private var aNormal = 0
    private var uMVP = 0
    private var uModel = 0
    private var uTime = 0
    private var uAnimState = 0
    private var uLightPos = 0
    private var uViewPos = 0
    private var uBaseColor = 0
    private var uGlowColor = 0
    private var uGlowIntensity = 0

    private val VERT = """
        #version 300 es
        in vec3 aPosition;
        in vec3 aNormal;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform float uTime;
        uniform int uAnimState;
        out vec3 vNormal;
        out vec3 vFragPos;
        out float vY;

        void main() {
            vec3 pos = aPosition;
            float t = uTime;

            if (uAnimState == 0) {
                // IDLE: gentle breathing + subtle sway
                pos.y += sin(t * 1.3) * 0.035;
                pos.x += sin(t * 0.6) * 0.012;
                pos.z += cos(t * 0.9) * 0.008;
            } else if (uAnimState == 1) {
                // LISTENING: head-tilt + lean-in (upper body more)
                float lean = sin(t * 1.8) * 0.05;
                float tiltFactor = clamp((pos.y + 1.0) * 0.5, 0.0, 1.0);
                pos.x += lean * tiltFactor;
                pos.y += sin(t * 2.2) * 0.018;
                // Subtle wave through body
                pos.z += sin(t * 3.0 + pos.y * 2.5) * 0.01;
            } else if (uAnimState == 2) {
                // TALKING: rapid speech oscillation + nod
                pos.y += sin(t * 9.0) * 0.022;
                pos.x += cos(t * 7.0) * 0.012;
                // jaw-area motion (lower face)
                float jawFactor = clamp((0.3 - pos.y) * 2.0, 0.0, 1.0);
                pos.z += sin(t * 11.0) * 0.018 * jawFactor;
            } else if (uAnimState == 3) {
                // WORKING: scan wave + focus lean
                float wave = sin(t * 6.0 + pos.y * 10.0) * 0.012;
                pos.x += wave;
                pos.z += cos(t * 4.0 + pos.x * 8.0) * 0.008;
                pos.y += sin(t * 2.0) * 0.015;
            }

            vFragPos = vec3(uModel * vec4(pos, 1.0));
            vNormal = normalize(mat3(transpose(inverse(uModel))) * aNormal);
            vY = pos.y;
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    private val FRAG = """
        #version 300 es
        precision mediump float;
        in vec3 vNormal;
        in vec3 vFragPos;
        in float vY;
        uniform vec3 uLightPos;
        uniform vec3 uViewPos;
        uniform vec4 uBaseColor;
        uniform vec3 uGlowColor;
        uniform float uGlowIntensity;
        uniform float uTime;
        uniform int uAnimState;
        out vec4 fragColor;

        void main() {
            vec3 norm = normalize(vNormal);

            // Key light
            vec3 lightDir = normalize(uLightPos - vFragPos);
            float diff = max(dot(norm, lightDir), 0.0);
            vec3 diffuse = diff * vec3(1.0, 0.98, 0.96);

            // Fill light (opposite side, cooler)
            vec3 fillDir = normalize(vec3(-uLightPos.x, uLightPos.y * 0.5, -uLightPos.z) - vFragPos);
            float fillDiff = max(dot(norm, fillDir), 0.0) * 0.3;
            vec3 fill = fillDiff * vec3(0.5, 0.6, 0.8);

            // Specular
            vec3 viewDir = normalize(uViewPos - vFragPos);
            vec3 halfV = normalize(lightDir + viewDir);
            float spec = pow(max(dot(norm, halfV), 0.0), 64.0) * 0.7;
            vec3 specular = spec * vec3(1.0, 1.0, 1.0);

            // Ambient
            vec3 ambient = vec3(0.18, 0.18, 0.22);

            // Rim light for stylized look
            float rim = 1.0 - max(dot(viewDir, norm), 0.0);
            rim = pow(rim, 3.0) * 0.4;
            vec3 rimColor = uGlowColor * rim;

            // Fresnel glow pulse based on state
            float pulse = 0.0;
            if (uAnimState == 0) {
                pulse = (sin(uTime * 1.5) * 0.5 + 0.5) * 0.15;
            } else if (uAnimState == 1) {
                pulse = abs(sin(uTime * 2.8)) * 0.5;
            } else if (uAnimState == 2) {
                pulse = abs(sin(uTime * 9.0)) * 0.6;
            } else if (uAnimState == 3) {
                // Scan line effect
                float scan = sin(uTime * 6.0 + vY * 12.0) * 0.5 + 0.5;
                pulse = scan * 0.7;
            }

            vec3 glow = uGlowColor * uGlowIntensity * (pulse + rim);
            vec3 result = (ambient + diffuse + fill) * uBaseColor.rgb + specular + glow + rimColor;

            // Vignette at mesh edges (depth feel)
            float edgeFade = 1.0 - rim * 0.3;
            fragColor = vec4(result * edgeFade, uBaseColor.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        program = buildShader(VERT, FRAG)
        cacheLocations()
        startTime = System.currentTimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val currentMesh = mesh ?: return
        if (vertexCount == 0) uploadMesh(currentMesh)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        if (autoRotate) autoRotAngle += 0.3f

        // Camera position
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0.3f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, autoRotAngle + userRotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        GLES30.glUseProgram(program)

        // Matrices
        GLES30.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES30.glUniform1f(uTime, elapsed)
        GLES30.glUniform1i(uAnimState, agentState.ordinal)

        // Lighting
        GLES30.glUniform3f(uLightPos, 2f, 3f, 4f)
        GLES30.glUniform3f(uViewPos, 0f, 0.3f, 3.5f)

        // Base color + glow by state
        val (baseR, baseG, baseB, glowR, glowG, glowB, glowI) = stateColors()
        GLES30.glUniform4f(uBaseColor, baseR, baseG, baseB, 1f)
        GLES30.glUniform3f(uGlowColor, glowR, glowG, glowB)
        GLES30.glUniform1f(uGlowIntensity, glowI)

        // Bind VBOs
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[0])
        GLES30.glEnableVertexAttribArray(aPosition)
        GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[1])
        GLES30.glEnableVertexAttribArray(aNormal)
        GLES30.glVertexAttribPointer(aNormal, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aNormal)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private data class StateColors(
        val baseR: Float, val baseG: Float, val baseB: Float,
        val glowR: Float, val glowG: Float, val glowB: Float,
        val glowIntensity: Float
    )

    private fun stateColors() = when (agentState) {
        AgentState.IDLE      -> StateColors(0.72f, 0.75f, 0.82f, 0.4f, 0.6f, 1.0f, 0.3f)
        AgentState.LISTENING -> StateColors(0.65f, 0.82f, 0.92f, 0.0f, 0.6f, 1.0f, 0.8f)
        AgentState.TALKING   -> StateColors(0.68f, 0.92f, 0.78f, 0.2f, 1.0f, 0.5f, 1.0f)
        AgentState.WORKING   -> StateColors(0.92f, 0.80f, 0.55f, 1.0f, 0.5f, 0.1f, 1.2f)
    }

    private fun uploadMesh(m: ObjMesh) {
        val ids = IntArray(3)
        GLES30.glGenBuffers(3, ids, 0)
        vboIds = ids

        m.vertexBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 3 * 4, m.vertexBuffer, GLES30.GL_STATIC_DRAW)

        m.normalBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 3 * 4, m.normalBuffer, GLES30.GL_STATIC_DRAW)

        m.texCoordBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[2])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 2 * 4, m.texCoordBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        vertexCount = m.vertexCount
        Log.d(TAG, "Uploaded mesh: $vertexCount vertices to GPU")
    }

    private fun cacheLocations() {
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        aNormal = GLES30.glGetAttribLocation(program, "aNormal")
        uMVP = GLES30.glGetUniformLocation(program, "uMVP")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uAnimState = GLES30.glGetUniformLocation(program, "uAnimState")
        uLightPos = GLES30.glGetUniformLocation(program, "uLightPos")
        uViewPos = GLES30.glGetUniformLocation(program, "uViewPos")
        uBaseColor = GLES30.glGetUniformLocation(program, "uBaseColor")
        uGlowColor = GLES30.glGetUniformLocation(program, "uGlowColor")
        uGlowIntensity = GLES30.glGetUniformLocation(program, "uGlowIntensity")
    }

    private fun buildShader(vert: String, frag: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vert)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, frag)
        return GLES30.glCreateProgram().also { prog ->
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            val status = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(prog)}")
            }
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error [$type]: ${GLES30.glGetShaderInfoLog(s)}")
        }
        return s
    }
}
