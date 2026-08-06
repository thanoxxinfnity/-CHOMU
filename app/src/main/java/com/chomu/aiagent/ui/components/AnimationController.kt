package com.chomu.aiagent.ui.components

import android.opengl.Matrix
import android.util.Log
import com.chomu.aiagent.domain.model.AgentState
import org.json.JSONObject
import kotlin.math.*

// ── Data types ───────────────────────────────────────────────────────────────

data class AnimFrame(
    val time: Float,
    val rotations: Map<String, FloatArray>
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

    private val jointMatrices = FloatArray(64 * 16).also { arr ->
        for (i in 0 until 64) { arr[i*16+0]=1f; arr[i*16+5]=1f; arr[i*16+10]=1f; arr[i*16+15]=1f }
    }

    fun setAiClip(clip: AnimClip) {
        currentClip = clip
        startTime = System.currentTimeMillis()
        Log.d(TAG, "Playing clip: ${clip.name} dur=${clip.duration}s frames=${clip.frames.size}")
    }

    fun clearAiClip() { currentClip = null }

    fun computeJointMatrices(timeMs: Long): FloatArray {
        val m = model ?: return jointMatrices
        val elapsed = (timeMs - startTime) / 1000f
        val poseRots = currentClip?.let { evaluateClip(it, elapsed) } ?: buildProceduralPose(elapsed)
        computeForwardKinematics(m, m.rootNodeIndex, identityMat4(), poseRots)
        return jointMatrices
    }

    // ── Procedural poses — full body including legs ───────────────────────────

    private fun buildProceduralPose(t: Float): Map<String, FloatArray> {
        val pose = mutableMapOf<String, FloatArray>()

        when (agentState) {
            AgentState.IDLE -> {
                val breathe = sin(t * 1.1f) * 0.015f
                val sway    = sin(t * 0.4f) * 0.012f
                val legRock = sin(t * 0.35f) * 0.018f

                pose["Hip"]      = QuatMath.fromAxisAngle(0f, 0f, 1f, sway * 0.6f)
                pose["Pelvis"]   = QuatMath.fromAxisAngle(0f, 0f, 1f, sway * 0.3f)
                pose["Spine01"]  = QuatMath.fromAxisAngle(0f, 0f, 1f, breathe)
                pose["Spine02"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 1.1f) * 0.01f)
                pose["Head"]     = QuatMath.multiply(
                    QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 0.7f)  * 0.022f),
                    QuatMath.fromAxisAngle(0f, 1f, 0f, sin(t * 0.45f) * 0.018f)
                )
                pose["L_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f,  sin(t * 0.5f) * 0.02f)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, -sin(t * 0.5f) * 0.02f)
                // Gentle leg weight-rock
                pose["L_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f,  legRock)
                pose["R_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, -legRock)
                pose["L_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -abs(legRock) * 0.4f)
                pose["R_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -abs(legRock) * 0.4f)
            }

            AgentState.LISTENING -> {
                val tilt = 0.12f + sin(t * 1.5f) * 0.05f
                val nod  = sin(t * 2f) * 0.025f

                pose["Head"]     = QuatMath.multiply(
                    QuatMath.fromAxisAngle(0f, 0f, 1f, tilt),
                    QuatMath.fromAxisAngle(1f, 0f, 0f, nod)
                )
                pose["NeckTwist01"] = QuatMath.fromAxisAngle(0f, 0f, 1f, tilt * 0.5f)
                pose["Spine01"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.04f)
                pose["Spine02"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.03f)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.25f + sin(t * 1.8f) * 0.04f)
                pose["R_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.3f)
                // Weight shift to right — attentive stance
                pose["Hip"]      = QuatMath.fromAxisAngle(0f, 0f, 1f, -0.07f)
                pose["L_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f,  0.04f)
                pose["R_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.03f)
                pose["R_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.10f)
                pose["L_Foot"]   = QuatMath.fromAxisAngle(1f, 0f, 0f,  0.03f)
            }

            AgentState.TALKING -> {
                val nod      = sin(t * 4f) * 0.05f + sin(t * 7f) * 0.02f
                val gestureL = sin(t * 3f)          * 0.18f
                val gestureR = sin(t * 2.7f + 1f)   * 0.15f
                val step     = sin(t * 2.5f)         * 0.07f

                pose["Head"]     = QuatMath.fromAxisAngle(1f, 0f, 0f, nod)
                pose["Spine01"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, sin(t * 4f) * 0.02f)
                pose["L_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f,  gestureL)
                pose["R_Upperarm"] = QuatMath.fromAxisAngle(0f, 0f, 1f, -gestureR)
                pose["L_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.4f + sin(t * 3.5f) * 0.2f)
                pose["R_Forearm"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.4f + sin(t * 3f)   * 0.2f)
                // Subtle stepping while talking
                pose["Hip"]      = QuatMath.fromAxisAngle(0f, 0f, 1f,  step * 0.5f)
                pose["L_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f,  step)
                pose["R_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, -step)
                pose["L_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -maxOf(0f,  step) * 0.35f)
                pose["R_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -maxOf(0f, -step) * 0.35f)
            }

            AgentState.WORKING -> {
                val typL = abs(sin(t * 9f)) * 0.12f
                val typR = abs(sin(t * 9f + PI.toFloat())) * 0.12f

                pose["Spine01"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.12f)
                pose["Spine02"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.08f)
                pose["Head"]     = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.18f)
                pose["L_Forearm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, typL)
                pose["R_Forearm"] = QuatMath.fromAxisAngle(1f, 0f, 0f, typR)
                pose["L_Hand"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -typL * 0.5f)
                pose["R_Hand"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -typR * 0.5f)
                // Stable slightly-bent working stance
                pose["L_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.08f)
                pose["R_Thigh"]  = QuatMath.fromAxisAngle(1f, 0f, 0f, 0.08f)
                pose["L_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.12f)
                pose["R_Calf"]   = QuatMath.fromAxisAngle(1f, 0f, 0f, -0.12f)
            }
        }
        return pose
    }

    // ── Evaluate keyframe clip ────────────────────────────────────────────────

    private fun evaluateClip(clip: AnimClip, t: Float): Map<String, FloatArray> {
        val time = if (clip.loop) t % clip.duration else t.coerceAtMost(clip.duration)
        val frames = clip.frames
        if (frames.isEmpty()) return emptyMap()
        if (frames.size == 1) return frames[0].rotations

        val loIdx = frames.indexOfLast { it.time <= time }.coerceAtLeast(0)
        val hiIdx = (loIdx + 1).coerceAtMost(frames.size - 1)
        val f0 = frames[loIdx]; val f1 = frames[hiIdx]
        val alpha = if (f1.time > f0.time) ((time - f0.time) / (f1.time - f0.time)).coerceIn(0f, 1f) else 0f

        val result = mutableMapOf<String, FloatArray>()
        for (joint in (f0.rotations.keys + f1.rotations.keys)) {
            val r0 = f0.rotations[joint] ?: QuatMath.identity()
            val r1 = f1.rotations[joint] ?: QuatMath.identity()
            result[joint] = QuatMath.slerp(r0, r1, alpha)
        }
        return result
    }

    // ── Forward kinematics ────────────────────────────────────────────────────

    private fun computeForwardKinematics(
        m: GlbModel, nodeIdx: Int, parentGlobal: FloatArray, poseRots: Map<String, FloatArray>
    ) {
        val jointListIdx = m.joints.indexOfFirst { it.nodeIndex == nodeIdx }
        val joint = if (jointListIdx >= 0) m.joints[jointListIdx] else null

        val localMat = if (joint != null) {
            val rot = poseRots[joint.name]
                ?.let { QuatMath.multiply(joint.defaultRotation, it) }
                ?: joint.defaultRotation
            trsMatrix(joint.defaultTranslation, rot, joint.defaultScale)
        } else identityMat4()

        val globalMat = FloatArray(16)
        Matrix.multiplyMM(globalMat, 0, parentGlobal, 0, localMat, 0)

        if (jointListIdx in 0 until 64) {
            val skinMat = FloatArray(16)
            Matrix.multiplyMM(skinMat, 0, globalMat, 0, m.inverseBindMatrices[jointListIdx], 0)
            System.arraycopy(skinMat, 0, jointMatrices, jointListIdx * 16, 16)
        }

        m.nodeChildren[nodeIdx]?.forEach { computeForwardKinematics(m, it, globalMat, poseRots) }
    }

    // ── Matrix helpers ────────────────────────────────────────────────────────

    private fun identityMat4() = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)

    private fun trsMatrix(t: FloatArray, r: FloatArray, s: FloatArray): FloatArray {
        val m = QuatMath.toMat4(r)
        for (i in 0..2) m[i]   *= s[0]
        for (i in 0..2) m[4+i] *= s[1]
        for (i in 0..2) m[8+i] *= s[2]
        m[12] = t[0]; m[13] = t[1]; m[14] = t[2]
        return m
    }

    // ── Companion: built-in clip library + AI parser ──────────────────────────

    companion object {

        fun builtinClipFor(animName: String): AnimClip? = when (animName) {
            "dance"     -> buildDanceClip()
            "wave"      -> buildWaveClip()
            "spin"      -> buildSpinClip()
            "bow"       -> buildBowClip()
            "salute"    -> buildSaluteClip()
            "clap"      -> buildClapClip()
            "jump"      -> buildJumpClip()
            "celebrate" -> buildCelebrateClip()
            "think"     -> buildThinkClip()
            "happy"     -> buildHappyClip()
            "sad"       -> buildSadClip()
            "angry"     -> buildAngryClip()
            "stretch"   -> buildStretchClip()
            "punch"     -> buildPunchClip()
            else        -> null
        }

        // Sample a pose function into N+1 keyframes spanning [0, dur]
        private fun frames(dur: Float, n: Int, pose: (Float) -> Map<String, FloatArray>): List<AnimFrame> =
            (0..n).map { i -> AnimFrame(i.toFloat() * dur / n, pose(i.toFloat() * dur / n)) }

        private fun Q(ax: Float, ay: Float, az: Float, a: Float) = QuatMath.fromAxisAngle(ax, ay, az, a)
        private fun QM(a: FloatArray, b: FloatArray) = QuatMath.multiply(a, b)
        private val TWO_PI = (PI * 2f).toFloat()

        // ── Dance: Kathak-Breakdance fusion — full body with leg steps ────────

        private fun buildDanceClip(): AnimClip {
            val dur = 4.0f
            return AnimClip("dance", dur, frames(dur, 24) { t ->
                val step = t * TWO_PI          // step cycle: 1 s period
                val slow = t * PI.toFloat()    // slow arm wave: 2 s period

                mutableMapOf(
                    // Hip bob + side sway
                    "Hip"    to QM(Q(0f,0f,1f, sin(step)*0.14f), Q(1f,0f,0f, sin(step*2f)*0.05f)),
                    "Pelvis" to Q(0f, 0f, 1f, sin(step)*0.07f),
                    // Torso twist opposes hip
                    "Waist"   to Q(0f, 1f, 0f, sin(step)*0.14f),
                    "Spine01" to QM(Q(0f,1f,0f, sin(step)*0.10f), Q(1f,0f,0f, sin(step*2f)*0.04f)),
                    "Spine02" to Q(0f, 1f, 0f, sin(step)*0.07f),
                    // Head bobs and looks with the rhythm
                    "Head"   to QM(Q(1f,0f,0f, sin(step*2f)*0.08f), Q(0f,1f,0f, -sin(step)*0.12f)),
                    // ── Legs alternating: L forward when sin>0 ───────────────
                    "L_Thigh"   to Q(1f, 0f, 0f,  sin(step)*0.42f),
                    "L_Calf"    to Q(1f, 0f, 0f, -maxOf(0f,  sin(step))*0.38f),
                    "L_Foot"    to Q(1f, 0f, 0f,  maxOf(0f,  sin(step))*0.20f),
                    "L_ToeBase" to Q(1f, 0f, 0f,  maxOf(0f,  sin(step))*0.12f),
                    "R_Thigh"   to Q(1f, 0f, 0f, -sin(step)*0.42f),
                    "R_Calf"    to Q(1f, 0f, 0f, -maxOf(0f, -sin(step))*0.38f),
                    "R_Foot"    to Q(1f, 0f, 0f,  maxOf(0f, -sin(step))*0.20f),
                    "R_ToeBase" to Q(1f, 0f, 0f,  maxOf(0f, -sin(step))*0.12f),
                    // ── Arms: natural opposition to legs ─────────────────────
                    "L_Clavicle" to Q(0f, 0f, 1f, sin(step)*0.10f),
                    "L_Upperarm" to QM(Q(1f,0f,0f, -sin(step)*0.30f), Q(0f,0f,1f, 0.32f+sin(slow)*0.28f)),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.38f+sin(step*2f+0.5f)*0.22f),
                    "L_Hand"     to Q(0f, 0f, 1f, sin(step*3f)*0.14f),
                    "R_Clavicle" to Q(0f, 0f, 1f, -sin(step)*0.10f),
                    "R_Upperarm" to QM(Q(1f,0f,0f, sin(step)*0.30f), Q(0f,0f,1f, -(0.32f+sin(slow+PI.toFloat())*0.28f))),
                    "R_Forearm"  to Q(1f, 0f, 0f, 0.38f+sin(step*2f+1.5f)*0.22f),
                    "R_Hand"     to Q(0f, 0f, 1f, -sin(step*3f)*0.14f)
                )
            }, loop = true)
        }

        // ── Wave ─────────────────────────────────────────────────────────────

        private fun buildWaveClip(): AnimClip {
            val dur = 2.0f
            return AnimClip("wave", dur, frames(dur, 12) { t ->
                val w = t * TWO_PI / dur
                mutableMapOf(
                    "Spine01"    to Q(0f, 0f, 1f, -0.06f),
                    "Head"       to QM(Q(0f,1f,0f, 0.22f), Q(1f,0f,0f, sin(w*2f)*0.03f)),
                    "R_Clavicle" to Q(0f, 0f, 1f, -0.18f),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -0.55f), Q(0f,0f,1f, -0.25f)),
                    "R_Forearm"  to Q(1f, 0f, 0f, 0.25f+sin(w*2f)*0.42f),
                    "R_Hand"     to Q(0f, 1f, 0f, sin(w*2f)*0.32f),
                    "L_Upperarm" to Q(0f, 0f, 1f, 0.08f),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.18f),
                    "Hip"        to Q(0f, 0f, 1f, -0.04f),
                    "R_Thigh"    to Q(1f, 0f, 0f, -0.03f),
                    "R_Calf"     to Q(1f, 0f, 0f, -0.06f)
                )
            }, loop = true)
        }

        // ── Spin ─────────────────────────────────────────────────────────────

        private fun buildSpinClip(): AnimClip {
            val dur = 1.8f
            return AnimClip("spin", dur, frames(dur, 18) { t ->
                val angle = t / dur * TWO_PI
                mutableMapOf(
                    "Hip"        to Q(0f, 1f, 0f, angle),
                    "L_Upperarm" to Q(0f, 0f, 1f,  0.55f),
                    "R_Upperarm" to Q(0f, 0f, 1f, -0.55f),
                    "L_Forearm"  to Q(1f, 0f, 0f, -0.10f),
                    "R_Forearm"  to Q(1f, 0f, 0f, -0.10f),
                    "Head"       to Q(0f, 1f, 0f, -angle * 0.3f),
                    "L_Calf"     to Q(1f, 0f, 0f, -0.10f),
                    "R_Calf"     to Q(1f, 0f, 0f, -0.10f)
                )
            }, loop = true)
        }

        // ── Bow ──────────────────────────────────────────────────────────────

        private fun buildBowClip(): AnimClip {
            val dur = 2.5f
            return AnimClip("bow", dur, frames(dur, 10) { t ->
                val prog = sin(t / dur * PI.toFloat())
                val bFwd = prog * 0.58f
                mutableMapOf(
                    "Hip"        to Q(1f, 0f, 0f,  bFwd*0.28f),
                    "Waist"      to Q(1f, 0f, 0f,  bFwd*0.30f),
                    "Spine01"    to Q(1f, 0f, 0f,  bFwd*0.42f),
                    "Spine02"    to Q(1f, 0f, 0f,  bFwd*0.30f),
                    "Head"       to Q(1f, 0f, 0f, -bFwd*0.35f),
                    "L_Upperarm" to Q(0f, 0f, 1f,  bFwd*0.18f),
                    "R_Upperarm" to Q(0f, 0f, 1f, -bFwd*0.18f),
                    "L_Thigh"    to Q(1f, 0f, 0f, -bFwd*0.15f),
                    "R_Thigh"    to Q(1f, 0f, 0f, -bFwd*0.15f),
                    "L_Calf"     to Q(1f, 0f, 0f,  bFwd*0.14f),
                    "R_Calf"     to Q(1f, 0f, 0f,  bFwd*0.14f)
                )
            }, loop = false)
        }

        // ── Salute ───────────────────────────────────────────────────────────

        private fun buildSaluteClip(): AnimClip {
            val dur = 2.0f
            return AnimClip("salute", dur, frames(dur, 8) { t ->
                val hold = (sin(t / dur * PI.toFloat() * 2f) * 0.5f + 0.5f).coerceIn(0f, 1f)
                mutableMapOf(
                    "Spine01"    to Q(1f, 0f, 0f, -0.05f*hold),
                    "Spine02"    to Q(1f, 0f, 0f, -0.04f*hold),
                    "Head"       to Q(0f, 1f, 0f,  0.18f*hold),
                    "R_Clavicle" to Q(0f, 0f, 1f, -0.15f*hold),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -0.65f*hold), Q(0f,0f,1f, -0.22f*hold)),
                    "R_Forearm"  to Q(1f, 0f, 0f,  1.25f*hold),
                    "R_Hand"     to Q(0f, 0f, 1f, -0.10f*hold),
                    "L_Upperarm" to Q(0f, 0f, 1f,  0.10f),
                    "L_Forearm"  to Q(1f, 0f, 0f,  0.18f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  0.04f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  0.04f)
                )
            }, loop = false)
        }

        // ── Clap ─────────────────────────────────────────────────────────────

        private fun buildClapClip(): AnimClip {
            val dur = 0.8f
            return AnimClip("clap", dur, frames(dur, 8) { t ->
                val clap   = abs(sin(t / dur * PI.toFloat()))
                val armsIn = clap * 0.45f
                mutableMapOf(
                    "Head"       to Q(1f, 0f, 0f,  clap*0.04f),
                    "Hip"        to Q(1f, 0f, 0f,  clap*0.03f),
                    "L_Clavicle" to Q(0f, 0f, 1f,  armsIn*0.30f),
                    "R_Clavicle" to Q(0f, 0f, 1f, -armsIn*0.30f),
                    "L_Upperarm" to QM(Q(1f,0f,0f, -(0.22f+armsIn*0.18f)), Q(0f,0f,1f,  armsIn*0.38f)),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -(0.22f+armsIn*0.18f)), Q(0f,0f,1f, -armsIn*0.38f)),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.62f+armsIn*0.42f),
                    "R_Forearm"  to Q(1f, 0f, 0f, 0.62f+armsIn*0.42f),
                    "L_Thigh"    to Q(1f, 0f, 0f, clap*0.04f),
                    "R_Thigh"    to Q(1f, 0f, 0f, clap*0.04f)
                )
            }, loop = true)
        }

        // ── Jump ─────────────────────────────────────────────────────────────

        private fun buildJumpClip(): AnimClip {
            val dur = 1.0f
            return AnimClip("jump", dur, frames(dur, 10) { t ->
                val phase = t / dur
                val crouchAmt = when {
                    phase < 0.25f -> phase / 0.25f
                    phase < 0.65f -> 1f - (phase - 0.25f) / 0.40f
                    else          -> (phase - 0.65f) / 0.35f * 0.5f
                }
                val thighFwd = crouchAmt * 0.38f
                val calfBend = -crouchAmt * 0.48f
                val armUp = if (phase in 0.25f..0.70f)
                    (1f - abs(phase - 0.475f) / 0.225f).coerceIn(0f, 1f) * 0.6f
                else 0f
                mutableMapOf(
                    "Hip"        to Q(1f, 0f, 0f, -crouchAmt*0.10f),
                    "Spine01"    to Q(1f, 0f, 0f, -crouchAmt*0.08f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  thighFwd),
                    "R_Thigh"    to Q(1f, 0f, 0f,  thighFwd),
                    "L_Calf"     to Q(1f, 0f, 0f,  calfBend),
                    "R_Calf"     to Q(1f, 0f, 0f,  calfBend),
                    "L_Foot"     to Q(1f, 0f, 0f, -calfBend*0.30f),
                    "R_Foot"     to Q(1f, 0f, 0f, -calfBend*0.30f),
                    "L_Upperarm" to Q(0f, 0f, 1f, 0.20f+armUp*0.40f),
                    "R_Upperarm" to Q(0f, 0f, 1f, -(0.20f+armUp*0.40f))
                )
            }, loop = true)
        }

        // ── Celebrate ────────────────────────────────────────────────────────

        private fun buildCelebrateClip(): AnimClip {
            val dur = 2.0f
            return AnimClip("celebrate", dur, frames(dur, 16) { t ->
                val pump   = t * TWO_PI * 2f
                val bounce = abs(sin(pump * 0.5f))
                mutableMapOf(
                    "Hip"        to Q(1f, 0f, 0f, -bounce*0.06f),
                    "Spine01"    to Q(1f, 0f, 0f, -bounce*0.05f),
                    "Head"       to Q(1f, 0f, 0f,  sin(pump)*0.08f),
                    "L_Upperarm" to QM(Q(1f,0f,0f, -0.55f-sin(pump)*0.22f),             Q(0f,0f,1f, 0.32f)),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -0.55f-sin(pump+PI.toFloat())*0.22f), Q(0f,0f,1f,-0.32f)),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.52f+sin(pump)*0.12f),
                    "R_Forearm"  to Q(1f, 0f, 0f, 0.52f+sin(pump+PI.toFloat())*0.12f),
                    "L_Thigh"    to Q(1f, 0f, 0f, bounce*0.10f),
                    "R_Thigh"    to Q(1f, 0f, 0f, bounce*0.10f),
                    "L_Calf"     to Q(1f, 0f, 0f, -bounce*0.18f),
                    "R_Calf"     to Q(1f, 0f, 0f, -bounce*0.18f)
                )
            }, loop = true)
        }

        // ── Think ────────────────────────────────────────────────────────────

        private fun buildThinkClip(): AnimClip {
            val dur = 3.0f
            return AnimClip("think", dur, frames(dur, 6) { t ->
                val breath = sin(t * TWO_PI / 3f) * 0.015f
                mutableMapOf(
                    "Head"       to QM(Q(1f,0f,0f, 0.18f), Q(0f,1f,0f, -0.12f)),
                    "Spine01"    to Q(1f, 0f, 0f, 0.10f+breath),
                    "R_Clavicle" to Q(0f, 0f, 1f, -0.12f),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -0.60f), Q(0f,0f,1f, -0.20f)),
                    "R_Forearm"  to Q(1f, 0f, 0f,  1.25f),
                    "R_Hand"     to Q(0f, 0f, 1f,  0.15f),
                    "L_Upperarm" to Q(0f, 0f, 1f,  0.08f),
                    "L_Forearm"  to Q(1f, 0f, 0f,  0.15f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  0.06f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  0.06f),
                    "L_Calf"     to Q(1f, 0f, 0f, -0.10f),
                    "R_Calf"     to Q(1f, 0f, 0f, -0.10f)
                )
            }, loop = true)
        }

        // ── Happy ────────────────────────────────────────────────────────────

        private fun buildHappyClip(): AnimClip {
            val dur = 1.5f
            return AnimClip("happy", dur, frames(dur, 12) { t ->
                val skip   = t * TWO_PI / dur
                val bounce = abs(sin(skip)) * 0.08f
                mutableMapOf(
                    "Hip"        to Q(1f, 0f, 0f, -bounce),
                    "Head"       to QM(Q(1f,0f,0f, -0.05f-bounce*0.5f), Q(0f,1f,0f, sin(skip)*0.12f)),
                    "Spine01"    to Q(0f, 0f, 1f, sin(skip)*0.06f),
                    "L_Upperarm" to Q(0f, 0f, 1f,  0.35f+sin(skip)*0.12f),
                    "R_Upperarm" to Q(0f, 0f, 1f, -(0.35f+sin(skip+PI.toFloat())*0.12f)),
                    "L_Forearm"  to Q(1f, 0f, 0f,  0.30f),
                    "R_Forearm"  to Q(1f, 0f, 0f,  0.30f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  bounce*0.8f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  bounce*0.8f),
                    "L_Calf"     to Q(1f, 0f, 0f, -bounce*1.0f),
                    "R_Calf"     to Q(1f, 0f, 0f, -bounce*1.0f)
                )
            }, loop = true)
        }

        // ── Sad ──────────────────────────────────────────────────────────────

        private fun buildSadClip(): AnimClip {
            val dur = 4.0f
            return AnimClip("sad", dur, frames(dur, 8) { t ->
                val slump = 0.5f + sin(t * PI.toFloat() / dur) * 0.08f
                mutableMapOf(
                    "Head"       to Q(1f, 0f, 0f,  0.30f*slump),
                    "Spine01"    to Q(1f, 0f, 0f,  0.20f*slump),
                    "Spine02"    to Q(1f, 0f, 0f,  0.15f*slump),
                    "Hip"        to Q(1f, 0f, 0f,  0.10f*slump),
                    "L_Upperarm" to Q(0f, 0f, 1f,  0.10f*slump),
                    "R_Upperarm" to Q(0f, 0f, 1f, -0.10f*slump),
                    "L_Forearm"  to Q(1f, 0f, 0f,  0.20f),
                    "R_Forearm"  to Q(1f, 0f, 0f,  0.20f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  0.08f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  0.08f),
                    "L_Calf"     to Q(1f, 0f, 0f, -0.12f),
                    "R_Calf"     to Q(1f, 0f, 0f, -0.12f)
                )
            }, loop = true)
        }

        // ── Angry ────────────────────────────────────────────────────────────

        private fun buildAngryClip(): AnimClip {
            val dur = 1.2f
            return AnimClip("angry", dur, frames(dur, 10) { t ->
                val shake = sin(t * TWO_PI / dur * 3f) * 0.05f
                mutableMapOf(
                    "Head"       to Q(1f, 0f, 0f,  0.10f+shake),
                    "Spine01"    to Q(1f, 0f, 0f,  0.12f+shake*0.5f),
                    "Hip"        to Q(0f, 0f, 1f,  shake*0.4f),
                    "L_Upperarm" to QM(Q(1f,0f,0f, -0.18f), Q(0f,0f,1f,  0.22f+shake)),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -0.18f), Q(0f,0f,1f, -(0.22f+shake))),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.70f+abs(shake)*0.5f),
                    "R_Forearm"  to Q(1f, 0f, 0f, 0.70f+abs(shake)*0.5f),
                    "L_Hand"     to Q(0f, 0f, 1f,  0.20f),
                    "R_Hand"     to Q(0f, 0f, 1f, -0.20f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  0.06f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  0.06f),
                    "L_Calf"     to Q(1f, 0f, 0f, -0.10f),
                    "R_Calf"     to Q(1f, 0f, 0f, -0.10f)
                )
            }, loop = true)
        }

        // ── Stretch ──────────────────────────────────────────────────────────

        private fun buildStretchClip(): AnimClip {
            val dur = 3.0f
            return AnimClip("stretch", dur, frames(dur, 8) { t ->
                val prog = sin(t / dur * PI.toFloat())
                mutableMapOf(
                    "Head"       to Q(1f, 0f, 0f, -prog*0.20f),
                    "Spine01"    to Q(1f, 0f, 0f, -prog*0.15f),
                    "Spine02"    to Q(1f, 0f, 0f, -prog*0.12f),
                    "L_Clavicle" to Q(0f, 0f, 1f,  prog*0.12f),
                    "R_Clavicle" to Q(0f, 0f, 1f, -prog*0.12f),
                    "L_Upperarm" to Q(0f, 0f, 1f, prog*0.80f+0.10f),
                    "R_Upperarm" to Q(0f, 0f, 1f, -(prog*0.80f+0.10f)),
                    "L_Forearm"  to Q(1f, 0f, 0f, -prog*0.25f),
                    "R_Forearm"  to Q(1f, 0f, 0f, -prog*0.25f),
                    "L_Thigh"    to Q(1f, 0f, 0f,  prog*0.06f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  prog*0.06f),
                    "L_Calf"     to Q(1f, 0f, 0f, -prog*0.10f),
                    "R_Calf"     to Q(1f, 0f, 0f, -prog*0.10f)
                )
            }, loop = true)
        }

        // ── Punch ────────────────────────────────────────────────────────────

        private fun buildPunchClip(): AnimClip {
            val dur = 0.6f
            return AnimClip("punch", dur, frames(dur, 8) { t ->
                val ext = sin(t / dur * PI.toFloat())
                val hip = if (t < dur * 0.5f) t / (dur * 0.5f) else 1f - (t - dur * 0.5f) / (dur * 0.5f)
                mutableMapOf(
                    "Hip"        to Q(0f, 1f, 0f, -hip*0.18f),
                    "Spine01"    to Q(0f, 1f, 0f, -hip*0.12f),
                    "Head"       to Q(0f, 1f, 0f, -hip*0.08f),
                    "R_Clavicle" to Q(0f, 0f, 1f, -ext*0.18f),
                    "R_Upperarm" to QM(Q(1f,0f,0f, -ext*0.25f), Q(0f,0f,1f, -0.15f-ext*0.15f)),
                    "R_Forearm"  to Q(1f, 0f, 0f, 1.0f-ext*0.85f),
                    "R_Hand"     to Q(0f, 0f, 1f, -0.12f),
                    "L_Upperarm" to Q(0f, 0f, 1f, 0.25f-ext*0.10f),
                    "L_Forearm"  to Q(1f, 0f, 0f, 0.50f+ext*0.20f),
                    "L_Thigh"    to Q(1f, 0f, 0f, -hip*0.08f),
                    "R_Thigh"    to Q(1f, 0f, 0f,  hip*0.08f)
                )
            }, loop = true)
        }

        // ── AI clip parser ────────────────────────────────────────────────────

        fun parseAiClip(jsonStr: String): AnimClip? = try {
            val json      = JSONObject(extractJson(jsonStr))
            val name      = json.optString("name", "custom")
            val duration  = json.optDouble("duration", 2.0).toFloat()
            val loop      = json.optBoolean("loop", true)
            val framesArr = json.optJSONArray("frames") ?: return null
            val parsed = (0 until framesArr.length()).mapNotNull { fi ->
                val frame     = framesArr.getJSONObject(fi)
                val time      = frame.optDouble("time", 0.0).toFloat()
                val jointsObj = frame.optJSONObject("joints") ?: return@mapNotNull null
                val rots = mutableMapOf<String, FloatArray>()
                jointsObj.keys().forEach { jn ->
                    val arr = jointsObj.optJSONArray(jn) ?: return@forEach
                    if (arr.length() >= 4) rots[jn] = floatArrayOf(
                        arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat(),
                        arr.getDouble(2).toFloat(), arr.getDouble(3).toFloat()
                    )
                }
                AnimFrame(time, rots)
            }.sortedBy { it.time }
            if (parsed.isEmpty()) null else AnimClip(name, duration, parsed, loop)
        } catch (e: Exception) {
            Log.e("AnimController", "Failed to parse AI clip", e)
            null
        }

        private fun extractJson(text: String): String {
            val s = text.indexOf('{'); val e = text.lastIndexOf('}')
            return if (s != -1 && e > s) text.substring(s, e + 1) else text
        }

        fun buildAnimationPrompt(animName: String): String = """
You are a professional 3D character animator. Generate a "$animName" animation for a humanoid skeleton.

Available joints (exact names):
Root, Hip, Pelvis, Waist, Spine01, Spine02, Head, NeckTwist01, NeckTwist02,
L_Clavicle, L_Upperarm, L_UpperarmTwist01, L_UpperarmTwist02, L_Forearm, L_ForearmTwist01, L_ForearmTwist02, L_Hand,
R_Clavicle, R_Upperarm, R_UpperarmTwist01, R_UpperarmTwist02, R_Forearm, R_ForearmTwist01, R_ForearmTwist02, R_Hand,
L_Thigh, L_ThighTwist01, L_ThighTwist02, L_Calf, L_CalfTwist01, L_CalfTwist02, L_Foot, L_ToeBase,
R_Thigh, R_ThighTwist01, R_ThighTwist02, R_Calf, R_CalfTwist01, R_CalfTwist02, R_Foot, R_ToeBase

Rules:
- Each joint rotation is a quaternion [x,y,z,w] — LOCAL offset from bind pose
- 8-12 keyframes; include BOTH legs and arms actively moving
- Keep quaternions normalized (magnitude ≈ 1.0)
- Make it energetic and loopable

Respond ONLY with valid JSON:
{"name":"$animName","duration":2.0,"loop":true,"frames":[{"time":0.0,"joints":{"L_Thigh":[0.2,0,0,0.98],"R_Thigh":[-0.2,0,0,0.98]}}]}
""".trimIndent()
    }
}
