package com.chomu.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NvidiaClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    data class Msg(val role: String, val content: String)

    suspend fun chat(apiKey: String, model: String, msgs: List<Msg>): String =
        withContext(Dispatchers.IO) {
            val arr = JSONArray().also { a -> msgs.forEach { m ->
                a.put(JSONObject().put("role", m.role).put("content", m.content))
            }}
            val body = JSONObject()
                .put("model", model)
                .put("messages", arr)
                .put("temperature", 0.8)
                .put("max_tokens", 300)
                .toString().toRequestBody(json)

            val req = Request.Builder()
                .url("https://integrate.api.nvidia.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body).build()

            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw Exception("API ${r.code}: ${r.body?.string()?.take(200)}")
                JSONObject(r.body!!.string())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            }
        }

    suspend fun vision(apiKey: String, imageBase64: String, context: String): String =
        withContext(Dispatchers.IO) {
            val contentArr = JSONArray()
                .put(JSONObject().put("type", "text").put("text", context))
                .put(JSONObject().put("type", "image_url").put(
                    "image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")
                ))
            val arr = JSONArray().put(JSONObject().put("role", "user").put("content", contentArr))
            val body = JSONObject()
                .put("model", "meta/llama-3.2-11b-vision-instruct")
                .put("messages", arr)
                .put("max_tokens", 150)
                .toString().toRequestBody(json)

            val req = Request.Builder()
                .url("https://integrate.api.nvidia.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body).build()

            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw Exception("Vision API ${r.code}")
                JSONObject(r.body!!.string())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            }
        }
}
