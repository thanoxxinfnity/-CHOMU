package com.chomu.aiagent.ui.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

object GlbLoader {
    private const val TAG = "GlbLoader"
    private const val GLB_MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN  = 0x004E4942

    suspend fun load(context: Context, assetPath: String): GlbModel? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            parse(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GLB: $assetPath", e)
            null
        }
    }

    private fun parse(bytes: ByteArray): GlbModel? {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic   = buf.int
        val version = buf.int
        val total   = buf.int
        if (magic != GLB_MAGIC || version != 2) {
            Log.e(TAG, "Not a valid GLB 2.0 file")
            return null
        }

        // JSON chunk
        val jLen  = buf.int
        val jType = buf.int
        if (jType != CHUNK_JSON) { Log.e(TAG, "Expected JSON chunk"); return null }
        val jsonBytes = ByteArray(jLen).also { buf.get(it) }
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

        // BIN chunk (optional but always present for this file)
        val binData: ByteArray = if (buf.remaining() >= 8) {
            val bLen  = buf.int
            val bType = buf.int
            if (bType == CHUNK_BIN) ByteArray(bLen).also { buf.get(it) } else ByteArray(0)
        } else ByteArray(0)

        return buildModel(json, binData)
    }

    private fun buildModel(json: JSONObject, bin: ByteArray): GlbModel? {
        val accessors   = json.getJSONArray("accessors")
        val bufferViews = json.getJSONArray("bufferViews")
        val nodes       = json.getJSONArray("nodes")
        val mesh        = json.getJSONArray("meshes").getJSONObject(0)
        val primitive   = mesh.getJSONArray("primitives").getJSONObject(0)
        val skin        = json.getJSONArray("skins").getJSONObject(0)
        val attrs       = primitive.getJSONObject("attributes")

        // ── Helper: read accessor data ─────────────────────────────────
        fun readAccessorBytes(accIdx: Int): Pair<ByteArray, Int> {
            val acc = accessors.getJSONObject(accIdx)
            val bvIdx  = acc.getInt("bufferView")
            val bv     = bufferViews.getJSONObject(bvIdx)
            val offset = bv.getInt("byteOffset") + acc.optInt("byteOffset", 0)
            val count  = acc.getInt("count")
            val compType = acc.getInt("componentType")
            val type     = acc.getString("type")
            val compSize = when (type) { "SCALAR"->1; "VEC2"->2; "VEC3"->3; "VEC4"->4; "MAT4"->16; else->1 }
            val compBytes = when (compType) { 5120,5121->1; 5122,5123->2; 5125,5126->4; else->4 }
            val stride = bv.optInt("byteStride", compSize * compBytes)
            val totalBytes = if (stride == compSize * compBytes) count * stride
                             else count * stride
            return Pair(bin.copyOfRange(offset, offset + totalBytes.coerceAtMost(bin.size - offset)), count)
        }

        fun readFloatAccessor(accIdx: Int): FloatBuffer {
            val acc = accessors.getJSONObject(accIdx)
            val bvIdx  = acc.getInt("bufferView")
            val bv     = bufferViews.getJSONObject(bvIdx)
            val offset = bv.getInt("byteOffset") + acc.optInt("byteOffset", 0)
            val count  = acc.getInt("count")
            val type   = acc.getString("type")
            val compSize = when (type) { "SCALAR"->1; "VEC2"->2; "VEC3"->3; "VEC4"->4; "MAT4"->16; else->1 }
            val stride = bv.optInt("byteStride", compSize * 4)
            val arr = FloatArray(count * compSize)
            val bb  = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                bb.position(offset + i * stride)
                for (c in 0 until compSize) arr[i * compSize + c] = bb.float
            }
            return ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().also { it.put(arr); it.position(0) }
        }

        // ── Indices (USHORT) ───────────────────────────────────────────
        val idxAccIdx = primitive.getInt("indices")
        val idxAcc    = accessors.getJSONObject(idxAccIdx)
        val idxBvIdx  = idxAcc.getInt("bufferView")
        val idxBv     = bufferViews.getJSONObject(idxBvIdx)
        val idxOffset = idxBv.getInt("byteOffset") + idxAcc.optInt("byteOffset", 0)
        val idxCount  = idxAcc.getInt("count")
        val indexArr  = ShortArray(idxCount)
        val idxBB = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        idxBB.position(idxOffset)
        for (i in 0 until idxCount) indexArr[i] = idxBB.short
        val indexBuf = ByteBuffer.allocateDirect(idxCount * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().also { it.put(indexArr); it.position(0) }

        // ── Vertex attributes ──────────────────────────────────────────
        val posBuf  = readFloatAccessor(attrs.getInt("POSITION"))
        val normBuf = readFloatAccessor(attrs.getInt("NORMAL"))
        val texBuf  = readFloatAccessor(attrs.getInt("TEXCOORD_0"))

        // JOINTS_0: UBYTE — read as bytes, store as shorts
        val jtsAccIdx = attrs.getInt("JOINTS_0")
        val jtsAcc    = accessors.getJSONObject(jtsAccIdx)
        val jtsBvIdx  = jtsAcc.getInt("bufferView")
        val jtsBv     = bufferViews.getJSONObject(jtsBvIdx)
        val jtsOffset = jtsBv.getInt("byteOffset") + jtsAcc.optInt("byteOffset", 0)
        val vertCount = jtsAcc.getInt("count")
        val jointsArr = ShortArray(vertCount * 4)
        val jtsBB = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        jtsBB.position(jtsOffset)
        for (i in 0 until vertCount * 4) jointsArr[i] = (jtsBB.get().toInt() and 0xFF).toShort()
        val jointsBuf = ByteBuffer.allocateDirect(vertCount * 4 * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().also { it.put(jointsArr); it.position(0) }

        // WEIGHTS_0: FLOAT
        val wgtBuf = readFloatAccessor(attrs.getInt("WEIGHTS_0"))

        // ── Inverse bind matrices (MAT4 x 41) ─────────────────────────
        val ibmAccIdx = skin.getInt("inverseBindMatrices")
        val ibmBuf    = readFloatAccessor(ibmAccIdx)
        val jointIndices = (0 until skin.getJSONArray("joints").length())
            .map { skin.getJSONArray("joints").getInt(it) }
        val invBindMats = Array(jointIndices.size) { i ->
            FloatArray(16).also { mat -> ibmBuf.position(i * 16); ibmBuf.get(mat); ibmBuf.position(0) }
        }

        // ── Build joint list from skeleton ─────────────────────────────
        val glbJoints = jointIndices.map { nodeIdx ->
            val n = nodes.getJSONObject(nodeIdx)
            val t = n.optJSONArray("translation")?.let { a -> floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat()) } ?: floatArrayOf(0f,0f,0f)
            val r = n.optJSONArray("rotation")?.let   { a -> floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat()) } ?: floatArrayOf(0f,0f,0f,1f)
            val s = n.optJSONArray("scale")?.let      { a -> floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat()) } ?: floatArrayOf(1f,1f,1f)
            GlbJoint(n.optString("name", "node$nodeIdx"), nodeIdx, t, r, s)
        }

        // ── Node children map ──────────────────────────────────────────
        val childrenMap = mutableMapOf<Int, List<Int>>()
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            val ch = n.optJSONArray("children") ?: continue
            childrenMap[i] = (0 until ch.length()).map { ch.getInt(it) }
        }

        // Find Root node index
        val rootIdx = (0 until nodes.length())
            .firstOrNull { nodes.getJSONObject(it).optString("name") == "Root" } ?: 40

        Log.d(TAG, "GLB loaded: ${glbJoints.size} joints, $vertCount vertices, $idxCount indices")
        return GlbModel(
            indexBuffer = indexBuf, indexCount = idxCount,
            positionBuffer = posBuf, normalBuffer = normBuf, texCoordBuffer = texBuf,
            jointsBuffer = jointsBuf, weightsBuffer = wgtBuf, vertexCount = vertCount,
            joints = glbJoints, inverseBindMatrices = invBindMats,
            nodeChildren = childrenMap, rootNodeIndex = rootIdx
        )
    }
}
