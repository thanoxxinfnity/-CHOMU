package com.chomu.aiagent.ui.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class ObjMesh(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val texCoordBuffer: FloatBuffer,
    val vertexCount: Int,
    val bounds: FloatArray
)

object ObjLoader {
    private const val TAG = "ObjLoader"

    // Primitive-backed growable float array — avoids boxing overhead that OOMs on large OBJs
    private class FloatList(initialCapacity: Int = 65536) {
        private var data = FloatArray(initialCapacity)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun add3(a: Float, b: Float, c: Float) { add(a); add(b); add(c) }
        fun add2(a: Float, b: Float) { add(a); add(b) }

        fun get(i: Int) = data[i]
        fun isEmpty() = size == 0

        fun toDirectBuffer(): FloatBuffer {
            val buf = ByteBuffer.allocateDirect(size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buf.put(data, 0, size)
            buf.position(0)
            return buf
        }
    }

    suspend fun load(context: Context, assetPath: String): ObjMesh? = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "${assetPath.hashCode()}_v2.bin")
        if (cacheFile.exists()) {
            val cached = loadFromCache(cacheFile)
            if (cached != null) return@withContext cached
            cacheFile.delete()  // corrupt cache — force re-parse
        }
        val mesh = parseObj(context, assetPath) ?: return@withContext null
        saveToCache(cacheFile, mesh)
        mesh
    }

    private fun parseObj(context: Context, assetPath: String): ObjMesh? {
        return try {
            // Raw vertex data (small — just unique vertices from v/vn/vt lines)
            val rawPos = FloatList(16384)
            val rawNorm = FloatList(16384)
            val rawTex = FloatList(8192)

            // Expanded per-triangle data
            val facePos = FloatList(65536)
            val faceNorm = FloatList(65536)
            val faceTex = FloatList(65536)

            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream), 65536).use { reader ->
                    reader.forEachLine { rawLine ->
                        val line = rawLine.trim()
                        when {
                            line.startsWith("v ") -> {
                                val p = line.substring(2).trim().split("\\s+".toRegex())
                                if (p.size >= 3) {
                                    rawPos.add(p[0].toFloatOrNull() ?: 0f)
                                    rawPos.add(p[1].toFloatOrNull() ?: 0f)
                                    rawPos.add(p[2].toFloatOrNull() ?: 0f)
                                }
                            }
                            line.startsWith("vn ") -> {
                                val n = line.substring(3).trim().split("\\s+".toRegex())
                                if (n.size >= 3) {
                                    rawNorm.add(n[0].toFloatOrNull() ?: 0f)
                                    rawNorm.add(n[1].toFloatOrNull() ?: 0f)
                                    rawNorm.add(n[2].toFloatOrNull() ?: 0f)
                                }
                            }
                            line.startsWith("vt ") -> {
                                val t = line.substring(3).trim().split("\\s+".toRegex())
                                if (t.size >= 2) {
                                    rawTex.add(t[0].toFloatOrNull() ?: 0f)
                                    rawTex.add(t[1].toFloatOrNull() ?: 0f)
                                }
                            }
                            line.startsWith("f ") -> {
                                val tokens = line.substring(2).trim().split("\\s+".toRegex())
                                if (tokens.size >= 3) {
                                    // Fan-triangulate n-gon
                                    val v0 = parseFaceVertex(tokens[0])
                                    var v1 = parseFaceVertex(tokens[1])
                                    for (i in 2 until tokens.size) {
                                        val v2 = parseFaceVertex(tokens[i])
                                        for (v in listOf(v0, v1, v2)) {
                                            expandVertex(v, rawPos, rawNorm, rawTex, facePos, faceNorm, faceTex)
                                        }
                                        v1 = v2
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (facePos.isEmpty()) {
                Log.e(TAG, "No face geometry found in OBJ")
                return null
            }

            // Normalize to [-1, 1] bounding box — fixed: use -MAX_VALUE for min tracking
            var minX = Float.MAX_VALUE;  var minY = Float.MAX_VALUE;  var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            var i = 0
            while (i < facePos.size) {
                val x = facePos.get(i); val y = facePos.get(i + 1); val z = facePos.get(i + 2)
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
                i += 3
            }

            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val cz = (minZ + maxZ) / 2f
            val span = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.001f)
            val scale = 2f / span

            // Build normalized direct buffers
            val vertCount = facePos.size / 3
            val posBuffer = ByteBuffer.allocateDirect(facePos.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val normBuffer = ByteBuffer.allocateDirect(faceNorm.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val texBuffer = ByteBuffer.allocateDirect(faceTex.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

            i = 0
            while (i < facePos.size) {
                posBuffer.put((facePos.get(i) - cx) * scale)
                posBuffer.put((facePos.get(i + 1) - cy) * scale)
                posBuffer.put((facePos.get(i + 2) - cz) * scale)
                i += 3
            }
            for (j in 0 until faceNorm.size) normBuffer.put(faceNorm.get(j))
            for (j in 0 until faceTex.size)  texBuffer.put(faceTex.get(j))

            posBuffer.position(0); normBuffer.position(0); texBuffer.position(0)

            Log.d(TAG, "OBJ loaded: $vertCount triangulated vertices, span=$span")
            ObjMesh(posBuffer, normBuffer, texBuffer, vertCount,
                floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ))

        } catch (e: Exception) {
            Log.e(TAG, "OBJ parse error", e)
            null
        }
    }

    private fun expandVertex(
        v: IntArray,
        rawPos: FloatList, rawNorm: FloatList, rawTex: FloatList,
        facePos: FloatList, faceNorm: FloatList, faceTex: FloatList
    ) {
        val pi = (v[0] - 1) * 3
        if (pi >= 0 && pi + 2 < rawPos.size) {
            facePos.add3(rawPos.get(pi), rawPos.get(pi + 1), rawPos.get(pi + 2))
        } else facePos.add3(0f, 0f, 0f)

        val ti = (v[1] - 1) * 2
        if (ti >= 0 && ti + 1 < rawTex.size) {
            faceTex.add2(rawTex.get(ti), rawTex.get(ti + 1))
        } else faceTex.add2(0f, 0f)

        val ni = (v[2] - 1) * 3
        if (ni >= 0 && ni + 2 < rawNorm.size) {
            faceNorm.add3(rawNorm.get(ni), rawNorm.get(ni + 1), rawNorm.get(ni + 2))
        } else faceNorm.add3(0f, 1f, 0f)
    }

    private fun parseFaceVertex(token: String): IntArray {
        val parts = token.split("/")
        return intArrayOf(
            parts.getOrNull(0)?.toIntOrNull() ?: 1,
            parts.getOrNull(1)?.toIntOrNull() ?: 1,
            parts.getOrNull(2)?.toIntOrNull() ?: 1
        )
    }

    // ── Cache ────────────────────────────────────────────────────────────────

    private fun saveToCache(file: File, mesh: ObjMesh) {
        try {
            val count = mesh.vertexCount
            // 4 (count) + count*3*4 (pos) + count*3*4 (norm) + count*2*4 (tex) + 6*4 (bounds)
            val total = 4 + count * (3 + 3 + 2) * 4 + 24
            val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(count)
            mesh.vertexBuffer.position(0); repeat(count * 3) { buf.putFloat(mesh.vertexBuffer.get()) }
            mesh.normalBuffer.position(0); repeat(count * 3) { buf.putFloat(mesh.normalBuffer.get()) }
            mesh.texCoordBuffer.position(0); repeat(count * 2) { buf.putFloat(mesh.texCoordBuffer.get()) }
            mesh.bounds.forEach { buf.putFloat(it) }
            file.writeBytes(buf.array())
            mesh.vertexBuffer.position(0); mesh.normalBuffer.position(0); mesh.texCoordBuffer.position(0)
            Log.d(TAG, "Cached $count verts to ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Cache write failed", e)
            file.delete()
        }
    }

    private fun loadFromCache(file: File): ObjMesh? {
        return try {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.int
            if (count <= 0 || count > 5_000_000) {
                Log.w(TAG, "Invalid cache count=$count, ignoring")
                return null
            }
            val verts = FloatArray(count * 3) { buf.float }
            val norms = FloatArray(count * 3) { buf.float }
            val texs = FloatArray(count * 2) { buf.float }
            val bounds = FloatArray(6) { buf.float }

            fun toBuffer(arr: FloatArray): FloatBuffer {
                val b = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                b.put(arr); b.position(0); return b
            }
            Log.d(TAG, "Loaded from cache: $count verts")
            ObjMesh(toBuffer(verts), toBuffer(norms), toBuffer(texs), count, bounds)
        } catch (e: Exception) {
            Log.e(TAG, "Cache read failed", e)
            null
        }
    }
}
