package com.chomu.aiagent.ui.components

import android.opengl.Matrix
import android.util.Log
import com.chomu.aiagent.domain.model.AgentState
import org.json.JSONObject
import kotlin.math.*

// ── Data types ───────────────────────────────────────────────────────────────

data class AnimFrame(
    val time: Float,
    val rotations: Map<String, FloatArray>   // joint name -> quaternion [x,y,z,w]
)

data class AnimClip(
    val name: String,
    val duration: Float,
    val frames: List<AnimFrame>,
    val loop: Boolean = true
)

// ── Math helpers ─────────────────────────────────────────────────────────────

object QuatMath {
    fun identity() = floatArrayOf(0f, 0f, 0f, 1f)

    fun multiply(q1: FloatArray, q2: FloatArray): FloatArray {
        val x1=q1[0]; val y1=q1[1]; val z1=q1[2]; val w1=q1[3]
        val x2=q2[0]; val y2=q2[1]; val z2=q2[2]; val w2=q2[3]
        return floatArrayOf(
            w1*x2 + x1*w2 + y1*z2 - z1*y2,
            w1*y2 - x1*z2 + y1*w2 + z1*x2,
            w1*z2 + x1*y2 - y1*x2 + z1*w2,
            w1*w2 - x1*x2 - y1*y2 - z1*z2
        )
    }

    // Rotation around axis by angle (radians)
    fun fromAxisAngle(ax: Float, ay: Float, az: Float, angle: Float): FloatArray {
        val s = sin(angle / 2f)
        return floatArrayOf(ax*s, ay*s, az*s, cos(angle / 2f))
    }

    fun slerp(q1: FloatArray, q2: FloatArray, t: Float): FloatArray {
        var dot = q1[0]*q2[0] + q1[1]*q2[1] + q1[2]*q2[2] + q1[3]*q2[3]
        val q2f = if (dot < 0f) floatArrayOf(-q2[0],-q2[1],-q2[2],-q2[3]) else q2.copyOf()
        dot = abs(dot)
        if (dot > 0.9995f) return normalize(lerp(q1, q2f, t))
        val theta0 = acos(dot.coerceIn(0f, 1f))
        val theta  = theta0 * t
        val s1 = cos(theta) - dot * sin(theta) / sin(theta0)
        val s2 = sin(theta) / sin(theta0)
        return floatArrayOf(s1*q1[0]+s2*q2f[0], s1*q1[1]+s2*q2f[1], s1*q1[2]+s2*q2f[2], s1*q1[3]+s2*q2f[3])
    }

    private fun lerp(a: FloatArray, b: FloatArray, t: Float) =
        floatArrayOf(a[0]+t*(b[0]-a[0]), a[1]+t*(b[1]-a[1]), a[2]+t*(b[2]-a[2]), a[3]+t*(b[3]-a[3]))

    private fun normalize(q: FloatArray): FloatArray {
        val len = sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]).coerceAtLeast(1e-6f)
        return floatArrayOf(q[0]/len, q[1]/len, q[2]/len, q[3]/len)
    }

    // Quaternion to column-major 4x4 rotation matrix
    fun toMat4(q: FloatArray): FloatArray {
        val x=q[0]; val y=q[1]; val z=q[2]; val w=q[3]
        return floatArrayOf(
            1-2*(y*y+z*z), 2*(x*y+w*z),   2*(x*z-w*y),   0f,
            2*(x*y-w*z),   1-2*(x*x+z*z), 2*(y*z+w*x),   0f,
            2*(x*z+w*y),   2*(y*z-w*x),   1-2*(x*x+y*y), 0f,
            0f,             0f,            0f,            1f
        )
    }
}

// ── Animation Controller ─────────────────────────────────────────────────────

class AnimationController {
    private val TAG = "AnimController"

    @Volatile var model: GlbModel? = null
    @Volatile var currentClip: AnimClip? = null
    @Volatile var agentState: AgentState = AgentState.IDLE
    @Volatile var startTime: Long = System.currentTimeMillis()

    // Flat array: 64 joint matrices × 16 floats, uploaded to GPU each frame
    private val jointMatrices = FloatArray(64 * 16).also { arr ->
        for (i in 0 until 64) { arr[i * 16 + 0] = 1f; arr[i * 16 + 5] = 1f; arr[i * 16 + 10] = 1f; arr[i * 16 + 15] = 1f }
    }

    fun setAiClip(clip: AnimClip) {
        currentClip = clip
        startTime = System.currentTimeMillis()
        Log.d(TAG, "Playing AI clip: ${clip.name}, duration=${clip.duration}s, frames=${clip.frames.size}")
    }

    fun clearAiClip() { currentClip = null }

    // Called every frame from GL thread
    fun computeJointMatrices(timeMs: Long): FloatArray {
        val m = model ?: return jointMatrices
        val elapsed = (timeMs - startTime) / 1000f

        val poseRots = currentClip?.let { evaluateClip(it, elapsed) }
            ?: buildProceduralPose(elapsed)

        computeForwardKinematics(m, m.rootNodeIndex, identityMat4(), poseRots)
        return jointMatrices
    }

    // ── Procedural poses for each agent state ────────────────────────────────

    private fun buildProceduralPose(t: Float): Map<String, FloatArray> {
        val pose = mutableMapOf<String, FloatArray>()
        val sin = { f: Float -> sin(f) }
        val cos = { f: Float -> cos(f) }

        when (agentState) {
            AgentState.IDLE -> {
                // Gentle breathing: Spine01 sway
                val breathe = sin(t * 1.1f) * 0.015f
                pose["Spine01"] = QuatMath.fromAxisAngle(0f, 0f, 1f, breathe)
                pose["Spine02"] = QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 1.1f) * 0.01f)
                // Head subtle nod + sway
                pose["Head"] = QuatMath.multiply(
                    QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 0.7f) * 0.02f),
                    QuatMath.fromAxisAngle(0f, 1f, 0f, sin(t * 0.45f) * 0.015f)
                )
                // Arms relaxed, slight sway
                pose["L_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, sin(t * 0.5f) * 0.02f)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, -sin(t * 0.5f) * 0.02f)
                // Hip micro-sway
                pose["Hip"] = QuatMath.fromAxisAngle(0f, 0f, 1f, sin(t * 0.4f) * 0.01f)
            }

            AgentState.LISTENING -> {
                // Head tilt to one side with bob
                val tilt = 0.12f + sin(t * 1.5f) * 0.05f
                pose["Head"] = QuatMath.multiply(
                    QuatMath.fromAxisAngle(0f, 0f, 1f, tilt),
                    QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 2f) * 0.025f)
                )
                pose["NeckTwist01"] = QuatMath.fromAxisAngle(0f, 0f, 1f, tilt * 0.5f)
                // Torso leans slightly forward
                pose["Spine01"] = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.04f)
                pose["Spine02"] = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.03f)
                // One arm slightly raised (attentive)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.25f + sin(t * 1.8f) * 0.04f)
                pose["R_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.3f)
            }

            AgentState.TALKING -> {
                // Head nods with speech rhythm
                val nod = sin(t * 4f) * 0.05f + sin(t * 7f) * 0.02f
                pose["Head"] = QuatMath.fromAxisAngle(1f, 0f, 0f, nod)
                // Arms gesture while talking
                val gestureL = sin(t * 3f) * 0.18f
                val gestureR = sin(t * 2.7f + 1f) * 0.15f
                pose["L_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, gestureL)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, -gestureR)
                pose["L_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.4f + sin(t * 3.5f) * 0.2f)
                pose["R_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.4f + sin(t * 3f) * 0.2f)
                // Spine bounce
                pose["Spine01"] = QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 4f) * 0.02f)
            }

            AgentState.WORKING -> {
                // Forward hunch: analyzing data
                pose["Spine01"] = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.12f)
                pose["Spine02"] = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.08f)
                // Head looks down
                pose["Head"] = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.18f)
                // Typing motion alternating arms
                val typL = abs(sin(t * 9f)) * 0.12f
                val typR = abs(sin(t * 9f + PI.toFloat())) * 0.12f
                pose["L_Forearm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, typL)
                pose["R_Forearm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, typR)
                pose["L_Hand"]    = QuatMath.fromAxisAngle(1f, 0f, 0f, -typL * 0.5f)
                pose["R_Hand"]    = QuatMath.fromAxisAngle(1f, 0f, 0f, -typR * 0.5f)
            }
        }
        return pose
    }

    // ── Evaluate AI clip at time t ────────────────────────────────────────────

    private fun evaluateClip(clip: AnimClip, t: Float): Map<String, FloatArray> {
        val time = if (clip.loop) t % clip.duration else t.coerceAtMost(clip.duration)
        val frames = clip.frames
        if (frames.isEmpty()) return emptyMap()
        if (frames.size == 1) return frames[0].rotations

        // Find bracketing keyframes
        var lo = frames.last { it.time <= time }.let { frames.indexOf(it) }
        var hi = (lo + 1).coerceAtMost(frames.size - 1)
        val f0 = frames[lo]; val f1 = frames[hi]
        val alpha = if (f1.time > f0.time) (time - f0.time) / (f1.time - f0.time) else 0f

        // Slerp all joints
        val result = mutableMapOf<String, FloatArray>()
        val allJoints = f0.rotations.keys + f1.rotations.keys
        for (joint in allJoints) {
            val r0 = f0.rotations[joint] ?: QuatMath.identity()
            val r1 = f1.rotations[joint] ?: QuatMath.identity()
            result[joint] = QuatMath.slerp(r0, r1, alpha.coerceIn(0f, 1f))
        }
        return result
    }

    // ── Forward kinematics traversal ──────────────────────────────────────────

    private fun computeForwardKinematics(
        m: GlbModel,
        nodeIdx: Int,
        parentGlobal: FloatArray,
        poseRots: Map<String, FloatArray>
    ) {
        val jointListIdx = m.joints.indexOfFirst { it.nodeIndex == nodeIdx }
        val joint = if (jointListIdx >= 0) m.joints[jointListIdx] else null

        val localMat = if (joint != null) {
            val rot = poseRots[joint.name]?.let { QuatMath.multiply(joint.defaultRotation, it) }
                ?: joint.defaultRotation
            trsMatrix(joint.defaultTranslation, rot, joint.defaultScale)
        } else identityMat4()

        val globalMat = FloatArray(16)
        Matrix.multiplyMM(globalMat, 0, parentGlobal, 0, localMat, 0)

        // Write to jointMatrices if this node is in the skin
        if (jointListIdx >= 0 && jointListIdx < 64) {
            val skinMat = FloatArray(16)
            Matrix.multiplyMM(skinMat, 0, globalMat, 0, m.inverseBindMatrices[jointListIdx], 0)
            System.arraycopy(skinMat, 0, jointMatrices, jointListIdx * 16, 16)
        }

        // Recurse children
        m.nodeChildren[nodeIdx]?.forEach { child ->
            computeForwardKinematics(m, child, globalMat, poseRots)
        }
    }

    // ── Matrix helpers ────────────────────────────────────────────────────────

    private fun identityMat4() = floatArrayOf(
        1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f
    )

    private fun trsMatrix(t: FloatArray, r: FloatArray, s: FloatArray): FloatArray {
        val m = QuatMath.toMat4(r)
        // Apply scale to rotation columns
        for (i in 0..2) m[i]     *= s[0]
        for (i in 0..2) m[4 + i] *= s[1]
        for (i in 0..2) m[8 + i] *= s[2]
        m[12] = t[0]; m[13] = t[1]; m[14] = t[2]
        return m
    }

    // ── AI animation clip parser ──────────────────────────────────────────────

    companion object {
        fun parseAiClip(jsonStr: String): AnimClip? = try {
            val json = JSONObject(extractJson(jsonStr))
            val name = json.optString("name", "custom")
            val duration = json.optDouble("duration", 2.0).toFloat()
            val loop = json.optBoolean("loop", true)
            val framesArr = json.optJSONArray("frames") ?: return null
            val frames = (0 until framesArr.length()).mapNotNull { fi ->
                val frame = framesArr.getJSONObject(fi)
                val time = frame.optDouble("time", 0.0).toFloat()
                val jointsObj = frame.optJSONObject("joints") ?: return@mapNotNull null
                val rots = mutableMapOf<String, FloatArray>()
                jointsObj.keys().forEach { jname ->
                    val arr = jointsObj.optJSONArray(jname) ?: return@forEach
                    if (arr.length() >= 4) {
                        rots[jname] = floatArrayOf(
                            arr.getDouble(0).toFloat(),
                            arr.getDouble(1).toFloat(),
                            arr.getDouble(2).toFloat(),
                            arr.getDouble(3).toFloat()
                        )
                    }
                }
                AnimFrame(time, rots)
            }.sortedBy { it.time }
            if (frames.isEmpty()) null
            else AnimClip(name, duration, frames, loop)
        } catch (e: Exception) {
            Log.e("AnimController", "Failed to parse AI clip", e)
            null
        }

        private fun extractJson(text: String): String {
            val start = text.indexOf('{'); val end = text.lastIndexOf('}')
            return if (start != -1 && end > start) text.substring(start, end + 1) else text
        }

        // System prompt sent to NVIDIA NIM for animation generation
        fun buildAnimationPrompt(animName: String): String = """
You are a professional 3D character animator. Generate a realistic "$animName" animation for a humanoid skeleton.

Available joints (use these exact names):
Root, Hip, Waist, Spine01, Spine02, Head, NeckTwist01, NeckTwist02,
L_Clavicle, L_Upperarm, L_ForearmTwist01, L_Forearm, L_Hand,
R_Clavicle, R_Upperarm, R_ForearmTwist01, R_Forearm, R_Hand,
L_Thigh, L_CalfTwist01, L_Calf, L_Foot, L_ToeBase,
R_Thigh, R_CalfTwist01, R_Calf, R_Foot, R_ToeBase

Rules:
- Each joint rotation is a quaternion [x, y, z, w] representing the LOCAL rotation OFFSET from default pose
- Generate at least 8 keyframes spread across the duration
- Only include joints that actually move
- Make the animation energetic, expressive, and loopable
- Keep quaternions normalized (magnitude ~1.0)
- Small rotations: 0.1-0.3 radians; large: 0.5-1.0 radians; use axis-angle intuition

Respond ONLY with valid JSON, no explanation:
{"name":"$animName","duration":2.0,"loop":true,"frames":[{"time":0.0,"joints":{"Spine01":[0,0,0.1,0.995],"L_Upperarm":[0,0,0.5,0.866]}}]}
""".trimIndent()
    }
}
