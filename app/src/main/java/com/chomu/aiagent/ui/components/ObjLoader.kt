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
    val bounds: FloatArray  // [minX, minY, minZ, maxX, maxY, maxZ]
)

object ObjLoader {
    private const val TAG = "ObjLoader"

    suspend fun load(context: Context, assetPath: String): ObjMesh? = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "${assetPath.hashCode()}.bin")
        if (cacheFile.exists()) {
            return@withContext loadFromCache(cacheFile)
        }
        val mesh = parseObj(context, assetPath) ?: return@withContext null
        saveToCache(cacheFile, mesh)
        mesh
    }

    private fun parseObj(context: Context, assetPath: String): ObjMesh? = try {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()

        val facePositions = mutableListOf<Float>()
        val faceNormals = mutableListOf<Float>()
        val faceTexCoords = mutableListOf<Float>()

        context.assets.open(assetPath).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("v ") -> {
                            val p = line.substring(2).trim().split("\\s+".toRegex())
                            if (p.size >= 3) {
                                positions.add(p[0].toFloatOrNull() ?: 0f)
                                positions.add(p[1].toFloatOrNull() ?: 0f)
                                positions.add(p[2].toFloatOrNull() ?: 0f)
                            }
                        }
                        line.startsWith("vn ") -> {
                            val n = line.substring(3).trim().split("\\s+".toRegex())
                            if (n.size >= 3) {
                                normals.add(n[0].toFloatOrNull() ?: 0f)
                                normals.add(n[1].toFloatOrNull() ?: 0f)
                                normals.add(n[2].toFloatOrNull() ?: 0f)
                            }
                        }
                        line.startsWith("vt ") -> {
                            val t = line.substring(3).trim().split("\\s+".toRegex())
                            if (t.size >= 2) {
                                texCoords.add(t[0].toFloatOrNull() ?: 0f)
                                texCoords.add(t[1].toFloatOrNull() ?: 0f)
                            }
                        }
                        line.startsWith("f ") -> {
                            val tokens = line.substring(2).trim().split("\\s+".toRegex())
                            if (tokens.size >= 3) {
                                val verts = tokens.map { parseFaceVertex(it) }
                                // Fan-triangulate polygon faces
                                for (i in 1 until verts.size - 1) {
                                    listOf(verts[0], verts[i], verts[i + 1]).forEach { v ->
                                        val vi = (v[0] - 1) * 3
                                        if (vi >= 0 && vi + 2 < positions.size) {
                                            facePositions.add(positions[vi])
                                            facePositions.add(positions[vi + 1])
                                            facePositions.add(positions[vi + 2])
                                        } else {
                                            facePositions.addAll(listOf(0f, 0f, 0f))
                                        }
                                        val ti = (v[1] - 1) * 2
                                        if (ti >= 0 && ti + 1 < texCoords.size) {
                                            faceTexCoords.add(texCoords[ti])
                                            faceTexCoords.add(texCoords[ti + 1])
                                        } else {
                                            faceTexCoords.addAll(listOf(0f, 0f))
                                        }
                                        val ni = (v[2] - 1) * 3
                                        if (ni >= 0 && ni + 2 < normals.size) {
                                            faceNormals.add(normals[ni])
                                            faceNormals.add(normals[ni + 1])
                                            faceNormals.add(normals[ni + 2])
                                        } else {
                                            faceNormals.addAll(listOf(0f, 1f, 0f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (facePositions.isEmpty()) return null

        // Compute bounds for normalization
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE; var maxZ = Float.MIN_VALUE
        for (i in facePositions.indices step 3) {
            val x = facePositions[i]; val y = facePositions[i+1]; val z = facePositions[i+2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }

        // Normalize to [-1, 1] bounding box
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f; val cz = (minZ + maxZ) / 2f
        val scale = 2f / maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.001f)
        for (i in facePositions.indices step 3) {
            facePositions[i] = (facePositions[i] - cx) * scale
            facePositions[i+1] = (facePositions[i+1] - cy) * scale
            facePositions[i+2] = (facePositions[i+2] - cz) * scale
        }

        val vertexCount = facePositions.size / 3
        Log.d(TAG, "Loaded OBJ: $vertexCount triangles, ${positions.size/3} vertices")

        ObjMesh(
            vertexBuffer = toFloatBuffer(facePositions),
            normalBuffer = toFloatBuffer(faceNormals),
            texCoordBuffer = toFloatBuffer(faceTexCoords),
            vertexCount = vertexCount,
            bounds = floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing OBJ", e)
        null
    }

    private fun parseFaceVertex(token: String): IntArray {
        val parts = token.split("/")
        return intArrayOf(
            parts.getOrNull(0)?.toIntOrNull() ?: 1,
            parts.getOrNull(1)?.toIntOrNull() ?: 1,
            parts.getOrNull(2)?.toIntOrNull() ?: 1
        )
    }

    private fun toFloatBuffer(list: List<Float>): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(list.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(list.toFloatArray())
        buf.position(0)
        return buf
    }

    private fun saveToCache(file: File, mesh: ObjMesh) = try {
        file.outputStream().buffered().use { out ->
            val totalFloats = mesh.vertexCount * 3 * 3 + mesh.vertexCount * 2
            val buf = ByteBuffer.allocate(4 + totalFloats * 4 + 4 + 24)
            buf.putInt(mesh.vertexCount)
            mesh.vertexBuffer.position(0)
            repeat(mesh.vertexCount * 3) { buf.putFloat(mesh.vertexBuffer.get()) }
            mesh.normalBuffer.position(0)
            repeat(mesh.vertexCount * 3) { buf.putFloat(mesh.normalBuffer.get()) }
            mesh.texCoordBuffer.position(0)
            repeat(mesh.vertexCount * 2) { buf.putFloat(mesh.texCoordBuffer.get()) }
            mesh.bounds.forEach { buf.putFloat(it) }
            out.write(buf.array(), 0, buf.position())
        }
        mesh.vertexBuffer.position(0)
        mesh.normalBuffer.position(0)
        mesh.texCoordBuffer.position(0)
    } catch (e: Exception) {
        Log.e(TAG, "Cache save failed", e)
    }

    private fun loadFromCache(file: File): ObjMesh? = try {
        file.inputStream().buffered().use { inp ->
            val bytes = inp.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val count = buf.int
            val verts = FloatArray(count * 3) { buf.float }
            val norms = FloatArray(count * 3) { buf.float }
            val texs = FloatArray(count * 2) { buf.float }
            val bounds = FloatArray(6) { buf.float }
            ObjMesh(
                vertexBuffer = toFloatBuffer(verts.toList()),
                normalBuffer = toFloatBuffer(norms.toList()),
                texCoordBuffer = toFloatBuffer(texs.toList()),
                vertexCount = count,
                bounds = bounds
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cache load failed", e)
        file.delete()
        null
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(arr); buf.position(0)
        return buf
    }
}
