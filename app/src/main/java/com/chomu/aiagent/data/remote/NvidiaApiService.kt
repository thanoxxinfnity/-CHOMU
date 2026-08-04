package com.chomu.aiagent.data.remote

import com.chomu.aiagent.data.remote.dto.NvidiaRequest
import com.chomu.aiagent.data.remote.dto.NvidiaResponse
import retrofit2.Response
import retrofit2.http.*

interface NvidiaApiService {

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") bearerToken: String,
        @Body request: NvidiaRequest
    ): Response<NvidiaResponse>

    @GET("models")
    suspend fun listModels(
        @Header("Authorization") bearerToken: String
    ): Response<NvidiaModelsResponse>
}

data class NvidiaModelsResponse(
    val data: List<NvidiaModelInfo>? = null
)

data class NvidiaModelInfo(
    val id: String,
    val owned_by: String? = null
)
