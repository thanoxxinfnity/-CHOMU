package com.chomu.aiagent.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("system_instruction") val systemInstruction: GeminiSystemInstruction? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiPart(val text: String)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

data class GeminiGenerationConfig(
    val temperature: Float = 0.7f,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 2048,
    @SerializedName("topP") val topP: Float = 0.95f
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

data class GeminiCandidate(
    val content: GeminiContent,
    @SerializedName("finishReason") val finishReason: String? = null
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
