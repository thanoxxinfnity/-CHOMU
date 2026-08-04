package com.chomu.aiagent.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NvidiaRequest(
    val model: String,
    val messages: List<NvidiaMessage>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = false,
    @SerializedName("top_p") val topP: Float = 0.95f
)

data class NvidiaMessage(
    val role: String,
    val content: String
)

data class NvidiaResponse(
    val id: String? = null,
    val choices: List<NvidiaChoice>? = null,
    val error: NvidiaError? = null
)

data class NvidiaChoice(
    val index: Int = 0,
    val message: NvidiaMessage,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class NvidiaError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
