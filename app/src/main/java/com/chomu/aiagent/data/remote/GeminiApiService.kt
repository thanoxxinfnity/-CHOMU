package com.chomu.aiagent.data.remote

import com.chomu.aiagent.data.remote.dto.GeminiRequest
import com.chomu.aiagent.data.remote.dto.GeminiResponse
import retrofit2.Response
import retrofit2.http.*

interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): Response<GeminiModelsResponse>
}

data class GeminiModelsResponse(
    val models: List<GeminiModelInfo>? = null
)

data class GeminiModelInfo(
    val name: String,
    val displayName: String? = null,
    val description: String? = null
)
