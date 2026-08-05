package com.chomu.aiagent.ui.components

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.chomu.aiagent.domain.model.AgentState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlbCompanionRenderer : GLSurfaceView.Renderer {
    private val TAG = "GlbRenderer"

    @Volatile var model: GlbModel? = null
    @Volatile var animController: AnimationController? = null
    @Volatile var agentState: AgentState = AgentState.IDLE
    @Volatile var userRotationY: Float = 0f

    private var program = 0
    private var vboIds = IntArray(5)   // [0]=pos [1]=norm [2]=uv [3]=joints [4]=weights
    private var eboId = 0
    private var vboUploaded = false
    private var indexCount = 0

    private val mvpMatrix   = FloatArray(16)
    private val projMatrix  = FloatArray(16)
    private val viewMatrix  = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix  = FloatArray(16)

    private var startTime  = System.currentTimeMillis()
    private var autoRotAngle = 0f

    // Attribute + uniform locations
    private var aPosition = 0
    private var aNormal   = 0
    private var aJoints   = 0
    private var aWeights  = 0
    private var uMVP           = 0
    private var uModel         = 0
    private var uTime          = 0
    private var uAnimState     = 0
    private var uJointMatrices = 0
    private var uLightPos      = 0
    private var uViewPos       = 0
    private var uBaseColor     = 0
    private var uGlowColor     = 0
    private var uGlowIntensity = 0

    // Identity matrices pre-built so every frame doesn't allocate when controller is null
    private val identityJoints = FloatArray(64 * 16).also { arr ->
        for (i in 0 until 64) {
            arr[i * 16 + 0]  = 1f; arr[i * 16 + 5]  = 1f
            arr[i * 16 + 10] = 1f; arr[i * 16 + 15] = 1f
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vertex shader: GPU skinning with 4 bone weights per vertex
    // ─────────────────────────────────────────────────────────────────────────
    private val VERT = """
        #version 300 es
        precision highp float;

        in vec3 aPosition;
        in vec3 aNormal;
        in vec4 aJoints;
        in vec4 aWeights;

        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform float uTime;
        uniform int uAnimState;
        uniform highp mat4 uJointMatrices[64];

        out vec3 vNormal;
        out vec3 vFragPos;
        out float vY;

        void main() {
            // Build skinning matrix from up to 4 influencing joints
            mat4 skinMat = aWeights.x * uJointMatrices[int(aJoints.x)]
                         + aWeights.y * uJointMatrices[int(aJoints.y)]
                         + aWeights.z * uJointMatrices[int(aJoints.z)]
                         + aWeights.w * uJointMatrices[int(aJoints.w)];

            vec4 skinnedPos  = skinMat * vec4(aPosition, 1.0);
            vec4 skinnedNorm = skinMat * vec4(aNormal,   0.0);

            vFragPos = vec3(uModel * skinnedPos);
            vNormal  = normalize(mat3(transpose(inverse(uModel))) * skinnedNorm.xyz);
            vY       = skinnedPos.y;

            gl_Position = uMVP * skinnedPos;
        }
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────────
    // Fragment shader: Phong + rim + Fresnel + state-reactive glow
    // ─────────────────────────────────────────────────────────────────────────
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
            vec3 norm    = normalize(vNormal);
            vec3 viewDir = normalize(uViewPos - vFragPos);

            // Key light (warm)
            vec3 lightDir = normalize(uLightPos - vFragPos);
            float diff    = max(dot(norm, lightDir), 0.0);
            vec3 diffuse  = diff * vec3(1.0, 0.95, 0.90) * 0.85;

            // Cool fill light from the opposite side
            vec3 fillDir = normalize(vec3(-uLightPos.x * 0.8, uLightPos.y * 0.3, -uLightPos.z * 0.5) - vFragPos);
            float fillD  = max(dot(norm, fillDir), 0.0) * 0.25;
            vec3 fill    = fillD * vec3(0.45, 0.55, 0.75);

            // Blinn-Phong specular
            vec3  halfV   = normalize(lightDir + viewDir);
            float spec    = pow(max(dot(norm, halfV), 0.0), 80.0) * 0.75;
            vec3  specular = spec * mix(vec3(1.0), uGlowColor, 0.4);

            // Ambient with vertical gradient
            float ambGrad = mix(0.14, 0.22, clamp(vY * 0.3 + 0.5, 0.0, 1.0));
            vec3  ambient = vec3(ambGrad) * mix(vec3(0.9, 0.92, 1.0), uGlowColor * 0.3, 0.25);

            // Rim / Fresnel
            float fresnel = pow(1.0 - max(dot(viewDir, norm), 0.0), 3.0);
            vec3  rim     = uGlowColor * fresnel * 0.5;

            // State-specific glow pulse
            float pulse = 0.0;
            if (uAnimState == 0) {
                pulse = (sin(uTime * 1.2) * 0.5 + 0.5) * 0.2;
            } else if (uAnimState == 1) {
                float wave = sin(uTime * 3.0 - vY * 5.0) * 0.5 + 0.5;
                pulse = wave * 0.45;
            } else if (uAnimState == 2) {
                pulse = abs(sin(uTime * 9.0)) * 0.55;
            } else if (uAnimState == 3) {
                float scanY  = mod(uTime * 1.2, 4.0) - 2.0;
                float stripe = 1.0 - smoothstep(0.0, 0.22, abs(vY - scanY));
                pulse = stripe * 0.8 + (sin(uTime * 8.0 + vY * 6.0) * 0.5 + 0.5) * 0.2;
            }

            vec3  glowC    = uGlowColor * uGlowIntensity * (pulse * 0.8 + fresnel * 0.3);
            float edgeMask = 1.0 - fresnel * 0.35;

            vec3 result = (ambient + diffuse + fill) * uBaseColor.rgb + specular + rim + glowC;
            fragColor = vec4(result * edgeMask, uBaseColor.a);
        }
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────────
    // GLSurfaceView.Renderer
    // ─────────────────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.02f, 0.04f, 0.10f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        program = buildShader(VERT, FRAG)
        cacheLocations()
        startTime = System.currentTimeMillis()
        // Invalidate GPU buffers so mesh is re-uploaded into the new GL context
        vboUploaded = false
        vboIds = IntArray(5)
        eboId = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projMatrix, 0, 40f, ratio, 0.01f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val m = model ?: return
        if (!vboUploaded) { uploadModel(m); if (!vboUploaded) return }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        autoRotAngle += 0.10f

        // Camera: centered on torso height (~0.9 m for a 1.8 m character)
        Matrix.setLookAtM(viewMatrix, 0,  0f, 0.9f, 3.5f,  0f, 0.9f, 0f,  0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, autoRotAngle + userRotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix,  0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix,  0, projMatrix,  0, tempMatrix,  0)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMVP,   1, false, mvpMatrix,   0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES30.glUniform1f(uTime, elapsed)
        GLES30.glUniform1i(uAnimState, agentState.ordinal)
        GLES30.glUniform3f(uLightPos, 2.5f, 3.5f, 4f)
        GLES30.glUniform3f(uViewPos,  0f,   0.9f, 3.5f)

        val (baseR, baseG, baseB, glowR, glowG, glowB, glowI) = stateColors()
        GLES30.glUniform4f(uBaseColor, baseR, baseG, baseB, 1f)
        GLES30.glUniform3f(uGlowColor, glowR, glowG, glowB)
        GLES30.glUniform1f(uGlowIntensity, glowI)

        // Joint matrices from AnimationController (or identity fallback)
        val controller = animController
        if (controller != null) {
            controller.agentState = agentState
            val jmats = controller.computeJointMatrices(System.currentTimeMillis())
            GLES30.glUniformMatrix4fv(uJointMatrices, 64, false, jmats, 0)
        } else {
            GLES30.glUniformMatrix4fv(uJointMatrices, 64, false, identityJoints, 0)
        }

        // Bind vertex attributes
        bindFloatVbo(vboIds[0], aPosition, 3)
        bindFloatVbo(vboIds[1], aNormal,   3)
        bindFloatVbo(vboIds[3], aJoints,   4)
        bindFloatVbo(vboIds[4], aWeights,  4)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aNormal)
        GLES30.glDisableVertexAttribArray(aJoints)
        GLES30.glDisableVertexAttribArray(aWeights)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindFloatVbo(id: Int, loc: Int, size: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id)
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, 0, 0)
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

    private fun uploadModel(m: GlbModel) {
        try {
            val buf5 = IntArray(5)
            GLES30.glGenBuffers(5, buf5, 0)
            vboIds = buf5
            val ebo = IntArray(1)
            GLES30.glGenBuffers(1, ebo, 0)
            eboId = ebo[0]

            // Positions
            m.positionBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 3 * 4, m.positionBuffer, GLES30.GL_STATIC_DRAW)

            // Normals
            m.normalBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[1])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 3 * 4, m.normalBuffer, GLES30.GL_STATIC_DRAW)

            // TexCoords (vboIds[2] reserved, not yet used in shader)
            m.texCoordBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[2])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 2 * 4, m.texCoordBuffer, GLES30.GL_STATIC_DRAW)

            // Joints: ShortBuffer (UBYTE values stored as shorts) → float for vertex attrib
            m.jointsBuffer.position(0)
            val jf = FloatArray(m.vertexCount * 4)
            for (i in 0 until m.vertexCount * 4) {
                jf[i] = (m.jointsBuffer.get().toInt() and 0xFFFF).toFloat()
            }
            val jfBuf = ByteBuffer.allocateDirect(jf.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(jf); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[3])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 4 * 4, jfBuf, GLES30.GL_STATIC_DRAW)

            // Weights
            m.weightsBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[4])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 4 * 4, m.weightsBuffer, GLES30.GL_STATIC_DRAW)

            // Index buffer (EBO)
            m.indexBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indexCount * 2, m.indexBuffer, GLES30.GL_STATIC_DRAW)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

            indexCount = m.indexCount
            vboUploaded = true
            Log.d(TAG, "GLB uploaded: ${m.vertexCount} verts, ${m.indexCount} indices, ${m.joints.size} joints")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload GLB model to GPU", e)
            vboUploaded = false
        }
    }

    private fun cacheLocations() {
        aPosition      = GLES30.glGetAttribLocation(program, "aPosition")
        aNormal        = GLES30.glGetAttribLocation(program, "aNormal")
        aJoints        = GLES30.glGetAttribLocation(program, "aJoints")
        aWeights       = GLES30.glGetAttribLocation(program, "aWeights")
        uMVP           = GLES30.glGetUniformLocation(program, "uMVP")
        uModel         = GLES30.glGetUniformLocation(program, "uModel")
        uTime          = GLES30.glGetUniformLocation(program, "uTime")
        uAnimState     = GLES30.glGetUniformLocation(program, "uAnimState")
        uJointMatrices = GLES30.glGetUniformLocation(program, "uJointMatrices[0]")
        uLightPos      = GLES30.glGetUniformLocation(program, "uLightPos")
        uViewPos       = GLES30.glGetUniformLocation(program, "uViewPos")
        uBaseColor     = GLES30.glGetUniformLocation(program, "uBaseColor")
        uGlowColor     = GLES30.glGetUniformLocation(program, "uGlowColor")
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
        if (status[0] == 0) Log.e(TAG, "Shader error [type=$type]: ${GLES30.glGetShaderInfoLog(s)}")
        return s
    }
}
