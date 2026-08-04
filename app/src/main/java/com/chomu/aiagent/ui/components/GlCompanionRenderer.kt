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

    @Volatile var agentState: AgentState = AgentState.IDLE
    @Volatile var mesh: ObjMesh? = null
    @Volatile var autoRotate: Boolean = true
    @Volatile var userRotationY: Float = 0f

    private var program = 0
    private var vboIds = IntArray(3)
    private var vertexCount = 0

    private val mvpMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var startTime = System.currentTimeMillis()
    private var autoRotAngle = 0f

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

    // Enhanced vertex shader with body-region-aware animations
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
        out float vRegion;

        // Region helpers (character is normalized to [-1,1])
        float headRegion(float y)  { return smoothstep(0.50, 0.75, y); }
        float hairRegion(float y)  { return smoothstep(0.65, 0.90, y); }
        float torsoRegion(float y) { return smoothstep(0.05, 0.45, y) * (1.0 - smoothstep(0.45, 0.65, y)); }
        float armRegion(float y, float x) {
            return smoothstep(0.10, 0.50, y) * (1.0 - smoothstep(0.50, 0.70, y))
                   * smoothstep(0.22, 0.38, abs(x));
        }
        float legRegion(float y)   { return smoothstep(-1.0, -0.40, y) * (1.0 - smoothstep(-0.40, -0.10, y)); }

        // Smooth 2D rotation
        vec2 rot2D(vec2 v, float a) {
            return vec2(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a));
        }

        void main() {
            vec3 pos = aPosition;
            float t = uTime;
            float head  = headRegion(pos.y);
            float hair  = hairRegion(pos.y);
            float torso = torsoRegion(pos.y);
            float arm   = armRegion(pos.y, pos.x);
            float leg   = legRegion(pos.y);

            if (uAnimState == 0) {
                // ── IDLE: breathing + micro-sway + hair physics ──

                // Chest breathe: torso expands on Y and Z
                float breathe = sin(t * 1.15) * 0.022;
                pos.y += breathe * torso;
                pos.z += breathe * 0.6 * torso;

                // Whole-body sway
                pos.x += sin(t * 0.45) * 0.007;

                // Head subtle nod
                vec2 headNod = rot2D(vec2(pos.y, pos.z), sin(t * 0.7) * 0.025 * head);
                pos.y = mix(pos.y, headNod.x, head);
                pos.z = mix(pos.z, headNod.y, head);

                // Hair strands sway with lag
                pos.x += sin(t * 1.1 + pos.y * 4.0) * 0.012 * hair;
                pos.z += cos(t * 0.9 + pos.y * 3.0) * 0.008 * hair;

                // Weight shift on legs
                pos.x += sin(t * 0.3) * 0.005 * leg;

            } else if (uAnimState == 1) {
                // ── LISTENING: head tilt + lean-in + ear perk ──

                // Torso leans slightly forward
                float upperBody = smoothstep(-0.5, 0.6, pos.y);
                pos.z += 0.03 * upperBody;

                // Head tilts to one side, oscillates gently
                float tiltAngle = sin(t * 1.6) * 0.06;
                vec2 headXY = rot2D(vec2(pos.x, pos.y), tiltAngle * head);
                pos.x = mix(pos.x, headXY.x, head);
                pos.y = mix(pos.y, headXY.y, head);

                // Curious head bob
                pos.y += sin(t * 2.0) * 0.015 * head;

                // Hair reacts to head movement
                pos.x += sin(t * 1.9 + pos.y * 5.0) * 0.015 * hair;

                // Arms: one arm gesturing (listening posture)
                float rightArm = arm * step(0.0, pos.x);
                pos.y += sin(t * 1.8 + 1.0) * 0.02 * rightArm;
                pos.z += cos(t * 1.5) * 0.012 * rightArm;

            } else if (uAnimState == 2) {
                // ── TALKING: lip-sync + head animation + gestures ──

                // Jaw/lower-face oscillation (lip sync)
                float jawY = smoothstep(0.52, 0.62, pos.y) * (1.0 - smoothstep(0.62, 0.72, pos.y));
                float jaw  = abs(sin(t * 8.5)) * 0.018 * jawY;
                pos.y -= jaw;
                pos.z += jaw * 0.5;

                // Head nod with speech rhythm
                float nodAngle = sin(t * 4.0) * 0.04;
                vec2 headTilt = rot2D(vec2(pos.y, pos.z), nodAngle * head);
                pos.y = mix(pos.y, headTilt.x, head * 0.6);
                pos.z = mix(pos.z, headTilt.y, head * 0.6);

                // Hair energetic movement
                pos.x += sin(t * 4.0 + pos.y * 3.0) * 0.018 * hair;
                pos.z += cos(t * 3.5 + pos.x * 2.0) * 0.012 * hair;

                // Arm gesture waves (hands talking)
                pos.y += sin(t * 3.8 + pos.x * 2.5) * 0.028 * arm;
                pos.z += cos(t * 3.2 + pos.x * 3.0) * 0.02  * arm;
                pos.x += sin(t * 2.5) * 0.015 * arm;

                // Whole body micro-moves with speech
                pos.x += cos(t * 2.2) * 0.006;

            } else if (uAnimState == 3) {
                // ── WORKING: forward lean + scan wave + typing ──

                // Whole upper body leans forward (focused posture)
                float upperLean = smoothstep(-0.6, 0.5, pos.y);
                pos.z += 0.06 * upperLean;
                pos.y -= 0.015 * upperLean;  // slight hunch

                // Head nods down (reading/focused)
                float lookDown = 0.08;
                vec2 lookDn = rot2D(vec2(pos.y, pos.z), lookDown * head);
                pos.y = mix(pos.y, lookDn.x, head * 0.7);
                pos.z = mix(pos.z, lookDn.y, head * 0.7);

                // Typing rhythm on forearms
                float typing = step(0.0, sin(t * 9.0)) * 0.014;
                pos.y -= typing * arm;
                pos.z += typing * 0.5 * arm;

                // Vertical scan wave across whole body
                float scanY = mod(t * 1.2, 4.0) - 2.0;
                float scanW = 1.0 - smoothstep(0.0, 0.25, abs(pos.y - scanY));
                pos.x += sin(t * 14.0 + pos.y * 9.0) * 0.008 * scanW;

                // Hair floats slightly (energy field)
                pos.x += sin(t * 2.0 + pos.y * 4.0) * 0.01 * hair;
            }

            vFragPos = vec3(uModel * vec4(pos, 1.0));
            vNormal  = normalize(mat3(transpose(inverse(uModel))) * aNormal);
            vY       = pos.y;
            vRegion  = headRegion(pos.y);
            gl_Position = uMVP * vec4(pos, 1.0);
        }
    """.trimIndent()

    // Enhanced fragment shader: Phong + rim + fresnel + holographic overlay
    private val FRAG = """
        #version 300 es
        precision mediump float;
        in vec3 vNormal;
        in vec3 vFragPos;
        in float vY;
        in float vRegion;
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
            vec3 viewDir = normalize(uViewPos - vFragPos);

            // Key light (warm)
            vec3 lightDir = normalize(uLightPos - vFragPos);
            float diff = max(dot(norm, lightDir), 0.0);
            vec3 diffuse = diff * vec3(1.0, 0.95, 0.90) * 0.85;

            // Fill light (cool, from left side)
            vec3 fillDir = normalize(vec3(-uLightPos.x * 0.8, uLightPos.y * 0.3, -uLightPos.z * 0.5) - vFragPos);
            float fillD = max(dot(norm, fillDir), 0.0) * 0.25;
            vec3 fill = fillD * vec3(0.45, 0.55, 0.75);

            // Specular (Blinn-Phong, tighter highlight on top)
            vec3 halfV = normalize(lightDir + viewDir);
            float spec = pow(max(dot(norm, halfV), 0.0), 80.0) * 0.75;
            vec3 specular = spec * mix(vec3(1.0), uGlowColor, 0.4);

            // Ambient with slight upward gradient
            float ambientGrad = mix(0.14, 0.22, clamp(vY * 0.5 + 0.5, 0.0, 1.0));
            vec3 ambient = vec3(ambientGrad) * mix(vec3(0.9, 0.92, 1.0), uGlowColor * 0.3, 0.25);

            // Rim/Fresnel glow
            float fresnel = pow(1.0 - max(dot(viewDir, norm), 0.0), 3.0);
            vec3 rim = uGlowColor * fresnel * 0.5;

            // State-specific glow pulse
            float pulse = 0.0;
            if (uAnimState == 0) {
                pulse = (sin(uTime * 1.2) * 0.5 + 0.5) * 0.2;
            } else if (uAnimState == 1) {
                // Pulsing "listening" waves up from bottom
                float wave = sin(uTime * 3.0 - vY * 5.0) * 0.5 + 0.5;
                pulse = wave * 0.45;
            } else if (uAnimState == 2) {
                // Fast lip-sync flash
                pulse = abs(sin(uTime * 9.0)) * 0.55;
            } else if (uAnimState == 3) {
                // Vertical scan stripe
                float scanY = mod(uTime * 1.2, 4.0) - 2.0;
                float stripe = 1.0 - smoothstep(0.0, 0.22, abs(vY - scanY));
                pulse = stripe * 0.8 + (sin(uTime * 8.0 + vY * 6.0) * 0.5 + 0.5) * 0.2;
            }

            vec3 glowContrib = uGlowColor * uGlowIntensity * (pulse * 0.8 + fresnel * 0.3);

            // Edge darkening for depth
            float edgeMask = 1.0 - fresnel * 0.35;

            vec3 result = (ambient + diffuse + fill) * uBaseColor.rgb
                        + specular + rim + glowContrib;

            fragColor = vec4(result * edgeMask, uBaseColor.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.02f, 0.04f, 0.10f, 1f)
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
        // Tighter FOV for less distortion, slightly closer camera
        Matrix.perspectiveM(projMatrix, 0, 38f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val currentMesh = mesh ?: return
        if (vertexCount == 0) uploadMesh(currentMesh)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        // Very slow auto-rotate for idle showcase feel; stops when user grabs
        if (autoRotate) autoRotAngle += 0.12f

        // Camera: slightly above center, looking at character
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0.15f, 3.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, autoRotAngle + userRotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES30.glUniform1f(uTime, elapsed)
        GLES30.glUniform1i(uAnimState, agentState.ordinal)
        GLES30.glUniform3f(uLightPos, 2.5f, 3.5f, 4f)
        GLES30.glUniform3f(uViewPos, 0f, 0.15f, 3.2f)

        val (baseR, baseG, baseB, glowR, glowG, glowB, glowI) = stateColors()
        GLES30.glUniform4f(uBaseColor, baseR, baseG, baseB, 1f)
        GLES30.glUniform3f(uGlowColor, glowR, glowG, glowB)
        GLES30.glUniform1f(uGlowIntensity, glowI)

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
        AgentState.IDLE      -> StateColors(0.74f, 0.78f, 0.88f, 0.29f, 0.56f, 0.94f, 0.35f)
        AgentState.LISTENING -> StateColors(0.62f, 0.85f, 0.95f, 0.00f, 0.75f, 1.00f, 0.90f)
        AgentState.TALKING   -> StateColors(0.65f, 0.95f, 0.75f, 0.15f, 0.95f, 0.45f, 1.10f)
        AgentState.WORKING   -> StateColors(0.96f, 0.82f, 0.52f, 1.00f, 0.55f, 0.05f, 1.30f)
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
        aPosition     = GLES30.glGetAttribLocation(program, "aPosition")
        aNormal       = GLES30.glGetAttribLocation(program, "aNormal")
        uMVP          = GLES30.glGetUniformLocation(program, "uMVP")
        uModel        = GLES30.glGetUniformLocation(program, "uModel")
        uTime         = GLES30.glGetUniformLocation(program, "uTime")
        uAnimState    = GLES30.glGetUniformLocation(program, "uAnimState")
        uLightPos     = GLES30.glGetUniformLocation(program, "uLightPos")
        uViewPos      = GLES30.glGetUniformLocation(program, "uViewPos")
        uBaseColor    = GLES30.glGetUniformLocation(program, "uBaseColor")
        uGlowColor    = GLES30.glGetUniformLocation(program, "uGlowColor")
        uGlowIntensity= GLES30.glGetUniformLocation(program, "uGlowIntensity")
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
            if (status[0] == 0) Log.e(TAG, "Link error: ${GLES30.glGetProgramInfoLog(prog)}")
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
        if (status[0] == 0) Log.e(TAG, "Shader error [$type]: ${GLES30.glGetShaderInfoLog(s)}")
        return s
    }
}
