package com.chomu.aiagent.data.repository

import com.chomu.aiagent.domain.model.ApiConfig
import com.chomu.aiagent.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface LLMRepository {
    suspend fun sendMessage(
        userMessage: String,
        history: List<Message>,
        config: ApiConfig
    ): Result<String>

    suspend fun fetchGeminiModels(apiKey: String): Result<List<String>>
    suspend fun fetchNvidiaModels(apiKey: String, baseUrl: String): Result<List<String>>

    fun getMessagesFlow(sessionId: String = "default"): Flow<List<Message>>
    suspend fun saveMessage(message: Message, sessionId: String = "default")
    suspend fun clearHistory(sessionId: String = "default")
}
