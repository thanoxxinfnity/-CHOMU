package com.chomu.aiagent.ui.components

import java.nio.FloatBuffer
import java.nio.ShortBuffer

data class GlbModel(
    // Indexed mesh geometry
    val indexBuffer: ShortBuffer,
    val indexCount: Int,
    val positionBuffer: FloatBuffer,   // VEC3 FLOAT per vertex
    val normalBuffer: FloatBuffer,     // VEC3 FLOAT per vertex
    val texCoordBuffer: FloatBuffer,   // VEC2 FLOAT per vertex
    val jointsBuffer: ShortBuffer,     // VEC4 UBYTE → stored as shorts per vertex
    val weightsBuffer: FloatBuffer,    // VEC4 FLOAT per vertex
    val vertexCount: Int,

    // Skeleton
    val joints: List<GlbJoint>,        // ordered by skin.joints[]
    val inverseBindMatrices: Array<FloatArray>,  // 16 floats (col-major) per joint
    val nodeChildren: Map<Int, List<Int>>,
    val rootNodeIndex: Int              // node index of "Root" bone
)

data class GlbJoint(
    val name: String,
    val nodeIndex: Int,
    val defaultTranslation: FloatArray = floatArrayOf(0f, 0f, 0f),
    val defaultRotation: FloatArray    = floatArrayOf(0f, 0f, 0f, 1f), // xyzw
    val defaultScale: FloatArray       = floatArrayOf(1f, 1f, 1f)
)
